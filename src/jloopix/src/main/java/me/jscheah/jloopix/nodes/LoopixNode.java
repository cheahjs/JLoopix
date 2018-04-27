package me.jscheah.jloopix.nodes;

import org.bouncycastle.math.ec.ECPoint;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class LoopixNode {
    public String host;
    public short port;
    public String name;
    public ECPoint publicKey;

    public LoopixNode(String host, short port, String name, ECPoint publicKey) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.publicKey = publicKey;
    }

    public Value toValue() {
        return ValueFactory.newArray(
                ValueFactory.newBinary(this.host.getBytes(StandardCharsets.UTF_8)),
                ValueFactory.newInteger(this.port),
                ValueFactory.newBinary(this.name.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return String.format("%s (%s:%d)", name, host, port);
    }
}
