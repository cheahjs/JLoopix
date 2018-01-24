package me.jscheah.jloopix.client;

import com.google.gson.Gson;
import me.jscheah.jloopix.*;
import me.jscheah.jloopix.database.DBManager;
import me.jscheah.sphinx.CryptoException;
import me.jscheah.sphinx.SphinxException;
import me.jscheah.sphinx.SphinxHeader;
import me.jscheah.sphinx.SphinxParams;
import me.jscheah.sphinx.msgpack.Packer;
import me.jscheah.sphinx.msgpack.Unpacker;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor;
import org.apache.mina.transport.socket.nio.NioDatagramConnector;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.ImmutableArrayValue;
import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableArrayValueImpl;
import org.msgpack.value.impl.ImmutableBinaryValueImpl;
import org.msgpack.value.impl.ImmutableLongValueImpl;
import org.msgpack.value.impl.ImmutableStringValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoopixClient extends IoHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(LoopixClient.class);
    private BigInteger secret;
    private SphinxParams params;
    private String name;
    private String host;
    private short port;
    private String providerName;
    private Config config;
    private DBManager database;
    private ClientCore cryptoClient;
    private Provider provider;
    private LoopixNode selfNode;

    private List<List<MixNode>> pubMixes;
    private List<Provider> pubProviders;
    private List<User> befriendedClients;

    private IoAcceptor acceptor;
    private IoConnector connector;
    private IoSession session;

    private Queue<byte[]> messageQueue;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final SecureRandom random = new SecureRandom();

    public LoopixClient(String name, String host, short port, String providerName, BigInteger secret, Config config) {
        this.secret = secret;
        this.name = name;
        this.host = host;
        this.port = port;
        this.providerName = providerName;
        this.config = config;

        try {
            params = new SphinxParams(1024);
        } catch (CryptoException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        this.selfNode = new LoopixNode(host, port, name, params.group.Generator.multiply(secret), secret);

        cryptoClient = new ClientCore(params, config.getNOISE_LENGTH(), new SphinxPacker(params, config.getEXP_PARAMS_DELAY()), name, port, host, secret, null);

        database = new DBManager(config.getDATABASE_NAME());

        messageQueue = new LinkedList<>();
    }

    /***
     * Creates a LoopixClient from config files.
     * @param configPath Path to config.json
     * @param publicPath Path to publicClient.bin
     * @param privatePath Path to privateClient.bint
     * @return New client
     */
    public static LoopixClient fromFile(String configPath, String publicPath, String privatePath) throws IOException {
        Config config = new Gson().fromJson(new FileReader(configPath), Config.class);
        BigInteger secret = Unpacker.getUnpacker(Files.readAllBytes(Paths.get(privatePath))).unpackBigNumber();
        Unpacker unpacker = Unpacker.getUnpacker(Files.readAllBytes(Paths.get(publicPath)));
        ImmutableArrayValue values = unpacker.unpackValue().asArrayValue();
        return new LoopixClient(
                values.get(1).asRawValue().asString(),
                values.get(3).asRawValue().asString(),
                values.get(2).asIntegerValue().asShort(),
                values.get(5).asRawValue().asString(),
                secret,
                config
        );
    }

    public void run() {
        logger.info("Starting client.");
        getNetworkInfo();
        setupNetwork();
        subscribeToProvider();
        turnOnPacketProcessing();
        makeLoopStream();
        makeDropStream();
        makeRealStream();
    }

    /***
     * Starts UDP listener and connector.
     */
    private void setupNetwork() {
        // Setup server acceptor, since we need to be able to receive incoming packets from the provider
        acceptor = new NioDatagramAcceptor();
        acceptor.setHandler(this);
        DatagramSessionConfig dcfg = (DatagramSessionConfig) acceptor.getSessionConfig();
        dcfg.setReuseAddress(true);
        try {
            logger.info("Trying to listen to {}", this.port);
            acceptor.bind(new InetSocketAddress(this.port));
            logger.info("Listening on port {}", this.port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Setup client connector to provider
        connector = new NioDatagramConnector();
        connector.setHandler(this);
        logger.info("Connecting to {} ({}:{})", this.provider.name, this.provider.host, this.provider.port);
        session = connector.connect(new InetSocketAddress(this.provider.host, this.provider.port))
                .awaitUninterruptibly()
                .getSession();
    }

    /***
     * Fetches network topology from database.
     */
    private void getNetworkInfo() {
        pubMixes = Core.groupLayeredTopology(database.selectAllMixNodes());
        pubProviders = database.selectAllProviders();
        befriendedClients = database.selectAllUsers();

        provider = database.getProviderFromName(providerName);
        if (provider == null) {
            throw new RuntimeException("Failed to find provider in database");
        }
    }

    /***
     * Sends [SUBSCRIBE, name, host, port] to provider.
     */
    private void subscribeToProvider() {
        scheduler.scheduleAtFixedRate(() -> {
            send(new ImmutableArrayValueImpl(new Value[]{
                    new ImmutableStringValueImpl("SUBSCRIBE"),
                    new ImmutableStringValueImpl(this.name),
                    new ImmutableStringValueImpl(this.host),
                    new ImmutableLongValueImpl(this.port)
            }));
        }, 0, config.getTIME_PULL(), TimeUnit.SECONDS);
    }

    /***
     * Starts the initial retrieveMessages call.
     */
    private void turnOnPacketProcessing() {
        retrieveMessages();
    }

    /***
     * Sends [PULL, name] to provider to ask for messages.
     */
    private void retrieveMessages() {
        logger.debug("Retrieving messages.");
            send(new ImmutableArrayValueImpl(new Value[] {
                    new ImmutableStringValueImpl("PULL"),
                    new ImmutableStringValueImpl(this.name)
            }));
        scheduler.schedule(this::retrieveMessages, config.getTIME_PULL(), TimeUnit.SECONDS);
    }

    /***
     * Creates a stream of drop packets.
     */
    private void makeDropStream() {
        logger.debug("Sending drop packet.");
        try {
            sendDropMessage();
        } catch (CryptoException | IOException | SphinxException e) {
            e.printStackTrace();
        }
        scheduler.schedule(this::makeDropStream, sampleTimeFromExponential(config.getEXP_PARAMS_DROP()), TimeUnit.MILLISECONDS);
    }

    /***
     * Sends a DROP message to a random client.
     * @throws CryptoException
     * @throws IOException
     * @throws SphinxException
     */
    private void sendDropMessage() throws CryptoException, IOException, SphinxException {
        User randomReceiver = this.befriendedClients.get(random.nextInt(this.befriendedClients.size()));
        List<LoopixNode> path = constructFullPath(randomReceiver);
        Pair<SphinxHeader, byte[]> loopMessage = cryptoClient.createDropMessage(randomReceiver, path);
        send(new ImmutableArrayValueImpl(new Value[] {
                loopMessage.getKey().toValue(),
                new ImmutableBinaryValueImpl(loopMessage.getValue())
        }));
    }

    /***
     * Creates a stream of loop packets.
     */
    private void makeLoopStream() {
        logger.debug("Sending loop packet.");
        try {
            sendLoopMessage();
        } catch (CryptoException | IOException | SphinxException e) {
            e.printStackTrace();
        }
        scheduler.schedule(this::makeLoopStream, sampleTimeFromExponential(config.getEXP_PARAMS_LOOPS()), TimeUnit.MILLISECONDS);
    }

    /***
     * Sends a loop message back to ourselves.
     * @throws CryptoException
     * @throws IOException
     * @throws SphinxException
     */
    private void sendLoopMessage() throws CryptoException, IOException, SphinxException {
        List<LoopixNode> path = constructFullPath(this);
        Pair<SphinxHeader, byte[]> loopMessage = cryptoClient.createLoopMessage(path);
        send(new ImmutableArrayValueImpl(new Value[] {
            loopMessage.getKey().toValue(),
            new ImmutableBinaryValueImpl(loopMessage.getValue())
        }));
    }

    /***
     * Creates a stream of real packets if there are messages to send, or sends drop packets instead, back to ourselves
     */
    private void makeRealStream() {
        try {
            if (!messageQueue.isEmpty()) {
                logger.debug("Sending real packet.");
                sendRealMessage(messageQueue.remove());
            } else {
                logger.debug("Sending substituting drop packet.");
                sendDropMessage();
            }
        } catch (CryptoException | IOException | SphinxException e) {
            e.printStackTrace();
        }
        scheduler.schedule(this::makeRealStream, sampleTimeFromExponential(config.getEXP_PARAMS_PAYLOAD()), TimeUnit.MILLISECONDS);
    }

    /***
     * Sends a real message back to ourselves.
     * @throws CryptoException
     * @throws IOException
     * @throws SphinxException
     */
    private void sendRealMessage(byte[] data) throws CryptoException, IOException, SphinxException {
        List<LoopixNode> path = constructFullPath(this);
        Pair<SphinxHeader, byte[]> realMessage = cryptoClient.packRealMessage(this.selfNode, path, data);
        send(new ImmutableArrayValueImpl(new Value[] {
                realMessage.getKey().toValue(),
                new ImmutableBinaryValueImpl(realMessage.getValue())
        }));
    }

    /***
     * Sends a msgpack message to the provider.
     * @param val msgpack encoded message
     */
    private synchronized void send(Value val) {
        logger.debug("Sending packet", val);
        if (!session.isConnected())
            logger.warn("Trying to send when session is not connected.");
        Packer packer = Packer.getPacker();
        try {
            packer.packValue(val);
        } catch (IOException e) {
            throw new RuntimeException("Failed to pack value");
        }
        byte[] encodedPacket = packer.toByteArray();
        IoBuffer buffer = IoBuffer.allocate(encodedPacket.length);
        buffer.put(encodedPacket);
        buffer.flip();
        session.write(buffer);
    }

    private List<LoopixNode> constructFullPath(LoopixClient receiver) {
        List<LoopixNode> mixChain = takeRandomMixChain();
        mixChain.add(0, provider);
        mixChain.add(receiver.provider);
        mixChain.add(receiver.selfNode);
        return mixChain;
    }

    private List<LoopixNode> constructFullPath(User receiver) {
        List<LoopixNode> mixChain = takeRandomMixChain();
        mixChain.add(0, provider);
        mixChain.add(database.getProviderFromName(receiver.providerName));
        mixChain.add(receiver);
        return mixChain;
    }

    /***
     * Generates a random chain of mix nodes
     * @return List of mix nodes
     */
    private List<LoopixNode> takeRandomMixChain() {
        List<LoopixNode> mixChain = new LinkedList<>();
        for (List<MixNode> layer : pubMixes) {
            MixNode mixNode = layer.get(random.nextInt(layer.size()));
            mixChain.add(mixNode);
        }
        return mixChain;
    }

    private long sampleTimeFromExponential(double lambda) {
        if (lambda == 0) {
            return 0;
        }
        return (long) ((Math.log(1 - random.nextDouble())/(-lambda)) * 1000);
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        logger.debug("Received {}: {}", message);
        // message is a HeapBuffer
        IoBuffer buffer = (IoBuffer) message;
        Unpacker unpacker = Unpacker.getUnpacker(buffer.array());
        ArrayValue values = unpacker.unpackValue().asArrayValue();
        // TODO: This might have to change to handle tagging of messages
        if (values.get(0).isBinaryValue()) {
            String type = values.get(0).asRawValue().asString();
            logger.info("Received {}", type);
            // Ignore dummy values
            // TODO: Wait what, Loopix sends DUMMY messages in plaintext?
            if (type.equals("DUMMY")) {
                return;
            } else {
                logger.warn("Received unknown message");
            }
        } else if (values.get(0).isArrayValue()) {
            SphinxHeader header = SphinxHeader.fromValue(values.get(0).asArrayValue());
            byte[] body = values.get(1).asRawValue().asByteArray();
            byte[] decryptedBody = cryptoClient.processPacket(new ImmutablePair<>(header, body), secret);
            // Assume we are sending/receiving text messages for now
            logger.info("Received: {}", new String(decryptedBody, Charset.forName("UTF-8")));
        } else {
            logger.warn("Received unknown message");
        }
    }

    /***
     * Adds a message to be sent back to ourselves.
     * @param data Data to send
     */
    public void addMessage(byte[] data) {
        messageQueue.add(data);
    }

    /***
     * Sends test real messages every second
     */
    public void testRealMessage() {
        addMessage(String.format("Hi from time: %d", new Date().getTime()).getBytes(Charset.forName("UTF-8")));
        scheduler.scheduleAtFixedRate(this::testRealMessage, 0, 1, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: jloopix <config.json> <public.bin> <private.bin>");
            return;
        }
        LoopixClient client = LoopixClient.fromFile(args[0], args[1], args[2]);
        client.run();
        client.testRealMessage();
    }
}
