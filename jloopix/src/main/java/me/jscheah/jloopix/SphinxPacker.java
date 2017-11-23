package me.jscheah.jloopix;

import me.jscheah.sphinx.SphinxParams;

import java.security.SecureRandom;

public class SphinxPacker {
    private SphinxParams params;
    private double expLambda;
    private SecureRandom random;

    public SphinxPacker(SphinxParams params, double expLambda) {
        this.params = params;
        this.expLambda = expLambda;
        random = new SecureRandom();
    }

    public double generateRandomDelay() {
        if (expLambda == 0) {
            return 0;
        }
        return Math.log(1 - random.nextDouble())/(-expLambda);
    }
}