package space.qu4nt.thirdparty.mcpainter.world;

public record GenerationSettings(long seed) {

    public static final GenerationSettings DEFAULT = new GenerationSettings(0);

    public GenerationSettings withSeed(long value) {
        return new GenerationSettings(value);
    }
}
