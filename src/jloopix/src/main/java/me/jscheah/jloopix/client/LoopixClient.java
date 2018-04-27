package me.jscheah.jloopix.client;

import com.google.gson.Gson;
import me.jscheah.jloopix.*;
import me.jscheah.jloopix.database.DBManager;
import me.jscheah.jloopix.nodes.LoopixNode;
import me.jscheah.jloopix.nodes.MixNode;
import me.jscheah.jloopix.nodes.Provider;
import me.jscheah.jloopix.nodes.User;
import me.jscheah.sphinx.SphinxPacket;
import me.jscheah.sphinx.exceptions.CryptoException;
import me.jscheah.sphinx.exceptions.SphinxException;
import me.jscheah.sphinx.SphinxHeader;
import me.jscheah.sphinx.params.SphinxParams;
import me.jscheah.sphinx.msgpack.Packer;
import me.jscheah.sphinx.msgpack.Unpacker;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.apache.mina.transport.socket.nio.NioDatagramConnector;
import org.bouncycastle.math.ec.ECPoint;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.ImmutableArrayValue;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;
import org.msgpack.value.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LoopixClient extends IoHandlerAdapter {
    // Logging
    private final Logger logger = LoggerFactory.getLogger(LoopixClient.class);
    // Node parameters
    private BigInteger privateKey;
    private String name;
    private String host;
    private short port;
    private ECPoint publicKey;
    private String providerName;
    private Provider provider;
    private LoopixNode selfNode;
    // Parameter config
    private Config config;
    private DBManager database;
    private ClientCore cryptoClient;
    // Network data
    private List<List<MixNode>> mixList;
    private List<Provider> providerList;
    private List<User> clientList;
    // Network session to provider
    private IoSession session;

    private LoopixMessageListener messageListener;
    private Queue<ClientMessage> messageQueue;
    private LoopixMessageBuilder messageBuilder;

    private final SecureRandom random = new SecureRandom();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public LoopixClient(String name, String host, short port, String providerName, ECPoint publicKey, BigInteger privateKey, Config config) {
        this.privateKey = privateKey;
        this.name = name;
        this.host = host;
        this.port = port;
        this.publicKey = publicKey;
        this.providerName = providerName;
        this.config = config;

        SphinxParams params;
        try {
            params = new SphinxParams(1024);
        } catch (CryptoException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        this.selfNode = new LoopixNode(host, port, name, publicKey);

        cryptoClient = new ClientCore(config.getNOISE_LENGTH(), new SphinxPacker(params, config.getEXP_PARAMS_DELAY()), name, port, host, publicKey);

        database = new DBManager(config.getDATABASE_NAME());

        messageQueue = new LinkedList<>();
    }

    /***
     * Creates a LoopixClient from config files.
     * @param configPath Path to config.json
     * @param publicPath Path to publicClient.bin
     * @param privatePath Path to privateClient.bin
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
                Unpacker.unpackEcPoint(values.get(4)),
                secret,
                config
        );
    }

    public String getName() {
        return name;
    }

    public List<User> getClientList() {
        return clientList;
    }

    public Config getConfig() {
        return config;
    }

    public User getSelfUser() {
        return new User(selfNode.host, selfNode.port, selfNode.name, selfNode.publicKey, null, providerName);
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

    public void stop() {
        scheduler.shutdownNow();
        session.closeOnFlush();
    }

    /***
     * Starts UDP listener and connector.
     */
    private void setupNetwork() {
        // Setup client connector to provider
        IoConnector connector = new NioDatagramConnector();
        connector.setHandler(this);
        DatagramSessionConfig dcfg = (DatagramSessionConfig) connector.getSessionConfig();
        dcfg.setReuseAddress(true);
        dcfg.setReceiveBufferSize(64*1024);
        // Default buffer size is 2048, which is too small for our loop messages
        dcfg.setReadBufferSize(10*1024);
        logger.debug("Set receive buffer size to {}", dcfg.getReceiveBufferSize());

        logger.info("Connecting to {} ({}:{}) and listening on port {}", this.provider.name, this.provider.host, this.provider.port, this.port);
        session = connector.connect(new InetSocketAddress(this.provider.host, this.provider.port), new InetSocketAddress(this.port))
                .awaitUninterruptibly()
                .getSession();
    }

    /***
     * Fetches network topology from database.
     */
    private void getNetworkInfo() {
        mixList = Core.groupLayeredTopology(database.selectAllMixNodes());
        providerList = database.selectAllProviders();
        clientList = database.selectAllUsers();

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
            logger.debug("Sending SUBSCRIBE to provider");
            try {
                send(getSubscribeValue());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 0, config.getTIME_PULL(), TimeUnit.SECONDS);
    }

    private static final byte[] SUBSCRIBE = "SUBSCRIBE".getBytes(StandardCharsets.UTF_8);
    private Value cachedSubscribe;
    private Value getSubscribeValue() throws IOException {
        if (cachedSubscribe == null)
            cachedSubscribe = ValueFactory.newArray(
                    ValueFactory.newBinary(SUBSCRIBE),
                    ValueFactory.newBinary(this.name.getBytes(StandardCharsets.UTF_8)),
                    ValueFactory.newBinary(this.host.getBytes(StandardCharsets.UTF_8)),
                    ValueFactory.newInteger(this.port),
                    ValueFactory.newExtension((byte) 2, Packer.ecPointToByteArray(this.publicKey))
            );
        return cachedSubscribe;
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
        scheduler.schedule(this::retrieveMessages, config.getTIME_PULL(), TimeUnit.SECONDS);
        logger.debug("Retrieving messages.");
        send(getPullValue());
    }

    private static final byte[] PULL = "PULL".getBytes(StandardCharsets.UTF_8);
    private Value cachedPull;
    private Value getPullValue() {
        if (cachedPull == null)
            cachedPull = ValueFactory.newArray(
                    ValueFactory.newBinary(PULL),
                    ValueFactory.newBinary(this.name.getBytes(StandardCharsets.UTF_8))
            );
        return cachedPull;
    }

    /***
     * Creates a stream of drop packets.
     */
    private void makeDropStream() {
        scheduler.schedule(this::makeDropStream, sampleTimeFromExponential(config.getEXP_PARAMS_DROP()), TimeUnit.NANOSECONDS);
        logger.debug("Sending drop packet.");
        try {
            sendDropMessage();
        } catch (CryptoException | IOException | SphinxException e) {
            e.printStackTrace();
        }
    }

    /***
     * Sends a DROP message to a random client.
     * @throws CryptoException
     * @throws IOException
     * @throws SphinxException
     */
    private void sendDropMessage() throws CryptoException, IOException, SphinxException {
        User randomReceiver = this.clientList.get(random.nextInt(this.clientList.size()));
        List<LoopixNode> path = constructFullPath(randomReceiver);
        logger.debug("Chain selected: {}", path);
        SphinxPacket dropMessage = cryptoClient.createDropMessage(randomReceiver, path);
        send(dropMessage.toValue());
    }

    /***
     * Creates a stream of loop packets.
     */
    private void makeLoopStream() {
        scheduler.schedule(this::makeLoopStream, sampleTimeFromExponential(config.getEXP_PARAMS_LOOPS()), TimeUnit.NANOSECONDS);
        logger.debug("Sending loop packet.");
        try {
            sendLoopMessage();
        } catch (CryptoException | IOException | SphinxException e) {
            e.printStackTrace();
        }
    }

    /***
     * Sends a loop message back to ourselves.
     * @throws CryptoException
     * @throws IOException
     * @throws SphinxException
     */
    private void sendLoopMessage() throws CryptoException, IOException, SphinxException {
        List<LoopixNode> path = constructFullPath(this);
        logger.debug("Chain selected: {}", path);
        SphinxPacket loopMessage = cryptoClient.createLoopMessage(path);
        send(loopMessage.toValue());
    }

    /***
     * Creates a stream of real packets if there are messages to send, or sends drop packets instead, back to ourselves
     */
    private void makeRealStream() {
        scheduler.schedule(this::makeRealStream, sampleTimeFromExponential(config.getEXP_PARAMS_PAYLOAD()), TimeUnit.NANOSECONDS);
        try {
            if (messageBuilder != null && !messageBuilder.isEmpty()) {
                logger.debug("Sending real packet from builder.");
                sendRealMessage(messageBuilder.getMessage());
            }
            else if (!messageQueue.isEmpty()) {
                logger.debug("Sending real packet.");
                sendRealMessage(messageQueue.remove());
            } else if (config.getDATA_DIR().equals("debug")) {
                logger.debug("Sending debug real packet.");
                addMessage(this.name, "Hi".getBytes());
                sendRealMessage(messageQueue.remove());
            } else {
                logger.debug("Sending substituting drop packet.");
                sendDropMessage();
            }
        } catch (CryptoException | IOException | SphinxException e) {
            e.printStackTrace();
        }
    }

    /***
     * Sends a real message back to ourselves.
     * @throws CryptoException
     * @throws IOException
     * @throws SphinxException
     */
    private void sendRealMessage(ClientMessage message) throws CryptoException, IOException, SphinxException {
        List<LoopixNode> path = constructFullPath(message.getRecipient());
        logger.debug("Chain selected: {}", path);
        SphinxPacket realMessage = cryptoClient.createRealMessage(message.getRecipient(), path, message.getData());
        send(realMessage.toValue());
    }

    /***
     * Sends a msgpack message to the provider.
     * @param value msgpack encoded message
     */
    private void send(Value value) {
        try {
            send(packValue(value));
        } catch (IOException e) {
            logger.error("Failed to pack value", e);
        }
    }

    /***
     * Sends a byte array to the provider.
     * @param encodedPacket byte array
     */
    private void send(byte[] encodedPacket) {
        logger.debug("Sending packet");
        if (!session.isConnected())
            logger.warn("Trying to send when session is not connected.");
        IoBuffer buffer = IoBuffer.allocate(encodedPacket.length);
        buffer.put(encodedPacket);
        buffer.flip();
        logger.debug("Writing {} bytes to socket", encodedPacket.length);
        session.write(buffer);
        logger.debug("Written to socket.");
    }

    private byte[] packValue(Value value) throws IOException {
        return ((Packer) Packer.getPacker().packValue(value)).toByteArray();
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
        for (List<MixNode> layer : mixList) {
            MixNode mixNode = layer.get(random.nextInt(layer.size()));
            mixChain.add(mixNode);
        }
        return mixChain;
    }

    /***
     * Samples nanoseconds from an exponential distribution with rate parameter, lambda = 1/scale
     * @param scale Scale parameter
     * @return Number of nanoseconds
     */
    private long sampleTimeFromExponential(double scale) {
        // sample in seconds
        double seconds = Core.randomExponential(scale);
        double nanoseconds = seconds * 1e9;
        return (long) nanoseconds;
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        logger.debug("Receive session: readbufsize: {}, minread: {}", session.getConfig().getReadBufferSize(), session.getConfig().getMinReadBufferSize());
        // message is a HeapBuffer
        IoBuffer buffer = (IoBuffer) message;
        Unpacker unpacker = Unpacker.getUnpacker(buffer.array());
        ArrayValue values = unpacker.unpackValue().asArrayValue();
        if (values.get(0).isArrayValue()) {
            SphinxHeader header = SphinxHeader.fromValue(values.get(0).asArrayValue());
            byte[] body = values.get(1).asRawValue().asByteArray();
            byte[] decryptedBody = cryptoClient.processPacket(new SphinxPacket(header, body), privateKey);
            if (decryptedBody[0] == 'H' && decryptedBody[1] == 'T' && decryptedBody.length == 2+config.getNOISE_LENGTH()) {
                logger.info("Received loop message");
                return;
            } else if (decryptedBody[0] == 'H' && decryptedBody[1] == 'D' && decryptedBody.length == 2+config.getNOISE_LENGTH()) {
                logger.info("Received dummy message");
                return;
            }
            // Assume we are sending/receiving text messages for now
            logger.info("Received: {}", new String(decryptedBody, StandardCharsets.UTF_8));
            if (this.messageListener != null) {
                messageListener.onMessageReceived(this, decryptedBody);
            }
        } else {
            logger.warn("Received unknown message");
        }
    }

    public void setMessageListener(LoopixMessageListener listener) {
        this.messageListener = listener;
    }

    public void setMessageBuilder(LoopixMessageBuilder builder) {
        this.messageBuilder = builder;
    }

    /***
     * Adds a message to be sent to a client.
     * @param clientName Name of client
     * @param data Data to send
     */
    public void addMessage(String clientName, byte[] data) {
        if (messageQueue.size() > 100)
            return;
        User recipient = database.getUserFromName(clientName);
        if (recipient == null) {
            // TODO: Signal this to user via exception, for now just log and ignore
            logger.warn("Could not find user {}", clientName);
            return;
        }
        messageQueue.add(new ClientMessage(recipient, data));
    }

    /***
     * Sends test real messages looped back every 1 seconds
     */
    private void sendTestRealMessages() {
        for (int i = 0; i < 50; i++)
            addMessage(this.name, String.format("Hi from time: %d", new Date().getTime()).getBytes(StandardCharsets.UTF_8));
        scheduler.schedule(this::sendTestRealMessages, 1, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: jloopix <config.json> <public.bin> <private.bin>");
            return;
        }
        LoopixClient client = LoopixClient.fromFile(args[0], args[1], args[2]);
        client.run();
        client.sendTestRealMessages();
    }
}
