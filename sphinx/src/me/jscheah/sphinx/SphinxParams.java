package me.jscheah.sphinx;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.*;
import java.util.List;

public class SphinxParams {

    public static class GroupECC {
        private ECParameterSpec EcSpec;
        private ECCurve mEcCurve;
        public ECPoint Generator;

        public GroupECC() {
            EcSpec = ECNamedCurveTable.getParameterSpec("secp224r1");
            mEcCurve = EcSpec.getCurve();
            Generator = EcSpec.getG();
        }

        public BigInteger generateSecret() {
            BigInteger result;
            BigInteger order = mEcCurve.getOrder();
            SecureRandom rand = new SecureRandom();
            do {
                result = new BigInteger(order.bitLength(), rand);
            } while (result.compareTo(order) >= 0); //exclusive order
            return result;
        }

        public ECPoint expon(ECPoint base, BigInteger exp) {
            return base.multiply(exp);
        }

        public ECPoint multiExpon(ECPoint base, List<BigInteger> exps) {
            BigInteger expon = new BigInteger("1");
            for (BigInteger exp : exps) {
                expon = exp.multiply(expon).mod(mEcCurve.getOrder());
            }
            return expon(base, expon);
        }

        public BigInteger makeExp(byte[] data) {
            //BigInteger expects a two's-complement array, but petlib's from_binary doesn't
            return (new BigInteger(Arrays.prepend(data, (byte)0)).mod(mEcCurve.getOrder()));
        }

        public boolean inGroup(ECPoint alpha) {
            return alpha.isValid();
        }

        public byte[] printable(ECPoint alpha) {
            return alpha.getEncoded(false);
        }

        public String printableString(ECPoint alpha) {
            byte[] b = printable(alpha);
            char[] hexArray = "0123456789ABCDEF".toCharArray();
            char[] hexChars = new char[b.length * 2];
            for ( int j = 0; j < b.length; j++ ) {
                int v = b[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars).toLowerCase();
        }
    }

    private GroupECC mGroup;
    private int maxLength;
    private int m;
    private int k;
    private Cipher aes;
    private MessageDigest sha256;

    public SphinxParams() throws CryptoException {
        this(192);
    }

    public SphinxParams(int headerLen) throws CryptoException {
        this(headerLen, 1024);
    }

    public SphinxParams(int headerLen, int bodyLen) throws CryptoException {
        mGroup = new GroupECC();
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

    public byte[] aesCtr(String key, String message, byte[] iv) throws CryptoException {
        byte[] keyBytes = key.getBytes(Charset.forName("UTF-8"));
        byte[] messageBytes = message.getBytes(Charset.forName("UTF-8"));
        return aesCtr(keyBytes, messageBytes, iv);
    }

    public byte[] aesCtr(byte[] key, String message, byte[] iv) throws CryptoException {
        byte[] messageBytes = message.getBytes(Charset.forName("UTF-8"));
        return aesCtr(key, messageBytes, iv);
    }

    public byte[] aesCtr(String key, byte[] message, byte[] iv) throws CryptoException {
        byte[] keyBytes = key.getBytes(Charset.forName("UTF-8"));
        return aesCtr(keyBytes, message, iv);
    }

    public byte[] aesCtr(byte[] key, byte[] message, byte[] iv) throws CryptoException {
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
        byte[] c = aesCtr(key, Arrays.copyOf(message, this.k), k1);
        byte[] r1 = Arrays.concatenate(
                c,
                Arrays.copyOfRange(message, this.k, message.length)
        );

        // Round 2
        c = aesCtr(key, Arrays.copyOfRange(r1, this.k, r1.length), Arrays.copyOf(r1, this.k));
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
        c = aesCtr(key, Arrays.copyOf(r2, this.k), k3);
        byte[] r3 = Arrays.concatenate(
                c,
                Arrays.copyOfRange(r2, this.k, r2.length)
        );

        // Round 4
        c = aesCtr(key, Arrays.copyOfRange(r3, this.k, r3.length), Arrays.copyOf(r3, this.k));
        byte[] r4 = Arrays.concatenate(
                Arrays.copyOf(r3, this.k),
                c
        );

        return r4;
    }

    public byte[] lionessDec(byte[] key, byte[] message) throws CryptoException {
        assert key.length == this.k;
        assert message.length >= this.k*2;

        byte[] r4 = message;
        byte[] r4_short = Arrays.copyOf(r4, this.k);
        byte[] r4_long = Arrays.copyOfRange(r4, this.k, r4.length);

        // Round 4
        byte[] r3_long = aesCtr(key, r4_long, r4_short);
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
        byte[] r2_short = aesCtr(key, r3_short, k2);
        byte[] r2_long = r3_long;

        // Round 2
        byte[] r1_long = aesCtr(key, r2_long, r2_short);
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
        byte[] c = aesCtr(key, r1_short, k0);
        byte[] r0 = Arrays.concatenate(
                c,
                r1_long
        );

        return r0;
    }

    public byte[] xorRho(byte[] key, byte[] plain) throws CryptoException {
        assert key.length == this.k;
        return aesCtr(key, plain, new byte[16]);
    }

    public byte[] mu(byte[] key, byte[] data) throws CryptoException {
        Mac mac = null;
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

    public byte[] pi(byte[] key, byte[] data) throws CryptoException {
        assert key.length == this.k;
        assert data.length == this.m;

        return lionessEnc(key, data);
    }


    public byte[] hash(byte[] data) {
        return sha256.digest(data);
    }

    public byte[] getAesKey(ECPoint s) {
        return Arrays.copyOf(
                sha256.digest(
                ("aes_key:" + mGroup.printableString(s)).getBytes(Charset.forName("UTF-8"))),
                this.k);
    }

    public byte[] deriveKey(byte[] key, byte[] flavor) throws CryptoException {
        return aesCtr(key, new byte[this.k], flavor);
    }

    public BigInteger hb(byte[] key)
            throws CryptoException {
        return mGroup.makeExp(
          deriveKey(key, "hbhbhbhbhbhbhbhb".getBytes(Charset.forName("UTF-8")))
        );
    }

    public byte[] hrho(byte[] key) throws CryptoException {
        return deriveKey(key, "hrhohrhohrhohrho".getBytes(Charset.forName("UTF-8")));
    }

    public byte[] hmu(byte[] key) throws CryptoException {
        return deriveKey(key, "hmu:hmu:hmu:hmu:".getBytes(Charset.forName("UTF-8")));
    }

    public byte[] hpi(byte[] key) throws CryptoException {
        return deriveKey(key, "hpi:hpi:hpi:hpi:".getBytes(Charset.forName("UTF-8")));
    }

    public byte[] htau(byte[] key) throws CryptoException {
        return deriveKey(key, "htauhtauhtauhtau".getBytes(Charset.forName("UTF-8")));
    }
}
