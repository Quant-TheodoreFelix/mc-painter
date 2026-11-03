package space.qu4nt.thirdparty.mcpainter.io;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import space.qu4nt.thirdparty.mcpainter.io.BlockCatalog.Block;
import space.qu4nt.thirdparty.mcpainter.world.Layer;

public final class FlowerBlocks {

    private static final Map<Layer, Block> BLOCKS = new EnumMap<>(Map.ofEntries(
            Map.entry(Layer.DANDELION, Block.DANDELION),
            Map.entry(Layer.POPPY, Block.POPPY),
            Map.entry(Layer.BLUE_ORCHID, Block.BLUE_ORCHID),
            Map.entry(Layer.ALLIUM, Block.ALLIUM),
            Map.entry(Layer.AZURE_BLUET, Block.AZURE_BLUET),
            Map.entry(Layer.RED_TULIP, Block.RED_TULIP),
            Map.entry(Layer.ORANGE_TULIP, Block.ORANGE_TULIP),
            Map.entry(Layer.WHITE_TULIP, Block.WHITE_TULIP),
            Map.entry(Layer.PINK_TULIP, Block.PINK_TULIP),
            Map.entry(Layer.OXEYE_DAISY, Block.OXEYE_DAISY),
            Map.entry(Layer.CORNFLOWER, Block.CORNFLOWER),
            Map.entry(Layer.LILY_OF_THE_VALLEY, Block.LILY_OF_THE_VALLEY),
            Map.entry(Layer.SUNFLOWER, Block.SUNFLOWER),
            Map.entry(Layer.LILAC, Block.LILAC),
            Map.entry(Layer.ROSE_BUSH, Block.ROSE_BUSH),
            Map.entry(Layer.PEONY, Block.PEONY),
            Map.entry(Layer.TORCHFLOWER, Block.TORCHFLOWER),
            Map.entry(Layer.PINK_PETALS, Block.PINK_PETALS),
            Map.entry(Layer.PITCHER_PLANT, Block.PITCHER_PLANT),
            Map.entry(Layer.EYEBLOSSOM, Block.CLOSED_EYEBLOSSOM),
            Map.entry(Layer.WILDFLOWERS, Block.WILDFLOWERS)));

    private static final Set<Layer> TALL = EnumSet.of(Layer.SUNFLOWER, Layer.LILAC, Layer.ROSE_BUSH, Layer.PEONY,
            Layer.PITCHER_PLANT);
    private static final Set<Layer> BEDS = EnumSet.of(Layer.PINK_PETALS, Layer.WILDFLOWERS);

    private FlowerBlocks() {
    }

    public static Block block(Layer flower) {
        Block block = BLOCKS.get(Objects.requireNonNull(flower, "flower"));
        if (block == null) {
            throw new IllegalArgumentException(flower + " is not a flower layer");
        }
        return block;
    }

    public static boolean tall(Layer flower) {
        return TALL.contains(Objects.requireNonNull(flower, "flower"));
    }

    public static boolean bed(Layer flower) {
        return BEDS.contains(Objects.requireNonNull(flower, "flower"));
    }

    public static int since(Layer flower) {
        return block(flower).since();
    }

    public static List<Layer> unavailable(Collection<Layer> layers, int dataVersion) {
        BlockCatalog catalog = new BlockCatalog(dataVersion);
        return layers.stream().filter(Layer::flower).filter(layer -> !catalog.has(block(layer))).sorted().toList();
    }
}
