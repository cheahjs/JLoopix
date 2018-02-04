package me.jscheah.jloopix.client.chatdemo;

import me.jscheah.jloopix.User;
import me.jscheah.jloopix.client.LoopixClient;
import me.jscheah.jloopix.client.LoopixMessageListener;
import org.bouncycastle.util.Arrays;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Scanner;

public class ChatClient implements LoopixMessageListener {
    private final static byte[] MAGIC_NUMBER = "CHAT".getBytes();
    private final LoopixClient client;

    private ChatClient(LoopixClient client) {
        this.client = client;
        client.setMessageListener(this);
    }

    public void run() {
        client.run();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            String message = String.format("%1$tH:%1$tM:%1$tS <%2$s> %3$s", new Date(), client.getName(), input);
            System.out.println(message);
            byte[] data = Arrays.concatenate(MAGIC_NUMBER, message.getBytes(Charset.forName("UTF-8")));
            for (User user : client.getBefriendedClients()) {
                if (user.name.equals(client.getName()))
                    continue;
                client.addMessage(user.name, data);
            }
        }
    }

    @Override
    public void onMessageReceived(LoopixClient client, byte[] message) {
        if (message[0] != 'C' || message[1] != 'H' || message[2] != 'A' || message[3] != 'T') {
            return;
        }
        // Strip "CHAT" prefix
        message = Arrays.copyOfRange(message, 4, message.length);
        String stringMsg = new String(message, Charset.forName("UTF-8"));
        System.out.println(stringMsg);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Usage: jloopix_demo <config.json> <public.bin> <private.bin>");
            return;
        }
        LoopixClient client = LoopixClient.fromFile(args[0], args[1], args[2]);
        client.getConfig().setTIME_PULL(1);
        ChatClient chatClient = new ChatClient(client);
        chatClient.run();
    }
}
