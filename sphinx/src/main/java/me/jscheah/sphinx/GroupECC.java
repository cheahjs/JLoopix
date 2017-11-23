package me.jscheah.sphinx;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

public class GroupECC {
    public ECParameterSpec EcSpec;
    private ECCurve mEcCurve;
    public ECPoint Generator;

    public GroupECC() {
        EcSpec = ECNamedCurveTable.getParameterSpec("secp224r1");
        mEcCurve = EcSpec.getCurve();
        Generator = EcSpec.getG();
    }

    public BigInteger generateSecret() {
        BigInteger result;
        BigInteger order = mEcCurve.getOrder();
        SecureRandom rand = new SecureRandom();
        do {
            result = new BigInteger(order.bitLength(), rand);
        } while (result.compareTo(order) >= 0); //exclusive order
        return result;
    }

    public ECPoint expon(ECPoint base, BigInteger exp) {
        return base.multiply(exp);
    }

    public ECPoint multiExpon(ECPoint base, List<BigInteger> exps) {
        BigInteger expon = new BigInteger("1");
        for (BigInteger exp : exps) {
            expon = exp.multiply(expon).mod(mEcCurve.getOrder());
        }
        return expon(base, expon);
    }

    public BigInteger makeExp(byte[] data) {
        //BigInteger expects a two's-complement array, but petlib's from_binary doesn't, so prepend 0x00 to force positive
        return (new BigInteger(Arrays.prepend(data, (byte)0)).mod(mEcCurve.getOrder()));
    }

    public boolean inGroup(ECPoint alpha) {
        return alpha.isValid();
    }

    public byte[] printable(ECPoint alpha) {
        return alpha.getEncoded(false);
    }

    public String printableString(ECPoint alpha) {
        byte[] b = printable(alpha);
        return Utils.hexlify(b);
    }
}