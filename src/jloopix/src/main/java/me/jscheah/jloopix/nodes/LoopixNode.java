package me.jscheah.jloopix.nodes;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class LoopixNode {
    public String host;
    public short port;
    public String name;
    public ECPoint publicKey;

    public LoopixNode(String host, short port, String name, ECPoint publicKey) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.publicKey = publicKey;
    }

    @Override
    public String toString() {
        return String.format("%s (%s:%d)", name, host, port);
    }
}
