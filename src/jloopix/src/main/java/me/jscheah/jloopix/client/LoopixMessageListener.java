package me.jscheah.jloopix.client;

public interface LoopixMessageListener {
    void onMessageReceived(LoopixClient client, byte[] message);
}
