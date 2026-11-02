package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import java.util.List;

public final class HeightmapCodec {

    public static final int COLUMNS = 256;
    public static final List<String> FULL_CHUNK_TYPES =
            List.of("OCEAN_FLOOR", "MOTION_BLOCKING_NO_LEAVES", "MOTION_BLOCKING", "WORLD_SURFACE");

    private HeightmapCodec() {
    }

    public static int bitsFor(int worldHeight) {
        if (worldHeight < 1) {
            throw new IllegalArgumentException("world height must be positive");
        }
        return Integer.SIZE - Integer.numberOfLeadingZeros(worldHeight);
    }

    public static int columnIndex(int x, int z) {
        return ChunkSection.blockIndex(x, 0, z);
    }

    public static long[] encode(int[] heights, int worldHeight) {
        if (heights.length != COLUMNS) {
            throw new IllegalArgumentException("heightmap must have " + COLUMNS + " columns");
        }
        for (int height : heights) {
            if (height < 0 || height > worldHeight) {
                throw new IllegalArgumentException("height " + height + " outside [0, " + worldHeight + "]");
            }
        }
        return PackedIndices.pack(heights, bitsFor(worldHeight));
    }

    public static int[] decode(long[] data, int worldHeight) throws ChunkFormatException {
        return PackedIndices.unpack(data, COLUMNS, bitsFor(worldHeight), worldHeight + 1);
    }
}
