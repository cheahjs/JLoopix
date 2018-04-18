package me.jscheah.jloopix.client.latencymeasurement;

import me.jscheah.jloopix.client.ClientMessage;
import me.jscheah.jloopix.client.LoopixClient;
import me.jscheah.jloopix.client.LoopixMessageBuilder;
import me.jscheah.jloopix.client.LoopixMessageListener;
import me.jscheah.jloopix.nodes.User;
import org.bouncycastle.util.Arrays;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Scanner;

public class LatencyMeasurement implements LoopixMessageBuilder, LoopixMessageListener {
    private User self;
    private LoopixClient client;
    private FileWriter dataFile;

    private LatencyMeasurement(LoopixClient client) {
        this.client = client;
        client.setMessageListener(this);
        client.setMessageBuilder(this);

        try {
            dataFile = new FileWriter("latency.csv");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        client.run();
        self = client.getSelfUser();
    }

    @Override
    public void onMessageReceived(LoopixClient client, byte[] message) {
        long receivedTime = System.nanoTime();
        long sentTime = bytesToLong(message);
        long timeTaken = receivedTime - sentTime;
        try {
            dataFile.write(String.format("%d,%d,%d\n", receivedTime, sentTime, timeTaken));
            dataFile.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: jloopix_latency <config.json> <public.bin> <private.bin>");
            return;
        }
        LoopixClient client = LoopixClient.fromFile(args[0], args[1], args[2]);
        LatencyMeasurement latencyMeasurement  = new LatencyMeasurement(client);
        latencyMeasurement.run();
    }

    @Override
    public boolean isEmpty() {
        return self == null;
    }

    @Override
    public ClientMessage getMessage() {
        return new ClientMessage(self, longToBytes(System.nanoTime()));
    }

    private static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    private static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }
}
