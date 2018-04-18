package me.jscheah.sphinx;

import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.params.SphinxParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.Arrays;

class SphinxParamsTest {
    @Test
    void testInit(){
        try {
            new SphinxParams();
        } catch (CryptoException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void testLionessEncThenDecEqual() {
        try {
            SphinxParams params = new SphinxParams();
            byte[] key = "AAAAAAAAAAAAAAAA".getBytes(Charset.forName("UTF-8"));
            byte[] message = "ARGARGARGARGARGARGARGARGARGARGARGARGARGARGARGARG".getBytes(Charset.forName("UTF-8"));
            byte[] ciphertext = params.lionessEnc(key, message);
            byte[] decMessage = params.lionessDec(key, ciphertext);
            Assertions.assertTrue(Arrays.equals(message, decMessage));
        } catch (CryptoException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void testAesEncThenDecEqual() {
        try {
            SphinxParams params = new SphinxParams();
            byte[] key = "AAAAAAAAAAAAAAAA".getBytes(Charset.forName("UTF-8"));
            byte[] message = "Hello World!".getBytes(Charset.forName("UTF-8"));
            byte[] ciphertext = params.aesEncrypt(key, message, new byte[16]);
            byte[] decMessage = params.aesEncrypt(key, ciphertext, new byte[16]);
            Assertions.assertTrue(Arrays.equals(message, decMessage));
        } catch (CryptoException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void xorRho() throws CryptoException {
        SphinxParams params = new SphinxParams();
        byte[] key = new byte[16];
        byte[] plain = new byte[32];
        Assertions.assertEquals(
                HexUtils.hexlify(params.rho(key, plain)),
                "66e94bd4ef8a2c3b884cfa59ca342b2e58e2fccefa7e3061367f1d57a4e7455a"
        );
    }

    @Test
    void mu() throws CryptoException {
        SphinxParams params = new SphinxParams();
        byte[] key = new byte[16];
        byte[] plain = new byte[32];
        Assertions.assertEquals(
                HexUtils.hexlify(params.mu(key, plain)),
                "33ad0a1c607ec03b09e6cd9893680ce2"
        );
    }

    @Test
    void getAesKey() throws CryptoException {
        SphinxParams params = new SphinxParams();
        Assertions.assertEquals(
                HexUtils.hexlify(params.deriveAesKeyFromSecret(params.group.Generator)),
                "4dfc0fc4bf89db354d919e212d609602"
        );
    }

    @Test
    void deriveKey() throws CryptoException {
        SphinxParams params = new SphinxParams();
        byte[] key = new byte[16];
        Assertions.assertEquals(
                HexUtils.hexlify(params.deriveKey(key, key)),
                "66e94bd4ef8a2c3b884cfa59ca342b2e"
        );
    }

    @Test
    void hb() throws CryptoException {
        SphinxParams params = new SphinxParams();
        byte[] key = new byte[16];
        Assertions.assertEquals(
                params.hb(key).toString(),
                "223545400317636578258278828935677691076"
        );
    }

    @Test
    void hrho() throws CryptoException {
        SphinxParams params = new SphinxParams();
        byte[] key = new byte[16];
        Assertions.assertEquals(
                HexUtils.hexlify(params.hrho(key)),
                "5b506c5de47d367ea864d82983ab0564"
        );
    }

    @Test
    void hmu() throws CryptoException {
        SphinxParams params = new SphinxParams();
        byte[] key = new byte[16];
        Assertions.assertEquals(
                HexUtils.hexlify(params.hmu(key)),
                "8a3a184296515c9483c5c5849427cc2c"
        );
    }

    @Test
    void hpi() throws CryptoException {
        SphinxParams params = new SphinxParams();
        byte[] key = new byte[16];
        Assertions.assertEquals(
                HexUtils.hexlify(params.hpi(key)),
                "8fce1cb8d2c6d886055e3cf11ed5cb94"
        );
    }

    @Test
    void htau() throws CryptoException {
        SphinxParams params = new SphinxParams();
        byte[] key = new byte[16];
        Assertions.assertEquals(
                HexUtils.hexlify(params.htau(key)),
                "6a330c0b94a3e9635df6c78650a4152a"
        );
    }
}