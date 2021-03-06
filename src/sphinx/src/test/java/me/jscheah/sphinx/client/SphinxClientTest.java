package me.jscheah.sphinx.client;

import me.jscheah.sphinx.*;
import me.jscheah.sphinx.client.SphinxClient;
import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.exceptions.SphinxException;
import me.jscheah.sphinx.params.SphinxParams;
import me.jscheah.sphinx.server.SphinxNode;
import org.apache.commons.lang3.tuple.Pair;
import me.jscheah.sphinx.msgpack.Unpacker;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.msgpack.value.ImmutableArrayValue;
import org.msgpack.value.impl.ImmutableBinaryValueImpl;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

class SphinxClientTest {
    @Test
    void nenc() throws IOException {
        byte[] encoded = SphinxClient.encodeNode(8);
        Assertions.assertArrayEquals(
                encoded,
                new byte[]{(byte) 0x92, (byte) 0xc4, 0x01, (byte) 0xf0, 0x08});
    }

    @Test
    void fullClientTest() throws CryptoException, IOException, SphinxException {
        int pathLength = 5;
        SphinxParams params = new SphinxParams();

        Map<Byte, PKIEntry> pkiPriv = new HashMap<>();
        Map<Byte, PKIEntry> pkiPub = new HashMap<>();

        for (byte i = 0; i < 10; i++) {
            BigInteger x = params.group.generateSecret();
            ECPoint y = params.group.expon(params.group.Generator, x);
            pkiPriv.put(i, new PKIEntry(i, x, y));
            pkiPub.put(i, new PKIEntry(i, null, y));
        }

        int[] path = SphinxClient.randomSubset(pkiPriv.keySet().stream().mapToInt(x -> x).toArray(), pathLength);
        List<byte[]> routing = Arrays.stream(path).mapToObj(x -> {
            try {
                return SphinxClient.encodeNode(x);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        List<ECPoint> nodeKeys = Arrays.stream(path).mapToObj(a -> pkiPub.get(((byte)(int)a)).pubk).collect(Collectors.toList());
        byte[] dest = "bob".getBytes(StandardCharsets.UTF_8);
        byte[] message = "this is a test".getBytes(StandardCharsets.UTF_8);
        SphinxPacket forward = SphinxClient.createForwardMessage(params, routing, nodeKeys, new ImmutableBinaryValueImpl(dest), message);

        byte[] binaryMessage = SphinxClient.packMessage(params, forward);
        List<SphinxParams> paramList = new LinkedList<>();
        paramList.add(params);
        Pair<SphinxParams, Pair<SphinxHeader, byte[]>> unpackedMessage = SphinxClient.unpackMessage(paramList, binaryMessage);

        Assertions.assertEquals(forward.header.alpha, unpackedMessage.getValue().getKey().alpha);
        Assertions.assertArrayEquals(forward.header.beta, unpackedMessage.getValue().getKey().beta);
        Assertions.assertArrayEquals(forward.header.gamma, unpackedMessage.getValue().getKey().gamma);
        Assertions.assertArrayEquals(forward.body, unpackedMessage.getValue().getValue());

        BigInteger x = pkiPriv.get((byte) path[0]).secret;
        SphinxHeader header = forward.header;
        byte[] delta = forward.body;
        while (true) {
            SphinxProcessData ret = SphinxNode.processSphinxPacket(params, x, header, delta);
            header = ret.header;
            delta = ret.delta;
            Unpacker unpacker = Unpacker.getUnpacker(ret.routing);
            ImmutableArrayValue root = unpacker.unpackValue().asArrayValue();
            byte flag = root.get(0).asRawValue().asByteArray()[0];

            if (flag == SphinxClient.RELAY_FLAG) {
                byte addr = root.get(1).asIntegerValue().asByte();
                x = pkiPriv.get(addr).secret;
            } else if (flag == SphinxClient.DEST_FLAG) {
                Assertions.assertEquals(1, root.size());
                Assertions.assertArrayEquals(Arrays.copyOf(ret.delta, 16), new byte[params.k]);
                Unpacker forwardUnpacker = SphinxClient.receiveForward(params, ret.delta);
                ImmutableArrayValue forwardRoot = forwardUnpacker.unpackValue().asArrayValue();
                byte[] decDest = forwardRoot.get(0).asRawValue().asByteArray();
                byte[] decMsg = forwardRoot.get(1).asRawValue().asByteArray();
                Assertions.assertArrayEquals(decDest, dest);
                Assertions.assertArrayEquals(decMsg, message);
                break;
            } else {
                throw new RuntimeException("Invalid flag");
            }
        }

        SingleUseReplyBlockData surb = SphinxClient.createSURB(params, routing, nodeKeys, new ImmutableBinaryValueImpl("myself".getBytes(StandardCharsets.UTF_8)));
        message = "This is a reply".getBytes(StandardCharsets.UTF_8);
        SphinxPacket surbPackage = SphinxClient.packageSURB(params, surb.nymTuple, message);

        x = pkiPriv.get((byte)path[0]).secret;

        header = surbPackage.header;
        delta = surbPackage.body;
        while (true) {
            SphinxProcessData ret = SphinxNode.processSphinxPacket(params, x, header, delta);
            header = ret.header;
            delta = ret.delta;
            Unpacker unpacker = Unpacker.getUnpacker(ret.routing);
            ImmutableArrayValue root = unpacker.unpackValue().asArrayValue();
            byte flag = root.get(0).asRawValue().asByteArray()[0];

            if (flag == SphinxClient.RELAY_FLAG) {
                byte addr = root.get(1).asIntegerValue().asByte();
                x = pkiPriv.get(addr).secret;
            } else if (flag == SphinxClient.SURB_FLAG) {
                break;
            }
        }

        byte[] received = SphinxClient.receiveSurb(params, surb.surbKeyTuple, delta);
        Assertions.assertArrayEquals(received, message);
    }
}