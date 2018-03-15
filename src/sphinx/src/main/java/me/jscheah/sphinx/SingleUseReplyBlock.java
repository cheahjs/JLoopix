package me.jscheah.sphinx;

public class SingleUseReplyBlock {
    public SingleUseReplyBlock(byte[] node0, SphinxHeader header, byte[] ktilde) {
        this.node0 = node0;
        this.header = header;
        this.ktilde = ktilde;
    }

    public byte[] node0;
    public SphinxHeader header;
    public byte[] ktilde;
}
