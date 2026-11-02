package space.qu4nt.thirdparty.mcpainter.nbt;

public record NbtLimits(int maxDepth, long maxBytes) {

    public static final int DEFAULT_MAX_DEPTH = 512;
    public static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;
    public static final NbtLimits DEFAULT = new NbtLimits(DEFAULT_MAX_DEPTH, DEFAULT_MAX_BYTES);

    public NbtLimits {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
    }
}
