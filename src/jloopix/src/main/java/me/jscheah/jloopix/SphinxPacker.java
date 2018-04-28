package me.jscheah.jloopix;

import me.jscheah.jloopix.nodes.LoopixNode;
import me.jscheah.sphinx.client.SphinxClient;
import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.exceptions.SphinxException;
import me.jscheah.sphinx.msgpack.Packer;
import me.jscheah.sphinx.params.SphinxParams;
import me.jscheah.sphinx.server.SphinxNode;
import org.apache.commons.lang3.tuple.Pair;
import me.jscheah.sphinx.*;
import org.bouncycastle.math.ec.ECPoint;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;
import org.msgpack.value.impl.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
        Value destination = receiver.toValue();
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
            routing.add(SphinxClient.encodeNode(getNodeData(node, drop, delay)));
        }
        return routing;
    }

    private Value getNodeData(LoopixNode node, boolean dropFlag, double delay) {
        return ValueFactory.newArray(
                ValueFactory.newArray(
                        ValueFactory.newBinary(node.host.getBytes(StandardCharsets.UTF_8)),
                        ValueFactory.newInteger(node.port)
                ),
                ValueFactory.newBoolean(dropFlag),
                ValueFactory.newNil(),
                ValueFactory.newFloat(delay),
                ValueFactory.newBinary(node.name.getBytes(StandardCharsets.UTF_8))
        );
    }

    public SphinxProcessData decryptSphinxPacket(SphinxPacket packet, BigInteger key)
            throws CryptoException, SphinxException {
        return SphinxNode.processSphinxPacket(this.params, key, packet.header, packet.body);
    }

    public Value handleReceivedForward(byte[] packet) throws IOException, SphinxException {
        return SphinxClient.receiveForward(this.params, packet).unpackValue();
    }
}