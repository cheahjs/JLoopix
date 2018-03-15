package me.jscheah.sphinx;

public class HexUtils {
    private final static char[] hexArray = "0123456789abcdef".toCharArray();

    /**
     * Transform a byte array into a hex string
     * @param bytes byte array to transform
     * @return hex string representation of {@code bytes}
     */
    public static String hexlify(byte[] bytes) {
        char[] charArray = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            // Convert to unsigned int
            int v = bytes[i] & 0xFF;
            // Get upper 4 bits
            charArray[i * 2] = hexArray[v >>> 4];
            // Get lower 4 bits
            charArray[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(charArray);
    }
}
