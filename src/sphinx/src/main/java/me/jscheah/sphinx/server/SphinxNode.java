package me.jscheah.sphinx.server;

import me.jscheah.sphinx.SphinxHeader;
import me.jscheah.sphinx.SphinxProcessData;
import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.exceptions.SphinxException;
import me.jscheah.sphinx.params.GroupECC;
import me.jscheah.sphinx.params.SphinxParams;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

import java.math.BigInteger;

public class SphinxNode {

    /***
     * Processes incoming messages
     * @param params Parameters to use
     * @param secret Server secret
     * @param header Message header
     * @param delta Message body
     */
    public static SphinxProcessData processSphinxPacket(SphinxParams params, BigInteger secret, SphinxHeader header, byte[] delta)
            throws SphinxException, CryptoException {
        GroupECC group = params.group;

        // Check is alpha in group
        if (!group.inGroup(header.alpha))
            throw new SphinxException("Alpha not in Group.");

        // Compute shared secret
        ECPoint sharedSecret = group.expon(header.alpha, secret);
        byte[] aesSecret = params.deriveAesKeyFromSecret(sharedSecret);

        assert header.beta.length == params.headerSize - 32;

        // Compute and compare MAC
        if (!java.util.Arrays.equals(header.gamma,
                params.mu(
                        params.hmu(aesSecret), header.beta
                ))) {
            throw new SphinxException("MAC mismatch.");
        }

        // pad beta
        byte[] betaPadded = Arrays.concatenate(
                header.beta,
                new byte[2*params.k]
        );
        byte[] B = params.rho(params.hrho(aesSecret), betaPadded);

        // problematic cast from signed byte to unsigned int
        int length = (((int)B[0]) & 0xFF);
        byte[] routing = Arrays.copyOfRange(B, 1, 1+length);
        byte[] rest = Arrays.copyOfRange(B, 1+length, B.length);

        // Compute replay tag
        byte[] tag = params.htau(aesSecret);
        // Compute next hop blinding factor and group element
        BigInteger b = params.hb(aesSecret);
        ECPoint alpha = group.expon(header.alpha, b);
        // Copy MAC for next hop
        byte[] gamma = Arrays.copyOf(rest, params.k);
        // Copy beta for next hop
        byte[] beta = Arrays.copyOfRange(rest, params.k, params.k + (params.headerSize - params.k*2));
        // Decrypt delta
        delta = params.pii(params.hpi(aesSecret), delta);

        return new SphinxProcessData(tag, routing, new SphinxHeader(alpha, beta, gamma), delta);
    }
}
