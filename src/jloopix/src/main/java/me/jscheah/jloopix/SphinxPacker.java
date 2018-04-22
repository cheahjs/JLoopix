package me.jscheah.jloopix;

import me.jscheah.jloopix.nodes.LoopixNode;
import me.jscheah.sphinx.client.SphinxClient;
import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.exceptions.SphinxException;
import me.jscheah.sphinx.params.SphinxParams;
import me.jscheah.sphinx.server.SphinxNode;
import org.apache.commons.lang3.tuple.Pair;
import me.jscheah.sphinx.*;
import org.bouncycastle.math.ec.ECPoint;
import org.msgpack.value.Value;
import org.msgpack.value.impl.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class SphinxPacker {
    private SphinxParams params;
    private double expBeta;


    public SphinxPacker(SphinxParams params, double expBeta) {
        this.params = params;
        this.expBeta = expBeta;
    }

    public SphinxPacket makePacket(LoopixNode receiver,
                                   List<LoopixNode> path,
                                   byte[] message,
                                   byte[] typeFlag)
            throws IOException, CryptoException, SphinxException {
        return makePacket(receiver, path, message, false, typeFlag);
    }

    public SphinxPacket makeDropPacket(LoopixNode receiver,
                                       List<LoopixNode> path,
                                       byte[] message)
            throws IOException, CryptoException, SphinxException {
        return makePacket(receiver, path, message, true, new byte[] {0x02});
    }

    private SphinxPacket makePacket(LoopixNode receiver,
                                    List<LoopixNode> path,
                                    byte[] message,
                                    boolean dropFlag,
                                    byte[] typeFlag)
            throws IOException, CryptoException, SphinxException {
        List<ECPoint> nodeKeys = getNodesPublicKeys(path);
        List<byte[]> routingInfo = getRouting(path, dropFlag, typeFlag);
        Value destination = new ImmutableArrayValueImpl(new Value[] {
                new ImmutableStringValueImpl(receiver.host),
                new ImmutableLongValueImpl(receiver.port),
                new ImmutableStringValueImpl(receiver.name)
        });
        return SphinxClient.createForwardMessage(params, routingInfo, nodeKeys, destination, message);
    }

    private static List<ECPoint> getNodesPublicKeys(List<LoopixNode> nodes) {
        return nodes.stream().map(x -> x.publicKey).collect(Collectors.toList());
    }

    private List<byte[]> getRouting(List<LoopixNode> path, boolean dropFlag, byte[] typeFlag) throws IOException {
        List<byte[]> routing = new LinkedList<>();
        for (int i = 0; i < path.size(); i++) {
            LoopixNode node = path.get(i);
            double delay = Core.randomExponential(expBeta);
            boolean drop = (i == path.size() - 1) && dropFlag;
            routing.add(SphinxClient.encodeNode(new ImmutableArrayValueImpl(new Value[] {
                    new ImmutableArrayValueImpl(new Value[] {
                            new ImmutableStringValueImpl(node.host),
                            new ImmutableLongValueImpl(node.port)
                    }),
                    drop ? ImmutableBooleanValueImpl.TRUE : ImmutableBooleanValueImpl.FALSE,
                    new ImmutableBinaryValueImpl(typeFlag),
                    new ImmutableDoubleValueImpl(delay),
                    new ImmutableStringValueImpl(node.name)
            })));
        }
        return routing;
    }

    public SphinxProcessData decryptSphinxPacket(Pair<SphinxHeader, byte[]> packet, BigInteger key)
            throws CryptoException, SphinxException {
        SphinxProcessData data = SphinxNode.processSphinxPacket(this.params, key, packet.getKey(), packet.getValue());
        return new SphinxProcessData(data.tag, data.routing, data.header, data.delta);
    }

    public Value handleReceivedForward(byte[] packet) throws IOException, SphinxException {
        return SphinxClient.receiveForward(this.params, packet).unpackValue();
    }
}