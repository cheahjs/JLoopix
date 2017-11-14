package me.jscheah.sphinx;

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
            byte[] ciphertext = params.aesCtr(key, message, new byte[16]);
            byte[] decMessage = params.aesCtr(key, ciphertext, new byte[16]);
            Assertions.assertTrue(Arrays.equals(message, decMessage));
        } catch (CryptoException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }
}