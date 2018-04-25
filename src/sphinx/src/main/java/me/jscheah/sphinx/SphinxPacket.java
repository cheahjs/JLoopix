package me.jscheah.sphinx;

import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableArrayValueImpl;
import org.msgpack.value.impl.ImmutableBinaryValueImpl;

public class SphinxPacket {
    public SphinxHeader header;
    public byte[] body;

    public SphinxPacket(SphinxHeader header, byte[] body) {
        this.header = header;
        this.body = body;
    }

    public Value toValue() {
        return new ImmutableArrayValueImpl(new Value[] {
                this.header.toValue(),
                new ImmutableBinaryValueImpl(body)
        });
    }
}
