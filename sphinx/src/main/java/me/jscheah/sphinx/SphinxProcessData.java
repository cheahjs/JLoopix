package me.jscheah.sphinx;

public class SphinxProcessData {
    public byte[] tag;
    public byte[] routing;
    public SphinxHeader header;
    public byte[] delta;

    public SphinxProcessData(byte[] tag, byte[] routing, SphinxHeader header, byte[] delta) {
        this.tag = tag;
        this.routing = routing;
        this.header = header;
        this.delta = delta;
    }
}
