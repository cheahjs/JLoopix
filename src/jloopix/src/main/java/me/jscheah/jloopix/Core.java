package me.jscheah.jloopix;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static java.util.stream.Collectors.groupingBy;

public class Core {
    private static SecureRandom random = new SecureRandom();
    public static byte[] generateRandomBytes(int length) {
        byte[] out = new byte[length];
        random.nextBytes(out);
        return out;
    }

    /***
     * Returns a list of groups of mixes based on ascending order of groupID
     * @param mixes List of mixes to use
     * @return List of groups of mixes
     */
    public static List<List<MixNode>> groupLayeredTopology(List<MixNode> mixes) {
        return new ArrayList<>(new TreeMap<>(mixes.stream().collect(groupingBy(x -> x.groupID))).values());
    }
}
