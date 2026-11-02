package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

public final class PackedIndices {

    public static final int MAX_BITS = 32;

    private PackedIndices() {
    }

    public static int bitsFor(int paletteSize, int minBits) {
        if (paletteSize < 1) {
            throw new IllegalArgumentException("palette must not be empty");
        }
        if (paletteSize == 1) {
            return 0;
        }
        int bits = Integer.SIZE - Integer.numberOfLeadingZeros(paletteSize - 1);
        return Math.max(bits, minBits);
    }

    public static int longCount(int count, int bits) {
        checkBits(bits);
        int perLong = Long.SIZE / bits;
        return (count + perLong - 1) / perLong;
    }

    public static int[] unpack(long[] data, int count, int bits, int paletteSize) throws ChunkFormatException {
        checkBits(bits);
        int expected = longCount(count, bits);
        if (data.length != expected) {
            throw new ChunkFormatException("packed data has " + data.length + " longs, expected " + expected
                    + " for " + count + " indices at " + bits + " bits");
        }
        int perLong = Long.SIZE / bits;
        long mask = (1L << bits) - 1;
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            long word = data[i / perLong];
            int value = (int) ((word >>> ((i % perLong) * bits)) & mask);
            if (value >= paletteSize) {
                throw new ChunkFormatException("index " + value + " at " + i + " exceeds palette size " + paletteSize);
            }
            indices[i] = value;
        }
        return indices;
    }

    public static long[] pack(int[] indices, int bits) {
        checkBits(bits);
        int perLong = Long.SIZE / bits;
        long mask = (1L << bits) - 1;
        long[] data = new long[longCount(indices.length, bits)];
        for (int i = 0; i < indices.length; i++) {
            long value = indices[i] & 0xFFFFFFFFL;
            if ((value & ~mask) != 0) {
                throw new IllegalArgumentException("index " + indices[i] + " does not fit in " + bits + " bits");
            }
            data[i / perLong] |= value << ((i % perLong) * bits);
        }
        return data;
    }

    private static void checkBits(int bits) {
        if (bits < 1 || bits > MAX_BITS) {
            throw new IllegalArgumentException("bits must be in [1, " + MAX_BITS + "], got " + bits);
        }
    }
}
