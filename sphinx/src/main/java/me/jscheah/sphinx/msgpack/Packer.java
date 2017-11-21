package me.jscheah.sphinx.msgpack;

import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.buffer.ArrayBufferOutput;

import java.io.IOException;
import java.math.BigInteger;

public class Packer extends MessageBufferPacker {
    protected Packer(MessagePack.PackerConfig packerConfig) {
        super(packerConfig);
    }

    protected Packer(ArrayBufferOutput arrayBufferOutput, MessagePack.PackerConfig packerConfig) {
        super(arrayBufferOutput, packerConfig);
    }

    public MessagePacker packBigNumber(BigInteger bn) throws IOException {
        byte sign = 0;
        if (bn.compareTo(new BigInteger("0")) < 0) {
            sign = '-';
            bn = bn.abs();
        } else {
            sign = '+';
        }
        byte[] data = Arrays.prepend(bn.toByteArray(), sign);
        this.packExtensionTypeHeader((byte) 0, data.length);
        this.addPayload(data);
        return this;
    }

    /**
     * Packs id of EcGroup used.
     * Fixed at id=713
     */
    public MessagePacker packEcGroup() throws IOException {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        packer.packInt(713);

        byte[] data = packer.toByteArray();
        this.packExtensionTypeHeader((byte) 1, data.length);
        this.addPayload(data);
        return this;
    }

    /**
     * Packs id of EcGroup used and EcPoint data.
     * EcGroup id fixed at 713
     */
    public MessagePacker packEcPoint(ECPoint point) throws IOException {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        packer.packInt(713);
        byte[] pointData = point.getEncoded(false);
        packer.packBinaryHeader(pointData.length);
        packer.addPayload(pointData);

        byte[] data = packer.toByteArray();
        this.packExtensionTypeHeader((byte) 2, data.length);
        this.addPayload(data);
        return this;
    }

    public static Packer getPacker() {
        return new Packer(MessagePack.DEFAULT_PACKER_CONFIG);
    }
}
