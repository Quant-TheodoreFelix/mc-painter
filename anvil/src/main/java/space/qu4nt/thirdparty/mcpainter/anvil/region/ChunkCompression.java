package space.qu4nt.thirdparty.mcpainter.anvil.region;

import java.util.Optional;

public enum ChunkCompression {
    GZIP(1),
    ZLIB(2),
    NONE(3),
    LZ4(4),
    CUSTOM(127);

    public static final int EXTERNAL_FLAG = 0x80;

    private final int id;

    ChunkCompression(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static Optional<ChunkCompression> fromId(int id) {
        for (ChunkCompression compression : values()) {
            if (compression.id == id) {
                return Optional.of(compression);
            }
        }
        return Optional.empty();
    }
}
