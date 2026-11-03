package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Optional;

public enum Layer {
    COAL_ORE("coalOre", true),
    IRON_ORE("ironOre", true),
    OAK_TREES("oakTrees", false),
    GRASS("grass", false),
    FLOWERS("flowers", false);

    public static final int MAX_VALUE = 255;
    public static final int INITIAL_ORE_VALUE = 128;

    private static final Layer[] VALUES = values();

    private final String id;
    private final boolean ore;

    Layer(String id, boolean ore) {
        this.id = id;
        this.ore = ore;
    }

    public String id() {
        return id;
    }

    public boolean ore() {
        return ore;
    }

    public static Optional<Layer> fromId(String id) {
        for (Layer layer : VALUES) {
            if (layer.id.equals(id)) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }

    static int count() {
        return VALUES.length;
    }

    static Layer fromOrdinal(int ordinal) {
        return VALUES[ordinal];
    }
}
