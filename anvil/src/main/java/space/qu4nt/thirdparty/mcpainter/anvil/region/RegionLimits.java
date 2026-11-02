package space.qu4nt.thirdparty.mcpainter.anvil.region;

import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;

public record RegionLimits(long maxExternalChunkBytes, NbtLimits nbt) {

    public static final long DEFAULT_MAX_EXTERNAL_CHUNK_BYTES = 64L * 1024 * 1024;
    public static final RegionLimits DEFAULT = new RegionLimits(DEFAULT_MAX_EXTERNAL_CHUNK_BYTES, NbtLimits.DEFAULT);

    public RegionLimits {
        if (maxExternalChunkBytes < 1 || maxExternalChunkBytes > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("maxExternalChunkBytes out of range");
        }
        if (nbt == null) {
            throw new NullPointerException("nbt");
        }
    }
}
