package me.jscheah.sphinx.msgpack;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.core.buffer.ArrayBufferInput;
import org.msgpack.core.buffer.MessageBufferInput;

public class Unpacker extends MessageUnpacker {
    protected Unpacker(MessageBufferInput messageBufferInput, MessagePack.UnpackerConfig unpackerConfig) {
        super(messageBufferInput, unpackerConfig);
    }



    public static Unpacker getUnpacker(byte[] data) {
        return new Unpacker((MessageBufferInput)(new ArrayBufferInput(data)), MessagePack.DEFAULT_UNPACKER_CONFIG);
    }
}
