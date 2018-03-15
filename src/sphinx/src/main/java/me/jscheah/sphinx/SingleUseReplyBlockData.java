package me.jscheah.sphinx;

import java.util.List;

public class SingleUseReplyBlockData {
    public SingleUseReplyBlockData(byte[] surbId, List<byte[]> surbKeyTuple, SingleUseReplyBlock nymTuple) {
        this.surbId = surbId;
        this.surbKeyTuple = surbKeyTuple;
        this.nymTuple = nymTuple;
    }

    public byte[] surbId;
    public List<byte[]> surbKeyTuple;
    public SingleUseReplyBlock nymTuple;
}
