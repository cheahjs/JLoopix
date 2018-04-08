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
import org.msgpack.value.ArrayValue;
import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableArrayValueImpl;
import org.msgpack.value.impl.ImmutableBinaryValueImpl;
import org.msgpack.value.impl.ImmutableExtensionValueImpl;
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

    private static byte[] padBody(int msgtotalsize, byte[] body)
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

    private static byte[] unpadBody(byte[] body) {
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
        /**
         * Group element
         */
        public ECPoint alpha;
        /**
         * Diffie-Hellman shared secret
         */
        public ECPoint s;
        /**
         * Blinding factor
         */
        public BigInteger b;
        /**
         * AES key
         */
        public byte[] aes;

        public HeaderRecord(ECPoint alpha, ECPoint s, BigInteger b, byte[] aes) {
            this.alpha = alpha;
            this.s = s;
            this.b = b;
            this.aes = aes;
        }
    }

    static class SphinxHeaderData {
        public SphinxHeader header;
        public List<byte[]> keys;

        public SphinxHeaderData(SphinxHeader header, List<byte[]> keys) {
            this.header = header;
            this.keys = keys;
        }
    }

    /**
     * Creates a Sphinx header
     * @param params Sphinx parameters to be used
     * @param path List of per hop data
     * @param keys List of per hop public keys
     * @param destination Destination data
     * @return The Sphinx packet header, and a list of keys used to encrypt the packet payload
     * @throws CryptoException
     */
    private static SphinxHeaderData createHeader(SphinxParams params,
                                                 List<byte[]> path,
                                                 List<ECPoint> keys,
                                                 byte[] destination) throws CryptoException {
        List<byte[]> hopData = path.stream()
                .map(x -> Arrays.prepend(x, (byte) x.length))
                .collect(Collectors.toCollection(LinkedList::new));

        GroupECC group = params.group;
        BigInteger blindFactor = params.group.generateSecret();

        // Derive key material for each hop
        List<HeaderRecord> asbTuples = new LinkedList<>();
        for (ECPoint k : keys) {
            ECPoint alpha = group.expon(group.Generator, blindFactor);
            ECPoint s = group.expon(k, blindFactor);
            byte[] aes_s = params.getAesKeyFromSecret(s);

            BigInteger b = params.hb(aes_s);
            blindFactor = blindFactor.multiply(b).mod(group.EcSpec.getCurve().getOrder());

            HeaderRecord hr = new HeaderRecord(alpha, s, b, aes_s);
            asbTuples.add(hr);
        }

        // Derive the routing information block keystream and encrypted padding (filler strings)
        byte[] phi = new byte[0];
        int minLen = params.headerSize - 32;
        for (int i = 1; i < path.size(); i++) {
            byte[] plain = Arrays.concatenate(
                    phi,
                    new byte[params.k + hopData.get(i).length]
            );
            phi = params.rho(
                    params.hrho(asbTuples.get(i - 1).aes),
                    Arrays.concatenate(
                            new byte[minLen],
                            plain
                    )
            );
            phi = Arrays.copyOfRange(phi, minLen, phi.length);

            minLen -= hopData.get(i).length + params.k;
        }

        assert phi.length == hopData.stream().skip(1).mapToInt(i -> i.length).sum() + (path.size() - 1) * params.k;

        byte[] finalRouting = Arrays.prepend(
                destination,
                (byte) destination.length
        );

        int metaLen = hopData.stream().skip(1).mapToInt(i -> i.length).sum();
        int randomPadLen = (params.headerSize - 32) - metaLen - ((path.size() - 1) * params.k) - finalRouting.length;

        if (randomPadLen < 0) {
            throw new RuntimeException("Insufficient space for routing info");
        }

        // Add routing information
        SecureRandom random = new SecureRandom();
        byte[] padding = new byte[randomPadLen];
        random.nextBytes(padding);
        byte[] beta = Arrays.concatenate(
                finalRouting,
                padding
        );
        beta = Arrays.concatenate(
                params.rho(
                        params.hrho(asbTuples.get(path.size() - 1).aes),
                        beta
                ), phi);

        // Create HMAC
        byte[] gamma = params.mu(
                params.hmu(asbTuples.get(path.size() - 1).aes),
                beta
        );

        for (int i = path.size() - 2; i >= 0; i--) {
            byte[] nodeId = hopData.get(i + 1);

            int plainBetaLen = (params.headerSize - 32) - params.k - nodeId.length;
            byte[] plain = Arrays.concatenate(
                    nodeId,
                    gamma,
                    Arrays.copyOf(beta, plainBetaLen)
            );

            beta = params.rho(params.hrho(asbTuples.get(i).aes), plain);
            gamma = params.mu(params.hmu(asbTuples.get(i).aes), beta);
        }

        return new SphinxHeaderData(
                new SphinxHeader(asbTuples.get(0).alpha, beta, gamma),
                asbTuples.stream().map(x -> x.aes).collect(Collectors.toCollection(LinkedList::new))
        );
    }

    public static SphinxPacket createForwardMessage(SphinxParams params,
                                                    List<byte[]> path,
                                                    List<ECPoint> keys,
                                                    Value destination,
                                                    byte[] message) throws CryptoException, IOException, SphinxException {
        // Pack destination routing command
        Packer packer = Packer.getPacker();
        packer.packArrayHeader(1)
                .packBinaryHeader(1)
                .addPayload(new byte[]{DEST_FLAG});
        byte[] finalB = packer.toByteArray();

        // Create header and secrets
        SphinxHeaderData header = createHeader(
                params, path, keys, finalB
        );

        // Pack
        packer = Packer.getPacker();
        packer.packArrayHeader(2)
                .packValue(destination)
                .packBinaryHeader(message.length)
                .addPayload(message);
        byte[] packedBytes = packer.toByteArray();
        assert params.k + 1 + packedBytes.length < params.bodySize;
        byte[] paddedBody = padBody(params.bodySize,
                Arrays.concatenate(
                        new byte[params.k],
                        packedBytes
                ));
        byte[] delta = paddedBody;
        for (int i = path.size() - 1; i >= 0; i--) {
            delta = params.pi(
                    params.hpi(header.keys.get(i)),
                    delta
            );
        }

        return new SphinxPacket(header.header, delta);
    }

    public static SingleUseReplyBlockData createSURB(SphinxParams params,
                                                     List<byte[]> path,
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
        SphinxHeaderData headerSecrets = createHeader(
                params, path, keys, finalB
        );

        byte[] ktilde = new byte[params.k];
        random.nextBytes(ktilde);

        List<byte[]> keyTuples = new LinkedList<>();
        keyTuples.add(ktilde);
        keyTuples.addAll(headerSecrets.keys
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
                        path.get(0), headerSecrets.header, ktilde
                )
        );
    }

    public static SphinxPacket packageSURB(SphinxParams params,
                                           SingleUseReplyBlock nymTuple,
                                           byte[] message) throws SphinxException, CryptoException {
        byte[] body = params.pi(
                nymTuple.ktilde,
                padBody(
                        params.bodySize,
                        Arrays.concatenate(
                                new byte[params.k],
                                message
                        )
                )
        );
        return new SphinxPacket(nymTuple.header, body);
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

    public static byte[] packMessage(SphinxParams params, SphinxPacket packet) throws IOException {
        return packMessage(params, packet.header, packet.body);
    }

    public static byte[] packMessage(SphinxParams params, SphinxHeader h, byte[] m) throws IOException {
        Value packet = new ImmutableArrayValueImpl(new Value[] {
                // parameters
                new ImmutableArrayValueImpl(new Value[] {
                        new ImmutableLongValueImpl(params.headerSize),
                        new ImmutableLongValueImpl(params.bodySize)
                }),
                // packet tuple (header, body)
                new ImmutableArrayValueImpl(new Value[] {
                        // header tuple (alpha, beta, gamma)
                        new ImmutableArrayValueImpl(new Value[] {
                                new ImmutableExtensionValueImpl((byte) 2, Packer.ecPointToByteArray(h.alpha)),
                                new ImmutableBinaryValueImpl(h.beta),
                                new ImmutableBinaryValueImpl(h.gamma)
                        }),
                        // body
                        new ImmutableBinaryValueImpl(m)
                })
        });
        Packer packer = Packer.getPacker();
        packer.packValue(packet);
        return packer.toByteArray();
    }

    public static Pair<SphinxParams, Pair<SphinxHeader, byte[]>> unpackMessage(List<SphinxParams> params, byte[] m) throws IOException {
        Unpacker unpacker = Unpacker.getUnpacker(m);
        ArrayValue packet = unpacker.unpackValue().asArrayValue();

        ArrayValue parameters = packet.get(0).asArrayValue();
        int paramsMaxLength = parameters.get(0).asIntegerValue().asInt();
        int paramsM = parameters.get(1).asIntegerValue().asInt();

        ArrayValue tuple = packet.get(1).asArrayValue();
        ArrayValue headerTuple = tuple.get(0).asArrayValue();
        ECPoint alpha = Unpacker.unpackEcPoint(headerTuple.get(0));
        byte[] beta = headerTuple.get(1).asBinaryValue().asByteArray();
        byte[] gamma = headerTuple.get(2).asBinaryValue().asByteArray();

        SphinxHeader header = new SphinxHeader(alpha, beta, gamma);

        byte[] message = tuple.get(1).asBinaryValue().asByteArray();

        SphinxParams msgParams = null;
        for (SphinxParams param : params) {
            if (paramsMaxLength == param.headerSize &&
                    paramsM == param.bodySize) {
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