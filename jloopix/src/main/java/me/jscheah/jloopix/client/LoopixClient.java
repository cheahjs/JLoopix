package me.jscheah.jloopix.client;

import me.jscheah.sphinx.CryptoException;
import me.jscheah.sphinx.SphinxParams;

import java.math.BigInteger;

public class LoopixClient {
    private BigInteger secret;
    private SphinxParams params;
    private String name;
    private String host;
    private short port;
    private String providerName;

    public LoopixClient(String name, String host, short port, String providerName, BigInteger secret) {
        this.secret = secret;
        this.name = name;
        this.host = host;
        this.port = port;
        this.providerName = providerName;

        try {
            params = new SphinxParams(1024);
        } catch (CryptoException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static LoopixClient fromFile(String path) {
        throw new RuntimeException("Stub!");
    }

    public void run() {
        throw new RuntimeException("Stub!");
    }
}
