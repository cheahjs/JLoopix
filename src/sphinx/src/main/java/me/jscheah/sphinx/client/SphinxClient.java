package me.jscheah.sphinx.client;

import me.jscheah.sphinx.*;
import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.exceptions.SphinxException;
import me.jscheah.sphinx.msgpack.Packer;
import me.jscheah.sphinx.msgpack.Unpacker;
import me.jscheah.sphinx.params.GroupECC;
import me.jscheah.sphinx.params.SphinxParams;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableLongValueImpl;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class SphinxClient {
    public static final byte RELAY_FLAG = (byte) 0xF0;
    public static final byte DEST_FLAG = (byte) 0xF1;
    public static final byte SURB_FLAG = (byte) 0xF2;

    public static byte[] padBody(int msgtotalsize, byte[] body)
            throws SphinxException {
        body = Arrays.append(body, (byte) 0x7F);
        if (msgtotalsize - body.length < 0)
            throw new SphinxException("Insufficient space for body");

        byte[] padding = new byte[msgtotalsize - body.length];
        for (int i = 0; i < padding.length; i++) {
            padding[i] = (byte) 0xFF;
        }
        return Arrays.concatenate(body, padding);
    }

    public static byte[] unpadBody(byte[] body) {
        int ind = body.length - 1;
        while (body[ind] == (byte) 0xFF && ind > 0) {
            ind--;
        }

        if (body[ind] == (byte) 0x7F) {
            return Arrays.copyOf(body, ind);
        } else {
            return new byte[0];
        }
    }

    public static byte[] Nenc(int idnum) throws IOException {
        return Nenc(new ImmutableLongValueImpl(idnum));
    }

    public static byte[] Nenc(Value value) throws IOException {
        Packer packer = Packer.getPacker();
        packer.packArrayHeader(2)
                .packBinaryHeader(1)
                .addPayload(new byte[]{RELAY_FLAG})
                .packValue(value);

        return packer.toByteArray();
    }

    public static int[] randomSubset(int[] list, int number) {
        assert list.length >= number;
        SecureRandom random = new SecureRandom();
        // Randomly sort list using TreeMap
        Map<Long, Integer> randomList = new TreeMap<>();
        for (int i : list) {
            randomList.put(random.nextLong(), i);
        }
        return randomList.values().stream().limit(number).mapToInt(i -> i).toArray();
    }

    static class HeaderRecord {
        public ECPoint alpha;
        public ECPoint s;
        public BigInteger b;
        public byte[] aes;

        public HeaderRecord(ECPoint alpha, ECPoint s, BigInteger b, byte[] aes) {
            this.alpha = alpha;
            this.s = s;
            this.b = b;
            this.aes = aes;
        }
    }

    public static Pair<SphinxHeader, List<byte[]>> createHeader(SphinxParams params,
                                                                List<byte[]> nodeList,
                                                                List<ECPoint> keys,
                                                                byte[] destination) throws CryptoException {
        List<byte[]> nodeMeta = nodeList.stream()
                .map(x -> Arrays.prepend(x, (byte) x.length))
                .collect(Collectors.toCollection(LinkedList::new));

        GroupECC group = params.group;

        BigInteger blindFactor = params.group.generateSecret();

        List<HeaderRecord> asbTuples = new LinkedList<>();
        for (ECPoint k : keys) {
            ECPoint alpha = group.expon(group.Generator, blindFactor);
            ECPoint s = group.expon(k, blindFactor);
            byte[] aes_s = params.getAesKey(s);

            BigInteger b = params.hb(aes_s);
            blindFactor = blindFactor.multiply(b).mod(group.EcSpec.getCurve().getOrder());

            HeaderRecord hr = new HeaderRecord(alpha, s, b, aes_s);
            asbTuples.add(hr);
        }

        byte[] phi = new byte[0];
        int minLen = params.maxLength - 32;
        for (int i = 1; i < nodeList.size(); i++) {
            byte[] plain = Arrays.concatenate(
                    phi,
                    new byte[params.k + nodeMeta.get(i).length]
            );
            phi = params.xorRho(
                    params.hrho(asbTuples.get(i - 1).aes),
                    Arrays.concatenate(
                            new byte[minLen],
                            plain
                    )
            );
            phi = Arrays.copyOfRange(phi, minLen, phi.length);

            minLen -= nodeMeta.get(i).length + params.k;
        }

        assert phi.length == nodeMeta.stream().skip(1).mapToInt(i -> i.length).sum() + (nodeList.size() - 1) * params.k;

        byte[] finalRouting = Arrays.prepend(
                destination,
                (byte) destination.length
        );

        int metaLen = nodeMeta.stream().skip(1).mapToInt(i -> i.length).sum();
        int randomPadLen = (params.maxLength - 32) - metaLen - ((nodeList.size() - 1) * params.k) - finalRouting.length;

        if (randomPadLen < 0) {
            throw new RuntimeException("Insufficient space for routing info");
        }

        SecureRandom random = new SecureRandom();
        byte[] padding = new byte[randomPadLen];
        random.nextBytes(padding);
        byte[] beta = Arrays.concatenate(
                finalRouting,
                padding
        );
        beta = Arrays.concatenate(
                params.xorRho(
                        params.hrho(asbTuples.get(nodeList.size() - 1).aes),
                        beta
                ), phi);

        byte[] gamma = params.mu(
                params.hmu(asbTuples.get(nodeList.size() - 1).aes),
                beta
        );

        for (int i = nodeList.size() - 2; i >= 0; i--) {
            byte[] nodeId = nodeMeta.get(i + 1);

            int plainBetaLen = (params.maxLength - 32) - params.k - nodeId.length;
            byte[] plain = Arrays.concatenate(
                    nodeId,
                    gamma,
                    Arrays.copyOf(beta, plainBetaLen)
            );

            beta = params.xorRho(params.hrho(asbTuples.get(i).aes), plain);
            gamma = params.mu(params.hmu(asbTuples.get(i).aes), beta);
        }

        return new MutablePair<>(
                new SphinxHeader(asbTuples.get(0).alpha, beta, gamma),
                asbTuples.stream().map(x -> x.aes).collect(Collectors.toCollection(LinkedList::new))
        );
    }

    public static Pair<SphinxHeader, byte[]> createForwardMessage(SphinxParams params,
                                                                  List<byte[]> nodeList,
                                                                  List<ECPoint> keys,
                                                                  Value destination,
                                                                  byte[] message) throws CryptoException, IOException, SphinxException {
        Packer packer = Packer.getPacker();
        packer.packArrayHeader(1)
                .packBinaryHeader(1)
                .addPayload(new byte[]{DEST_FLAG});
        byte[] finalB = packer.toByteArray();

        Pair<SphinxHeader, List<byte[]>> headerSecrets = createHeader(
                params, nodeList, keys, finalB
        );

        packer = Packer.getPacker();
        packer.packArrayHeader(2)
                .packValue(destination)
                .packBinaryHeader(message.length)
                .addPayload(message);
        byte[] packedBytes = packer.toByteArray();
        assert params.k + 1 + packedBytes.length < params.m;
        byte[] body = padBody(params.m,
                Arrays.concatenate(
                        new byte[params.k],
                        packedBytes
                ));
        byte[] delta = params.pi(
                params.hpi(headerSecrets.getValue().get(nodeList.size() - 1)),
                body
        );
        for (int i = nodeList.size() - 2; i >= 0; i--) {
            delta = params.pi(
                    params.hpi(headerSecrets.getValue().get(i)),
                    delta
            );
        }

        return new MutablePair<>(headerSecrets.getKey(), delta);
    }

    public static SingleUseReplyBlockData createSURB(SphinxParams params,
                                                     List<byte[]> nodeList,
                                                     List<ECPoint> keys,
                                                     Value destination) throws IOException, CryptoException {
        SecureRandom random = new SecureRandom();
        byte[] xid = new byte[params.k];
        random.nextBytes(xid);
        Packer packer = Packer.getPacker();
        packer.packArrayHeader(3)
                .packBinaryHeader(1)
                .addPayload(new byte[]{SURB_FLAG})
                .packValue(destination)
                .packBinaryHeader(xid.length)
                .addPayload(xid);

        byte[] finalB = packer.toByteArray();
        Pair<SphinxHeader, List<byte[]>> headerSecrets = createHeader(
                params, nodeList, keys, finalB
        );

        byte[] ktilde = new byte[params.k];
        random.nextBytes(ktilde);

        List<byte[]> keyTuples = new LinkedList<>();
        keyTuples.add(ktilde);
        keyTuples.addAll(headerSecrets.getValue()
                .stream().map(x -> {
                    try {
                        return params.hpi(x);
                    } catch (CryptoException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList()));
        
        return new SingleUseReplyBlockData(
                xid,
                keyTuples,
                new SingleUseReplyBlock(
                        nodeList.get(0), headerSecrets.getKey(), ktilde
                )
        );
    }

    public static Pair<SphinxHeader, byte[]> packageSurb(SphinxParams params,
                                                         SingleUseReplyBlock nymTuple,
                                                         byte[] message) throws SphinxException, CryptoException {
        byte[] body = params.pi(
                nymTuple.ktilde,
                padBody(
                        params.m,
                        Arrays.concatenate(
                                new byte[params.k],
                                message
                        )
                )
        );
        return new MutablePair<>(nymTuple.header, body);
    }

    public static Unpacker receiveForward(SphinxParams params, byte[] delta) {
        for (int i = 0; i < params.k; i++) {
            if (delta[i] != 0) {
                throw new RuntimeException("Modified body");
            }
        }

        delta = unpadBody(Arrays.copyOfRange(delta, params.k, delta.length));
        return Unpacker.getUnpacker(delta);
    }

    public static byte[] receiveSurb(SphinxParams params,
                                     List<byte[]> keyTuple,
                                     byte[] delta) throws CryptoException {
        byte[] ktilde = keyTuple.get(0);
        keyTuple.remove(0);
        int nu = keyTuple.size();
        for (int i = nu-1; i >= 0; i--) {
            delta = params.pi(keyTuple.get(i), delta);
        }
        delta = params.pii(ktilde, delta);

        for (int i = 0; i < params.k; i++) {
            if (delta[i] != 0) {
                throw new RuntimeException("Message corrupted");
            }
        }

        byte[] msg = unpadBody(Arrays.copyOfRange(delta, params.k, delta.length));
        return msg;
    }

    public static byte[] packMessage(SphinxParams params, SphinxHeader h, byte[] m) throws IOException {
        Packer packer = Packer.getPacker();
        packer.packArrayHeader(2)
                .packArrayHeader(2)
                .packInt(params.maxLength)
                .packInt(params.m)
                .packArrayHeader(2)
                .packArrayHeader(3);
        packer.packEcPoint(h.alpha)
                .packBinaryHeader(h.beta.length)
                .addPayload(h.beta)
                .packBinaryHeader(h.gamma.length)
                .addPayload(h.gamma)
                .packBinaryHeader(m.length)
                .addPayload(m);
        return packer.toByteArray();
    }

    public static Pair<SphinxParams, Pair<SphinxHeader, byte[]>> unpackMessage(List<SphinxParams> params, byte[] m) throws IOException {
        Unpacker unpacker = Unpacker.getUnpacker(m);
        unpacker.unpackArrayHeader();
        unpacker.unpackArrayHeader();
        int paramsMaxLength = unpacker.unpackInt();
        int paramsM = unpacker.unpackInt();
        unpacker.unpackArrayHeader();
        unpacker.unpackArrayHeader();
        ECPoint alpha = unpacker.unpackEcPoint();
        byte[] beta = unpacker.readPayload(unpacker.unpackBinaryHeader());
        byte[] gamma = unpacker.readPayload(unpacker.unpackBinaryHeader());

        SphinxHeader header = new SphinxHeader(alpha, beta, gamma);

        byte[] message = unpacker.readPayload(unpacker.unpackBinaryHeader());

        SphinxParams msgParams = null;
        for (SphinxParams param : params) {
            if (paramsMaxLength == param.maxLength &&
                    paramsM == param.m) {
                msgParams = param;
                break;
            }
        }
        if (msgParams == null) {
            throw new RuntimeException("No parameter settings.");
        }
        return new MutablePair<>(msgParams, new MutablePair<>(header, message));
    }
}