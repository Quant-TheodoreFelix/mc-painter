package space.qu4nt.thirdparty.mcpainter.io;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;

public final class BlockCatalog {

    record Form(int from, int to, String path, Map<String, String> properties) {

        static Form of(int from, int to, String path, String... properties) {
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < properties.length; i += 2) {
                map.put(properties[i], properties[i + 1]);
            }
            return new Form(from, to, path, map);
        }

        boolean covers(int dataVersion) {
            return dataVersion >= from && dataVersion <= to;
        }
    }

    public enum Block {
        AIR(BlockTraits.AIR, always("air")),
        BEDROCK(BlockTraits.SOLID, always("bedrock")),
        STONE(BlockTraits.SOLID, always("stone")),
        DEEPSLATE(BlockTraits.SOLID, always("deepslate", "axis", "y")),
        DIRT(BlockTraits.SOLID, always("dirt")),
        GRASS_BLOCK(BlockTraits.SOLID, always("grass_block", "snowy", "false")),
        SAND(BlockTraits.SOLID, always("sand")),
        GRAVEL(BlockTraits.SOLID, always("gravel")),
        SNOW_BLOCK(BlockTraits.SOLID, always("snow_block")),
        WATER(BlockTraits.FLUID, always("water", "level", "0")),
        COAL_ORE(BlockTraits.SOLID, always("coal_ore")),
        IRON_ORE(BlockTraits.SOLID, always("iron_ore")),
        DEEPSLATE_COAL_ORE(BlockTraits.SOLID, always("deepslate_coal_ore")),
        DEEPSLATE_IRON_ORE(BlockTraits.SOLID, always("deepslate_iron_ore")),
        OAK_LOG(BlockTraits.SOLID, always("oak_log", "axis", "y")),
        OAK_LEAVES(BlockTraits.LEAVES,
                Form.of(DataVersions.MIN_SUPPORTED, DataVersions.V1_18_2, "oak_leaves",
                        "distance", "1", "persistent", "false"),
                Form.of(DataVersions.V1_19, DataVersions.MAX_SUPPORTED, "oak_leaves",
                        "waterlogged", "false", "distance", "1", "persistent", "false")),
        SHORT_GRASS(BlockTraits.PLANT,
                Form.of(DataVersions.MIN_SUPPORTED, DataVersions.V1_20_2, "grass"),
                Form.of(DataVersions.V1_20_3, DataVersions.MAX_SUPPORTED, "short_grass")),
        TALL_GRASS(BlockTraits.PLANT, always("tall_grass", "half", "lower")),
        DANDELION(BlockTraits.PLANT, always("dandelion")),
        POPPY(BlockTraits.PLANT, always("poppy"));

        private final BlockTraits traits;
        private final List<Form> forms;

        Block(BlockTraits traits, Form... forms) {
            this.traits = traits;
            this.forms = List.of(forms);
        }

        public BlockTraits traits() {
            return traits;
        }

        Form form(int dataVersion) {
            for (Form form : forms) {
                if (form.covers(dataVersion)) {
                    return form;
                }
            }
            return null;
        }

        private static Form always(String path, String... properties) {
            return Form.of(DataVersions.MIN_SUPPORTED, DataVersions.MAX_SUPPORTED, path, properties);
        }
    }

    private record Variant(Block block, String property, String value) {
    }

    private final int dataVersion;
    private final Map<Block, Form> forms = new LinkedHashMap<>();
    private final Map<Block, BlockStateEntry> states = new LinkedHashMap<>();
    private final Map<Variant, BlockStateEntry> variants = new HashMap<>();
    private final Map<BlockStateEntry, BlockTraits> traits = new HashMap<>();

    public BlockCatalog(int dataVersion) {
        if (!DataVersions.isSupported(dataVersion)) {
            throw new IllegalArgumentException("unsupported data version " + dataVersion);
        }
        this.dataVersion = dataVersion;
        for (Block block : Block.values()) {
            Form form = block.form(dataVersion);
            if (form != null) {
                BlockStateEntry state = BlockStateEntry.of(ResourceLocation.minecraft(form.path()), form.properties());
                forms.put(block, form);
                states.put(block, state);
                traits.put(state, block.traits());
            }
        }
    }

    public int dataVersion() {
        return dataVersion;
    }

    public boolean has(Block block) {
        return states.containsKey(Objects.requireNonNull(block, "block"));
    }

    public BlockStateEntry state(Block block) {
        BlockStateEntry state = states.get(Objects.requireNonNull(block, "block"));
        if (state == null) {
            throw new IllegalArgumentException(block + " does not exist at data version " + dataVersion);
        }
        return state;
    }

    public BlockStateEntry state(Block block, String property, String value) {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(value, "value");
        BlockStateEntry base = state(block);
        Form form = forms.get(block);
        if (!form.properties().containsKey(property)) {
            throw new IllegalArgumentException(block + " has no property " + property);
        }
        return variants.computeIfAbsent(new Variant(block, property, value), key -> {
            Map<String, String> properties = new LinkedHashMap<>(form.properties());
            properties.put(property, value);
            BlockStateEntry state = BlockStateEntry.of(base.id(), properties);
            traits.put(state, block.traits());
            return state;
        });
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
