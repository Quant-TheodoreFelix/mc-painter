package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum Layer {
    COAL_ORE("coalOre", Kind.ORE),
    IRON_ORE("ironOre", Kind.ORE),
    OAK_TREES("oakTrees", Kind.TREE),
    GRASS("grass", Kind.GRASS),
    DANDELION("dandelion", Kind.FLOWER),
    POPPY("poppy", Kind.FLOWER),
    BLUE_ORCHID("blueOrchid", Kind.FLOWER),
    ALLIUM("allium", Kind.FLOWER),
    AZURE_BLUET("azureBluet", Kind.FLOWER),
    RED_TULIP("redTulip", Kind.FLOWER),
    ORANGE_TULIP("orangeTulip", Kind.FLOWER),
    WHITE_TULIP("whiteTulip", Kind.FLOWER),
    PINK_TULIP("pinkTulip", Kind.FLOWER),
    OXEYE_DAISY("oxeyeDaisy", Kind.FLOWER),
    CORNFLOWER("cornflower", Kind.FLOWER),
    LILY_OF_THE_VALLEY("lilyOfTheValley", Kind.FLOWER),
    SUNFLOWER("sunflower", Kind.FLOWER),
    LILAC("lilac", Kind.FLOWER),
    ROSE_BUSH("roseBush", Kind.FLOWER),
    PEONY("peony", Kind.FLOWER),
    TORCHFLOWER("torchflower", Kind.FLOWER),
    PINK_PETALS("pinkPetals", Kind.FLOWER),
    PITCHER_PLANT("pitcherPlant", Kind.FLOWER),
    EYEBLOSSOM("eyeblossom", Kind.FLOWER),
    WILDFLOWERS("wildflowers", Kind.FLOWER);

    public enum Kind {
        ORE, TREE, GRASS, FLOWER
    }

    public static final int MAX_VALUE = 255;
    public static final int INITIAL_ORE_VALUE = 128;

    private static final Layer[] VALUES = values();
    private static final List<Layer> FLOWERS = Arrays.stream(VALUES).filter(Layer::flower).toList();

    private final String id;
    private final Kind kind;

    Layer(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public boolean ore() {
        return kind == Kind.ORE;
    }

    public boolean flower() {
        return kind == Kind.FLOWER;
    }

    public static List<Layer> flowers() {
        return FLOWERS;
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
