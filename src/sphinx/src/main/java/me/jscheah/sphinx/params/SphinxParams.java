package me.jscheah.sphinx.params;

import me.jscheah.sphinx.exceptions.CryptoException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Random;

public class SphinxParams {
    private static final byte[] KEY_1_APPEND = "1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_3_APPEND = "3".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_KEY_PREPEND = "aes_key:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HB_IV = "hbhbhbhbhbhbhbhb".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HRHO_IV = "hrhohrhohrhohrho".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HMU_IV = "hmu:hmu:hmu:hmu:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HPI_IV = "hpi:hpi:hpi:hpi:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HTAU_IV = "htauhtauhtauhtau".getBytes(StandardCharsets.UTF_8);

    public GroupECC group;
    public int headerSize;
    public int bodySize;
    public int k;           // security parameter
    private Random random;
    private ThreadLocal<Cipher> aes = ThreadLocal.withInitial(this::getAesInstance);
    private ThreadLocal<MessageDigest> sha256 = ThreadLocal.withInitial(this::getShaInstance);
    private ThreadLocal<Mac> mac = ThreadLocal.withInitial(this::getMacInstance);

    /**
     * Creates a new SphinxParams class with a header length of 192 and a body length of 1024
     *
     * @throws CryptoException
     */
    public SphinxParams() throws CryptoException {
        this(192);
    }

    /**
     * Creates a new SphinxParams class with a header length of {@code headerLen} and a body length of 1024
     *
     * @param headerLen length of header
     * @throws CryptoException
     */
    public SphinxParams(int headerLen) throws CryptoException {
        this(headerLen, 1024);
    }

    /**
     * Creates a new SphinxParams class with a header length of {@code headerLen} and a body length of {@code bodyLen}
     *
     * @param headerLen length of header
     * @param bodyLen   length of body
     * @throws CryptoException
     */
    public SphinxParams(int headerLen, int bodyLen) throws CryptoException {
        this(new GroupECC(), headerLen, bodyLen);
    }

    public SphinxParams(GroupECC group, int headerLen, int bodyLen) throws CryptoException {
        random = new SecureRandom();
        this.group = group;
        headerSize = headerLen;
        bodySize = bodyLen;
        k = 16;
        try {
            // Try to instantiate AES-128-CTR, SHA-256, HMAC-SHA256. If it doesn't throw an exception here, it should
            // be safe to assume future invocations won't throw exceptions.
            Cipher.getInstance("AES/SIC/NoPadding", new BouncyCastleProvider());
            MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new CryptoException(e);
        }
    }

    /**
     * Performs AES-128-CTR encryption on {@code message} using {@code key} and {@code iv}
     *
     * @param key     AES key
     * @param message message to encrypt
     * @param iv      AES IV
     * @return encrypted message
     * @throws CryptoException
     */
    public byte[] aesEncrypt(byte[] key, byte[] message, byte[] iv) throws CryptoException {
        try {
            aes.get().init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new CryptoException(e);
        }
        try {
            return aes.get().doFinal(message);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new CryptoException(e);
        }
    }

    /**
     * Performs a SHA-256 hash of {@code data}
     *
     * @param data data to be hash
     * @return SHA-256 hash of {@code data}
     */
    private byte[] hash(byte[] data) {
        return sha256.get().digest(data);
    }

    /**
     * Encrypt {@code message} with {@code key} with LIONESS
     *
     * @param key     encryption key
     * @param message message to encrypt
     * @return encrypted message
     * @throws CryptoException
     */
    public byte[] lionessEnc(byte[] key, byte[] message) throws CryptoException {
        assert key.length == this.k;
        assert message.length >= this.k * 2;

        // Round 1
        byte[] k1 = Arrays.copyOf(
                hash(
                        Arrays.concatenate(
                                Arrays.copyOfRange(message, this.k, message.length),
                                key,
                                KEY_1_APPEND
                        )
                ), this.k);
        byte[] left_0 = aesEncrypt(key, Arrays.copyOf(message, this.k), k1);
        byte[] r1 = Arrays.concatenate(
                left_0,
                Arrays.copyOfRange(message, this.k, message.length)
        );

        // Round 2
        byte[] right_1 = aesEncrypt(key, Arrays.copyOfRange(r1, this.k, r1.length), Arrays.copyOf(r1, this.k));
        byte[] r2 = Arrays.concatenate(
                Arrays.copyOf(r1, this.k),
                right_1
        );

        // Round 3
        byte[] k3 = Arrays.copyOf(
                hash(
                        Arrays.concatenate(
                                Arrays.copyOfRange(r2, this.k, r2.length),
                                key,
                                KEY_3_APPEND
                        )
                ), this.k);
        byte[] left_2 = aesEncrypt(key, Arrays.copyOf(r2, this.k), k3);
        byte[] r3 = Arrays.concatenate(
                left_2,
                Arrays.copyOfRange(r2, this.k, r2.length)
        );

        // Round 4
        byte[] right_3 = aesEncrypt(key, Arrays.copyOfRange(r3, this.k, r3.length), Arrays.copyOf(r3, this.k));
        byte[] r4 = Arrays.concatenate(
                Arrays.copyOf(r3, this.k),
                right_3
        );

        return r4;
    }

    /**
     * Decrypt {@code message} with {@code key} with LIONESS
     *
     * @param key     encryption key
     * @param message message to decrypt
     * @return decrypted message
     * @throws CryptoException
     */
    public byte[] lionessDec(byte[] key, byte[] message) throws CryptoException {
        assert key.length == this.k;
        assert message.length >= this.k * 2;

        byte[] r4 = message;
        byte[] r4_short = Arrays.copyOf(r4, this.k);
        byte[] r4_long = Arrays.copyOfRange(r4, this.k, r4.length);

        // Round 4
        byte[] r3_long = aesEncrypt(key, r4_long, r4_short);
        byte[] r3_short = r4_short;

        // Round 3
        byte[] k2 = Arrays.copyOf(
                hash(
                        Arrays.concatenate(
                                r3_long,
                                key,
                                KEY_3_APPEND
                        )
                ), this.k);
        byte[] r2_short = aesEncrypt(key, r3_short, k2);
        byte[] r2_long = r3_long;

        // Round 2
        byte[] r1_long = aesEncrypt(key, r2_long, r2_short);
        byte[] r1_short = r2_short;

        // Round 1
        byte[] k0 = Arrays.copyOf(
                hash(
                        Arrays.concatenate(
                                r1_long,
                                key,
                                KEY_1_APPEND
                        )
                ), this.k);
        byte[] c = aesEncrypt(key, r1_short, k0);
        byte[] r0 = Arrays.concatenate(c, r1_long);

        return r0;
    }

    /**
     * A pseudo-random generator using AES
     *
     * @param key   AES key
     * @param plain data to feed into AES
     * @return encrypted data
     * @throws CryptoException
     */
    public byte[] rho(byte[] key, byte[] plain) throws CryptoException {
        assert key.length == this.k;
        return aesEncrypt(key, plain, new byte[16]);
    }

    /**
     * Generates a MAC for {@code data} keyed by {@code key}
     * Uses HMAC-SHA256 truncated to the security parameter
     *
     * @param key  MAC key
     * @param data MAC data
     * @return MAC for the data
     * @throws CryptoException
     */
    public byte[] mu(byte[] key, byte[] data) throws CryptoException {
        try {
            mac.get().init(new SecretKeySpec(key, "HmacSHA256"));
        } catch (InvalidKeyException e) {
            throw new CryptoException(e);
        }
        return Arrays.copyOf(mac.get().doFinal(data), this.k);
    }

    /**
     * Encrypts {@code data} with {@code key} using a family of pseudo-random permutations (PRPs), which is implemented
     * using the LIONESS cipher
     *
     * @param key  LIONESS key
     * @param data data to encrypt
     * @return encrypted ciphertext
     * @throws CryptoException
     */
    public byte[] pi(byte[] key, byte[] data) throws CryptoException {
        assert key.length == this.k;
        assert data.length == this.bodySize;

        return lionessEnc(key, data);
    }

    /**
     * Decrypts {@code data} with {@code key} using a family of pseudo-random permutations (PRPs), which is implemented
     * using the LIONESS cipher
     *
     * @param key  LIONESS key
     * @param data data to decrypt
     * @return decrypted plaintext
     * @throws CryptoException
     */
    public byte[] pii(byte[] key, byte[] data) throws CryptoException {
        assert key.length == this.k;
        assert data.length == this.bodySize;

        return lionessDec(key, data);
    }

    /**
     * Computes an AES key for a given ECPoint {@code s} by hashing the binary representation of {@code s}
     *
     * @param s an ECPoint
     * @return an AES key
     */
    public byte[] deriveAesKeyFromSecret(ECPoint s) {
        return Arrays.copyOf(
                sha256.get().digest(
                        Arrays.concatenate(
                                AES_KEY_PREPEND,
                                group.printable(s)
                        )
                ), this.k);
    }

    /**
     * Derives a key by encrypting an empty block with {@code key} and IV {@code iv}
     *
     * @param key    AES key
     * @param iv AES IV
     * @return result of the encryption
     * @throws CryptoException
     */
    public byte[] deriveKey(byte[] key, byte[] iv) throws CryptoException {
        return aesEncrypt(key, new byte[this.k], iv);
    }

    /**
     * Computes a blinding factor based on the key
     *
     * @param key key to generate blinding factor
     * @return a blinding factor
     * @throws CryptoException
     */
    public BigInteger hb(byte[] key)
            throws CryptoException {
        return group.makeExp(
                deriveKey(key, HB_IV)
        );
    }

    /**
     * Keyed hash function used to key the rho function
     *
     * @param key key to be hashed
     * @return a key for the rho function
     * @throws CryptoException
     */
    public byte[] hrho(byte[] key) throws CryptoException {
        return deriveKey(key, HRHO_IV);
    }

    /**
     * Keyed hash function used to key the mu function
     *
     * @param key key to be hashed
     * @return a key for the mu function
     * @throws CryptoException
     */
    public byte[] hmu(byte[] key) throws CryptoException {
        return deriveKey(key, HMU_IV);
    }

    /**
     * Keyed hash function used to key the pi function
     *
     * @param key key to be hashed
     * @return a key for the pi function
     * @throws CryptoException
     */
    public byte[] hpi(byte[] key) throws CryptoException {
        return deriveKey(key, HPI_IV);
    }

    /**
     * Keyed hash function used to key the tau function
     *
     * @param key key to be hashed
     * @return a key for the tau function
     * @throws CryptoException
     */
    public byte[] htau(byte[] key) throws CryptoException {
        return deriveKey(key, HTAU_IV);
    }

    public void randomBytes(byte[] bytes) {
        random.nextBytes(bytes);
    }

    private Cipher getAesInstance() {
        try {
            return Cipher.getInstance("AES/SIC/NoPadding", new BouncyCastleProvider());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    private MessageDigest getShaInstance() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Mac getMacInstance() {
        try {
            return Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
