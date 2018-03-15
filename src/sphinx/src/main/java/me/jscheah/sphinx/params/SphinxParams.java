package me.jscheah.sphinx.params;

import me.jscheah.sphinx.exceptions.CryptoException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.*;

public class SphinxParams {

    public GroupECC group;
    public int maxLength;   // Size of header
    public int m;   // Size of message body
    public int k;   // Security parameter
    private Cipher aes;
    private MessageDigest sha256;

    /**
     * Creates a new SphinxParams class with a header length of 192 and a body length of 1024
     * @throws CryptoException
     */
    public SphinxParams() throws CryptoException {
        this(192);
    }

    /**
     * Creates a new SphinxParams class with a header length of {@code headerLen} and a body length of 1024
     * @param headerLen length of header
     * @throws CryptoException
     */
    public SphinxParams(int headerLen) throws CryptoException {
        this(headerLen, 1024);
    }

    /**
     * Creates a new SphinxParams class with a header length of {@code headerLen} and a body length of {@code bodyLen}
     * @param headerLen length of header
     * @param bodyLen length of body
     * @throws CryptoException
     */
    public SphinxParams(int headerLen, int bodyLen) throws CryptoException {
        group = new GroupECC();
        maxLength = headerLen;
        m = bodyLen;
        k = 16;
        try {
            aes = Cipher.getInstance("AES/SIC/NoPadding", new BouncyCastleProvider());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new CryptoException(e);
        }
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException(e);
        }
    }

    /**
     * Performs AES-128-CTR encryption on {@code message} using {@code key} and {@code iv}
     * @param key AES key
     * @param message message to encrypt
     * @param iv AES IV
     * @return encrypted message
     * @throws CryptoException
     */
    public byte[] aesEncrypt(byte[] key, byte[] message, byte[] iv) throws CryptoException {
        try {
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new CryptoException(e);
        }
        try {
            return aes.doFinal(message);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new CryptoException(e);
        }
    }

    /**
     * Performs a SHA-256 hash of {@code data}
     * @param data data to be hash
     * @return SHA-256 hash of {@code data}
     */
    public byte[] hash(byte[] data) {
        return sha256.digest(data);
    }

    /**
     * Encrypt {@code message} with {@code key} with LIONESS
     * @param key encryption key
     * @param message message to encrypt
     * @return encrypted message
     * @throws CryptoException
     */
    public byte[] lionessEnc(byte[] key, byte[] message) throws CryptoException {
        assert key.length == this.k;
        assert message.length >= this.k*2;

        // Round 1
        byte[] k1 = Arrays.copyOf(
                hash(
                        Arrays.concatenate(
                                Arrays.copyOfRange(message, this.k, message.length),
                                key,
                                "1".getBytes(Charset.forName("UTF-8"))
                        )
                ), this.k);
        byte[] c = aesEncrypt(key, Arrays.copyOf(message, this.k), k1);
        byte[] r1 = Arrays.concatenate(
                c,
                Arrays.copyOfRange(message, this.k, message.length)
        );

        // Round 2
        c = aesEncrypt(key, Arrays.copyOfRange(r1, this.k, r1.length), Arrays.copyOf(r1, this.k));
        byte[] r2 = Arrays.concatenate(
                Arrays.copyOf(r1, this.k),
                c
        );

        // Round 3
        byte[] k3 = Arrays.copyOf(
                hash(
                        Arrays.concatenate(
                                Arrays.copyOfRange(r2, this.k, r2.length),
                                key,
                                "3".getBytes(Charset.forName("UTF-8"))
                        )
                ), this.k);
        c = aesEncrypt(key, Arrays.copyOf(r2, this.k), k3);
        byte[] r3 = Arrays.concatenate(
                c,
                Arrays.copyOfRange(r2, this.k, r2.length)
        );

        // Round 4
        c = aesEncrypt(key, Arrays.copyOfRange(r3, this.k, r3.length), Arrays.copyOf(r3, this.k));
        byte[] r4 = Arrays.concatenate(
                Arrays.copyOf(r3, this.k),
                c
        );

        return r4;
    }

    /**
     * Decrypt {@code message} with {@code key} with LIONESS
     * @param key encryption key
     * @param message message to decrypt
     * @return decrypted message
     * @throws CryptoException
     */
    public byte[] lionessDec(byte[] key, byte[] message) throws CryptoException {
        assert key.length == this.k;
        assert message.length >= this.k*2;

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
                                "3".getBytes(Charset.forName("UTF-8"))
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
                                "1".getBytes(Charset.forName("UTF-8"))
                        )
                ), this.k);
        byte[] c = aesEncrypt(key, r1_short, k0);
        byte[] r0 = Arrays.concatenate(
                c,
                r1_long
        );

        return r0;
    }

    /**
     * A pseudo-random generator using AES
     * @param key AES key
     * @param plain data to feed into AES
     * @return encrypted data
     * @throws CryptoException
     */
    public byte[] xorRho(byte[] key, byte[] plain) throws CryptoException {
        assert key.length == this.k;
        return aesEncrypt(key, plain, new byte[16]);
    }

    /**
     * Generates a MAC for {@code data} keyed by {@code key}
     * @param key MAC key
     * @param data MAC data
     * @return MAC for the data
     * @throws CryptoException
     */
    public byte[] mu(byte[] key, byte[] data) throws CryptoException {
        Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException(e);
        }
        try {
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
        } catch (InvalidKeyException e) {
            throw new CryptoException(e);
        }
        return Arrays.copyOf(mac.doFinal(data), this.k);
    }

    /**
     * Encrypts {@code data} with {@code key} using a family of pseudo-random permutations (PRPs), which is implemented
     * using the LIONESS cipher
     * @param key LIONESS key
     * @param data data to encrypt
     * @return encrypted ciphertext
     * @throws CryptoException
     */
    public byte[] pi(byte[] key, byte[] data) throws CryptoException {
        assert key.length == this.k;
        assert data.length == this.m;

        return lionessEnc(key, data);
    }

    /**
     * Decrypts {@code data} with {@code key} using a family of pseudo-random permutations (PRPs), which is implemented
     * using the LIONESS cipher
     * @param key LIONESS key
     * @param data data to decrypt
     * @return decrypted plaintext
     * @throws CryptoException
     */
    public byte[] pii(byte[] key, byte[] data) throws CryptoException {
        assert key.length == this.k;
        assert data.length == this.m;

        return lionessDec(key, data);
    }

    /**
     * Computes an AES key for a given ECPoint {@code s} by hashing the binary representation of {@code s}
     * @param s an ECPoint
     * @return an AES key
     */
    public byte[] getAesKey(ECPoint s) {
        return Arrays.copyOf(
                sha256.digest(
                        Arrays.concatenate(
                                "aes_key:".getBytes(Charset.forName("UTF-8")),
                                group.printable(s)
                        )
                ), this.k);
    }

    /**
     * Derives a key by encrypting an empty block with {@code key} and IV {@code flavor}
     * @param key AES key
     * @param flavor AES IV
     * @return result of the encryption
     * @throws CryptoException
     */
    public byte[] deriveKey(byte[] key, byte[] flavor) throws CryptoException {
        return aesEncrypt(key, new byte[this.k], flavor);
    }

    /**
     * Computes a blinding factor based on the key
     * @param key key to generate blinding factor
     * @return a blinding factor
     * @throws CryptoException
     */
    public BigInteger hb(byte[] key)
            throws CryptoException {
        return group.makeExp(
          deriveKey(key, "hbhbhbhbhbhbhbhb".getBytes(Charset.forName("UTF-8")))
        );
    }

    /**
     * Keyed hash function used to key the rho function
     * @param key key to be hashed
     * @return a key for the rho function
     * @throws CryptoException
     */
    public byte[] hrho(byte[] key) throws CryptoException {
        return deriveKey(key, "hrhohrhohrhohrho".getBytes(Charset.forName("UTF-8")));
    }

    /**
     * Keyed hash function used to key the mu function
     * @param key key to be hashed
     * @return a key for the mu function
     * @throws CryptoException
     */
    public byte[] hmu(byte[] key) throws CryptoException {
        return deriveKey(key, "hmu:hmu:hmu:hmu:".getBytes(Charset.forName("UTF-8")));
    }

    /**
     * Keyed hash function used to key the pi function
     * @param key key to be hashed
     * @return a key for the pi function
     * @throws CryptoException
     */
    public byte[] hpi(byte[] key) throws CryptoException {
        return deriveKey(key, "hpi:hpi:hpi:hpi:".getBytes(Charset.forName("UTF-8")));
    }

    /**
     * Keyed hash function used to key the tau function
     * @param key key to be hashed
     * @return a key for the tau function
     * @throws CryptoException
     */
    public byte[] htau(byte[] key) throws CryptoException {
        return deriveKey(key, "htauhtauhtauhtau".getBytes(Charset.forName("UTF-8")));
    }
}
