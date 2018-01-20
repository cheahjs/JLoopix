package me.jscheah.sphinx;

import me.jscheah.sphinx.msgpack.Packer;
import org.bouncycastle.math.ec.ECPoint;
import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableArrayValueImpl;
import org.msgpack.value.impl.ImmutableBinaryValueImpl;
import org.msgpack.value.impl.ImmutableExtensionValueImpl;

import java.io.IOException;

public class SphinxHeader {
    public ECPoint alpha;
    public byte[] beta;
    public byte[] gamma;

    public SphinxHeader(ECPoint alpha, byte[] beta, byte[] gamma) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
    }

    public Value toValue() {
        try {
            return new ImmutableArrayValueImpl(new Value[] {
                    new ImmutableExtensionValueImpl((byte) 2, Packer.ecPointToByteArray(alpha)),
                    new ImmutableBinaryValueImpl(beta),
                    new ImmutableBinaryValueImpl(gamma)
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
