package me.jscheah.sphinx;

public class SphinxPacket {
    public SphinxHeader header;
    public byte[] body;

    public SphinxPacket(SphinxHeader header, byte[] body) {
        this.header = header;
        this.body = body;
    }
}
