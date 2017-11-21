package me.jscheah.sphinx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class SphinxClientTest {
    @Test
    void padBody() {
    }

    @Test
    void unpadBody() {
    }

    @Test
    void nenc() throws IOException {
        byte[] encoded = SphinxClient.Nenc(8);
        Assertions.assertArrayEquals(
                encoded,
                new byte[]{(byte) 0x92, (byte) 0xc4, 0x01, (byte) 0xf0, 0x08});
    }
}