package me.jscheah.sphinx.params;

import me.jscheah.sphinx.HexUtils;
import me.jscheah.sphinx.exceptions.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SphinxParamsTest {
    private SphinxParams params;

    @BeforeEach
    void setup() throws CryptoException {
        params = new SphinxParams();
    }

    @Test
    void testLionessEncThenDecEqual() {
        try {
            byte[] key = "AAAAAAAAAAAAAAAA".getBytes(StandardCharsets.UTF_8);
            byte[] message = "ARGARGARGARGARGARGARGARGARGARGARGARGARGARGARGARG".getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = params.lionessEnc(key, message);
            byte[] decMessage = params.lionessDec(key, ciphertext);
            assertTrue(Arrays.equals(message, decMessage));
        } catch (CryptoException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void testAesEncThenDecEqual() {
        try {
            byte[] key = "AAAAAAAAAAAAAAAA".getBytes(StandardCharsets.UTF_8);
            byte[] message = "Hello World!".getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = params.aesEncrypt(key, message, new byte[16]);
            byte[] decMessage = params.aesEncrypt(key, ciphertext, new byte[16]);
            assertTrue(Arrays.equals(message, decMessage));
        } catch (CryptoException e) {
            throw new Error("Exception thrown when not expected", e);
        }
    }

    @Test
    void xorRho() throws CryptoException {
        byte[] key = new byte[16];
        byte[] plain = new byte[32];
        assertEquals(
                HexUtils.hexlify(params.rho(key, plain)),
                "66e94bd4ef8a2c3b884cfa59ca342b2e58e2fccefa7e3061367f1d57a4e7455a"
        );
    }

    @Test
    void mu() throws CryptoException {
        byte[] key = new byte[16];
        byte[] plain = new byte[32];
        assertEquals(
                HexUtils.hexlify(params.mu(key, plain)),
                "33ad0a1c607ec03b09e6cd9893680ce2"
        );
    }

    @Test
    void deriveAesKeyFromSecret() throws CryptoException {
        assertEquals(
                HexUtils.hexlify(params.deriveAesKeyFromSecret(params.group.Generator)),
                "4dfc0fc4bf89db354d919e212d609602"
        );
    }

    @Test
    void deriveKey() throws CryptoException {
        byte[] key = new byte[16];
        assertEquals(
                HexUtils.hexlify(params.deriveKey(key, key)),
                "66e94bd4ef8a2c3b884cfa59ca342b2e"
        );
    }

    @Test
    void hb() throws CryptoException {
        byte[] key = new byte[16];
        assertEquals(
                params.hb(key).toString(),
                "223545400317636578258278828935677691076"
        );
    }

    @Test
    void hrho() throws CryptoException {
        byte[] key = new byte[16];
        assertEquals(
                HexUtils.hexlify(params.hrho(key)),
                "5b506c5de47d367ea864d82983ab0564"
        );
    }

    @Test
    void hmu() throws CryptoException {
        byte[] key = new byte[16];
        assertEquals(
                HexUtils.hexlify(params.hmu(key)),
                "8a3a184296515c9483c5c5849427cc2c"
        );
    }

    @Test
    void hpi() throws CryptoException {
        byte[] key = new byte[16];
        assertEquals(
                HexUtils.hexlify(params.hpi(key)),
                "8fce1cb8d2c6d886055e3cf11ed5cb94"
        );
    }

    @Test
    void htau() throws CryptoException {
        byte[] key = new byte[16];
        assertEquals(
                HexUtils.hexlify(params.htau(key)),
                "6a330c0b94a3e9635df6c78650a4152a"
        );
    }

    private CryptoException lastExceptionThrown;
    private void joinThreads(Thread t1, Thread t2, Thread t3, Thread t4) throws CryptoException {
        try {
            t1.join();
            t2.join();
            t3.join();
            t4.join();
            if (lastExceptionThrown != null) {
                throw lastExceptionThrown;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void concurrentCipher() throws CryptoException {
        byte[] key = "AAAAAAAAAAAAAAAA".getBytes(StandardCharsets.UTF_8);
        byte[] message = "ARGARGARGARGARGARGARGARGARGARGARGARGARGARGARGARG".getBytes(StandardCharsets.UTF_8);
        Thread t1 = concurrentCipherThread(key, message);
        Thread t2 = concurrentCipherThread(key, message);
        Thread t3 = concurrentCipherThread(key, message);
        Thread t4 = concurrentCipherThread(key, message);
        t1.start();
        t2.start();
        t3.start();
        t4.start();
        joinThreads(t1, t2, t3, t4);
    }

    private Thread concurrentCipherThread(byte[] key, byte[] message) {
        return new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                try {
                    byte[] ciphertext = params.lionessEnc(key, message);
                    byte[] decMessage = params.lionessDec(key, ciphertext);
                    assertTrue(Arrays.equals(message, decMessage));
                } catch (CryptoException e) {
                    lastExceptionThrown = e;
                }
            }
        });
    }

    @Test
    void concurrentHash() throws CryptoException {
        Thread t1 = concurrentHashThread();
        Thread t2 = concurrentHashThread();
        Thread t3 = concurrentHashThread();
        Thread t4 = concurrentHashThread();
        t1.start();
        t2.start();
        t3.start();
        t4.start();
        joinThreads(t1, t2, t3, t4);
    }

    private Thread concurrentHashThread() {
        return new Thread(() -> {
            for (int i = 0; i < 5000; i++) {
                assertEquals(
                        HexUtils.hexlify(params.deriveAesKeyFromSecret(params.group.Generator)),
                        "4dfc0fc4bf89db354d919e212d609602"
                );
            }
        });
    }

    @Test
    void concurrentHMAC() throws CryptoException {
        Thread t1 = concurrentHMACThread();
        Thread t2 = concurrentHMACThread();
        Thread t3 = concurrentHMACThread();
        Thread t4 = concurrentHMACThread();
        t1.start();
        t2.start();
        t3.start();
        t4.start();
        joinThreads(t1, t2, t3, t4);
    }

    private Thread concurrentHMACThread() {
        return new Thread(() -> {
            for (int i = 0; i < 5000; i++) {
                try {
                    byte[] key = new byte[16];
                    byte[] plain = new byte[32];
                    assertEquals(
                            HexUtils.hexlify(params.mu(key, plain)),
                            "33ad0a1c607ec03b09e6cd9893680ce2"
                    );
                } catch (CryptoException e) {
                    lastExceptionThrown = e;
                }
            }
        });
    }
}