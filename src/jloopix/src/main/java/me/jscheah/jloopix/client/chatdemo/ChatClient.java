package me.jscheah.jloopix.client.chatdemo;

import me.jscheah.jloopix.nodes.User;
import me.jscheah.jloopix.client.LoopixClient;
import me.jscheah.jloopix.client.LoopixMessageListener;
import org.bouncycastle.util.Arrays;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    public void run() throws IOException {
        client.run();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String input = reader.readLine().trim();
            String message = String.format("%1$tH:%1$tM:%1$tS <%2$s> %3$s", new Date(), client.getName(), input);
            printMessage(message, true);
            byte[] data = Arrays.concatenate(MAGIC_NUMBER, message.getBytes(Charset.forName("UTF-8")));
            for (User user : client.getClientList()) {
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
        printMessage(stringMsg, false);
    }

    private void printMessage(String message, boolean input) {
        if (input) {
            // Move up one, and clear the input
            System.out.print("\033[1A\033[2K");
            System.out.println(message);
        } else {
            // Store cursor position, move to left, insert line
            System.out.print("\0337\r\033[1L");
            System.out.print(message);
            // Restore cursor position, move down
            System.out.print("\0338\033[1B");
        }
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
