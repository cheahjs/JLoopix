package me.jscheah.jloopix;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class LoopixNode {
    public String host;
    public short port;
    public String name;
    public ECPoint publicKey;
    public BigInteger privateKey;

    public LoopixNode(String host, short port, String name, ECPoint publicKey, BigInteger privateKey) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    @Override
    public String toString() {
        return String.format("%s (%s:%d)", name, host, port);
    }
}
