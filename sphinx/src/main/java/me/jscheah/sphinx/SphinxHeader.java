package me.jscheah.sphinx;

import org.bouncycastle.math.ec.ECPoint;

public class SphinxHeader {
    public ECPoint alpha;
    public byte[] beta;
    public byte[] gamma;

    public SphinxHeader(ECPoint alpha, byte[] beta, byte[] gamma) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
    }
}
