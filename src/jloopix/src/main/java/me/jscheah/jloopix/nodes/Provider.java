package me.jscheah.jloopix.nodes;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class Provider extends LoopixNode {
    public Provider(String host, short port, String name, ECPoint publicKey, BigInteger privateKey) {
        super(host, port, name, publicKey);
    }
}
