package me.jscheah.sphinx;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class PKIEntry {
    public byte id;
    public BigInteger x;
    public ECPoint y;

    public PKIEntry(byte id, BigInteger x, ECPoint y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }
}
