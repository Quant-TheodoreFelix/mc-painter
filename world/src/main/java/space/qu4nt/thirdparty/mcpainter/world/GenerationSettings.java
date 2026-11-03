package space.qu4nt.thirdparty.mcpainter.world;

public record GenerationSettings(long seed, boolean initialOres) {

    public static final GenerationSettings DEFAULT = new GenerationSettings(0, false);

    public GenerationSettings(long seed) {
        this(seed, false);
    }

    public GenerationSettings withSeed(long value) {
        return new GenerationSettings(value, initialOres);
    }
}
