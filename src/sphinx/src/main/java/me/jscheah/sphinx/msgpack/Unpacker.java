package me.jscheah.sphinx.msgpack;

import me.jscheah.sphinx.params.GroupECC;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.core.buffer.ArrayBufferInput;
import org.msgpack.core.buffer.MessageBufferInput;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.ExtensionValue;
import org.msgpack.value.ImmutableValue;
import org.msgpack.value.Value;

import java.io.IOException;
import java.math.BigInteger;

public class Unpacker extends MessageUnpacker {
    protected Unpacker(MessageBufferInput messageBufferInput, MessagePack.UnpackerConfig unpackerConfig) {
        super(messageBufferInput, unpackerConfig);
    }

    public BigInteger unpackBigNumber() throws IOException {
        ImmutableValue value = this.unpackValue();
        if (!value.isExtensionValue())
            throw new RuntimeException("Expected extension value");

        ExtensionValue extValue = value.asExtensionValue();
        if (extValue.getType() != 0)
            throw new RuntimeException("Expected bignum value");

        byte[] data = extValue.getData();
        byte sign = data[0];
        if (sign != '+' && sign != '-')
            throw new RuntimeException("Sign was not pos or neg");

        BigInteger bn = new BigInteger(sign == '+' ? 1 : -1, Arrays.copyOfRange(data, 1, data.length));
        return bn;
    }

    public int unpackEcGroup() throws IOException {
        ImmutableValue value = this.unpackValue();
        if (!value.isExtensionValue())
            throw new RuntimeException("Expected extension value");

        ExtensionValue extValue = value.asExtensionValue();
        if (extValue.getType() != 1)
            throw new RuntimeException("Expected ecgroup value");

        byte[] data = extValue.getData();

        int id = MessagePack.DEFAULT_UNPACKER_CONFIG.newUnpacker(data)
                .unpackValue()
                .asIntegerValue().asInt();
        // We only support secp224r1 at the moment.
        assert id == 713;
        return id;
    }

    public ECPoint unpackEcPoint() throws IOException {
        return unpackEcPoint(this.unpackValue());
    }

    public static ECPoint unpackEcPoint(Value value) throws IOException {
        if (!value.isExtensionValue())
            throw new RuntimeException("Expected extension value");

        ExtensionValue extValue = value.asExtensionValue();
        if (extValue.getType() != 2)
            throw new RuntimeException("Expected ecpoint value");

        byte[] data = extValue.getData();

        MessageUnpacker unpacker = MessagePack.DEFAULT_UNPACKER_CONFIG.newUnpacker(data);
        ArrayValue array = unpacker.unpackValue().asArrayValue();
        int id = array.get(0).asIntegerValue().asInt();
        // We only support secp224r1 at the moment.
        assert id == 713;
        byte[] ecData = array.get(1).asRawValue().asByteArray();
        ECPoint point = new GroupECC().EcSpec.getCurve().decodePoint(ecData);
        return point;
    }

    public static Unpacker getUnpacker(byte[] data) {
        return new Unpacker(new ArrayBufferInput(data), MessagePack.DEFAULT_UNPACKER_CONFIG);
    }
}
