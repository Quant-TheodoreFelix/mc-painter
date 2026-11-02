package space.qu4nt.thirdparty.mcpainter.anvil.region;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record RegionPos(int x, int z) {

    public static final int CHUNKS_PER_SIDE = 32;

    private static final Pattern FILE_NAME = Pattern.compile("r\\.(-?[0-9]{1,8})\\.(-?[0-9]{1,8})\\.mca");

    public static RegionPos ofChunk(int chunkX, int chunkZ) {
        return new RegionPos(chunkX >> 5, chunkZ >> 5);
    }

    public static Optional<RegionPos> parseFileName(String fileName) {
        Matcher matcher = FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new RegionPos(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
    }

    public String fileName() {
        return "r." + x + "." + z + ".mca";
    }

    public int chunkX(int localX) {
        checkLocal(localX, "localX");
        return x * CHUNKS_PER_SIDE + localX;
    }

    public int chunkZ(int localZ) {
        checkLocal(localZ, "localZ");
        return z * CHUNKS_PER_SIDE + localZ;
    }

    static int index(int localX, int localZ) {
        checkLocal(localX, "localX");
        checkLocal(localZ, "localZ");
        return localX + localZ * CHUNKS_PER_SIDE;
    }

    static void checkLocal(int value, String name) {
        if (value < 0 || value >= CHUNKS_PER_SIDE) {
            throw new IndexOutOfBoundsException(name + " must be in [0, 32), got " + value);
        }
    }
}
