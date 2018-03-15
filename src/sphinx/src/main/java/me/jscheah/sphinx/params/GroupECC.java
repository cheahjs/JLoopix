package me.jscheah.sphinx.params;

import me.jscheah.sphinx.HexUtils;
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

    /***
     * Provides group operations in ECC, using the P-224 curve
     */
    public GroupECC() {
        EcSpec = ECNamedCurveTable.getParameterSpec("secp224r1");
        mEcCurve = EcSpec.getCurve();
        Generator = EcSpec.getG();
    }

    /***
     * Returns a new random EC private key
     * @return the generated EC private key
     */
    public BigInteger generateSecret() {
        BigInteger result;
        BigInteger order = mEcCurve.getOrder();
        SecureRandom rand = new SecureRandom();
        do {
            result = new BigInteger(order.bitLength(), rand);
        } while (result.compareTo(order) >= 0); //exclusive order
        return result;
    }

    /***
     * Exponentiation of an {@code ECPoint} {@code base} to the exponent {@code exp}
     * @param base the base to exponentiate
     * @param exp the exponent
     * @return the result of the exponentiation
     */
    public ECPoint expon(ECPoint base, BigInteger exp) {
        return base.multiply(exp);
    }

    /***
     * Exponentiation of an {@code ECPoint} {@code base} to multiple exponents {@code exps}
     * @param base the base to exponentiate
     * @param exps a list of exponents
     * @return the result of the exponentiation
     */
    public ECPoint multiExpon(ECPoint base, List<BigInteger> exps) {
        BigInteger expon = new BigInteger("1");
        // Using the properties of the group we can reduce the number of EC multiplications to just one
        for (BigInteger exp : exps) {
            expon = exp.multiply(expon).mod(mEcCurve.getOrder());
        }
        return expon(base, expon);
    }

    /***
     * Creates a new EC exponent from {@code data}
     * @param data big-endian two's-complement binary representation of BigInteger.
     * @return an EC exponent generated from {@code data}
     */
    public BigInteger makeExp(byte[] data) {
        //BigInteger expects a two's-complement array, but petlib's from_binary doesn't, so prepend 0x00 to force positive
        return (new BigInteger(Arrays.prepend(data, (byte)0)).mod(mEcCurve.getOrder()));
    }

    /***
     * Checks if an ECPoint is in the group
     * @param alpha the ECPoint to check
     * @return true if the ECPoint is in the group
     */
    public boolean inGroup(ECPoint alpha) {
        return alpha.isValid();
    }

    /***
     * Returns the binary representation of a ECPoint
     * @param alpha an ECPoint
     * @return the binary representation of {@code alpha}
     */
    public byte[] printable(ECPoint alpha) {
        return alpha.getEncoded(false);
    }

    /***
     * Returns the hex string representation of a ECPoint
     * @param alpha an ECPoint
     * @return the hex string representation of {@code alpha}
     */
    public String printableString(ECPoint alpha) {
        byte[] b = printable(alpha);
        return HexUtils.hexlify(b);
    }
}
