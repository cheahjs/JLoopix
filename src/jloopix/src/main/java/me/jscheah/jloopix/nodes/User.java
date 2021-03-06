package me.jscheah.jloopix.nodes;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class User extends LoopixNode {
    public String providerName;

    public User(String host, short port, String name, ECPoint publicKey, BigInteger privateKey, String providerName) {
        super(host, port, name, publicKey);
        this.providerName = providerName;
    }
}
