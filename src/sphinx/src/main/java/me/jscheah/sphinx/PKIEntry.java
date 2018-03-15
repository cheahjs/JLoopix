package me.jscheah.sphinx;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

public class PKIEntry {
    public byte id;
    public BigInteger secret;
    public ECPoint pubk;

    public PKIEntry(byte id, BigInteger secret, ECPoint pubk) {
        this.id = id;
        this.secret = secret;
        this.pubk = pubk;
    }
}
