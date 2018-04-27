package me.jscheah.jloopix;

import me.jscheah.jloopix.nodes.MixNode;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static java.util.stream.Collectors.groupingBy;

public class Core {
    public static final byte[] MAGIC_LOOP = "HT".getBytes(StandardCharsets.UTF_8);
    private static SecureRandom random = new SecureRandom();

    public static byte[] generateRandomBytes(int length) {
        byte[] out = new byte[length];
        random.nextBytes(out);
        return out;
    }

    public static double randomExponential(double scale) {
        // sample = -ln(u)*scale
        return Math.log(random.nextDouble())*(-scale);
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
