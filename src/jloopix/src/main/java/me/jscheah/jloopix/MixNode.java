package me.jscheah.jloopix;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class MixNode extends LoopixNode {
    public int groupID;

    public MixNode(String host, short port, String name, ECPoint publicKey, BigInteger privateKey, int groupID) {
        super(host, port, name, publicKey, privateKey);
        this.groupID = groupID;
    }
}
