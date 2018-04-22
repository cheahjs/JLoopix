package me.jscheah.jloopix.client;

import me.jscheah.sphinx.client.SphinxClient;
import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.exceptions.SphinxException;
import org.apache.commons.lang3.tuple.Pair;
import me.jscheah.jloopix.Core;
import me.jscheah.jloopix.nodes.LoopixNode;
import me.jscheah.jloopix.SphinxPacker;
import me.jscheah.sphinx.*;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.Value;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.List;

class ClientCore extends LoopixNode {
    private int noiseLength;
    private SphinxPacker packer;

    ClientCore(int noiseLength, SphinxPacker packer, String name, short port, String host, ECPoint publicKey) {
        super(host, port, name, publicKey);
        this.noiseLength = noiseLength;
        this.packer = packer;
    }

    /**
     * Creates a LOOP message. The message has the payload 'HT' + RANDOM(noiseLength)
     * @param path The path the message will take
     * @return A SphinxPacket to be forwarded
     * @throws SphinxException
     * @throws IOException
     * @throws CryptoException
     */
    SphinxPacket createLoopMessage(List<LoopixNode> path)
            throws SphinxException, IOException, CryptoException {
        byte[] message = Arrays.concatenate(
                "HT".getBytes(Charset.forName("UTF-8")),
                Core.generateRandomBytes(noiseLength)
        );
        return packer.makePacket(this, path, message, new byte[0x03]);
    }

    /**
     * Creates a LOOP message. The message has a random payload with a drop flag in the header
     * @param path The path the message will take
     * @return A SphinxPacket to be forwarded
     * @throws SphinxException
     * @throws IOException
     * @throws CryptoException
     */
    SphinxPacket createDropMessage(LoopixNode randomReceiver, List<LoopixNode> path)
            throws SphinxException, IOException, CryptoException {
        byte[] message = Core.generateRandomBytes(noiseLength);
        return packer.makeDropPacket(randomReceiver, path, message);
}

    SphinxPacket createRealMessage(LoopixNode receiver, List<LoopixNode> path, byte[] message)
            throws SphinxException, IOException, CryptoException {
        return packer.makePacket(receiver, path, message, new byte[0x01]);
    }

    byte[] processPacket(Pair<SphinxHeader, byte[]> packet, BigInteger privk)
            throws SphinxException, IOException, CryptoException {
        SphinxProcessData sphinxProcessData = packer.decryptSphinxPacket(packet, privk);
        byte routingFlag = sphinxProcessData.routing[sphinxProcessData.routing.length-1];
        if (routingFlag == SphinxClient.DEST_FLAG) {
            Value value = packer.handleReceivedForward(sphinxProcessData.delta);
            ArrayValue outerTuple = value.asArrayValue();
            ArrayValue destination = outerTuple.get(0).asArrayValue();
            byte[] message = outerTuple.get(1).asRawValue().asByteArray();
            if (isDestinationSelf(destination)) {
                return message;
            } else {
                throw new RuntimeException("Received message not meant for us");
            }
        }
        throw new RuntimeException("Processed non-destination packet");
    }

    private boolean isDestinationSelf(ArrayValue dest) {
        return host.equals(dest.get(0).asRawValue().asString())
                && port == dest.get(1).asIntegerValue().asShort()
                && name.equals(dest.get(2).asRawValue().asString());
    }
}
