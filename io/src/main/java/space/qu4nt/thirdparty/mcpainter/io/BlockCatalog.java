package space.qu4nt.thirdparty.mcpainter.io;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;

public final class BlockCatalog {

    public enum Block {
        AIR("air", BlockTraits.AIR),
        BEDROCK("bedrock", BlockTraits.SOLID),
        STONE("stone", BlockTraits.SOLID),
        DIRT("dirt", BlockTraits.SOLID),
        GRASS_BLOCK("grass_block", BlockTraits.SOLID, "snowy", "false"),
        SAND("sand", BlockTraits.SOLID),
        GRAVEL("gravel", BlockTraits.SOLID),
        SNOW_BLOCK("snow_block", BlockTraits.SOLID),
        WATER("water", BlockTraits.FLUID, "level", "0"),
        COAL_ORE("coal_ore", BlockTraits.SOLID),
        IRON_ORE("iron_ore", BlockTraits.SOLID);

        private final ResourceLocation id;
        private final BlockTraits traits;
        private final Map<String, String> defaultProperties;
        private final int sinceDataVersion;

        Block(String path, BlockTraits traits, String... properties) {
            this.id = ResourceLocation.minecraft(path);
            this.traits = traits;
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < properties.length; i += 2) {
                map.put(properties[i], properties[i + 1]);
            }
            this.defaultProperties = Map.copyOf(map);
            this.sinceDataVersion = DataVersions.MIN_SUPPORTED;
        }

        public ResourceLocation id() {
            return id;
        }

        public BlockTraits traits() {
            return traits;
        }
    }

    private final int dataVersion;
    private final Map<Block, BlockStateEntry> states = new LinkedHashMap<>();
    private final Map<BlockStateEntry, BlockTraits> traits = new LinkedHashMap<>();

    public BlockCatalog(int dataVersion) {
        if (!DataVersions.isSupported(dataVersion)) {
            throw new IllegalArgumentException("unsupported data version " + dataVersion);
        }
        this.dataVersion = dataVersion;
        for (Block block : Block.values()) {
            if (dataVersion >= block.sinceDataVersion) {
                BlockStateEntry state = BlockStateEntry.of(block.id, block.defaultProperties);
                states.put(block, state);
                traits.put(state, block.traits);
            }
        }
    }

    public int dataVersion() {
        return dataVersion;
    }

    public BlockStateEntry state(Block block) {
        BlockStateEntry state = states.get(Objects.requireNonNull(block, "block"));
        if (state == null) {
            throw new IllegalArgumentException(block + " does not exist at data version " + dataVersion);
        }
        return state;
    }

    public BlockTraits traits(BlockStateEntry state) {
        BlockTraits known = traits.get(state);
        if (known == null) {
            throw new IllegalArgumentException("block state not in catalog: " + state.id());
        }
        return known;
    }

    public List<Block> available() {
        return List.copyOf(states.keySet());
    }
}
