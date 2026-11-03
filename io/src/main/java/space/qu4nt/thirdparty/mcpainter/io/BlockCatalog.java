package space.qu4nt.thirdparty.mcpainter.io;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        POPPY(BlockTraits.PLANT, always("poppy")),
        BLUE_ORCHID(BlockTraits.PLANT, always("blue_orchid")),
        ALLIUM(BlockTraits.PLANT, always("allium")),
        AZURE_BLUET(BlockTraits.PLANT, always("azure_bluet")),
        RED_TULIP(BlockTraits.PLANT, always("red_tulip")),
        ORANGE_TULIP(BlockTraits.PLANT, always("orange_tulip")),
        WHITE_TULIP(BlockTraits.PLANT, always("white_tulip")),
        PINK_TULIP(BlockTraits.PLANT, always("pink_tulip")),
        OXEYE_DAISY(BlockTraits.PLANT, always("oxeye_daisy")),
        CORNFLOWER(BlockTraits.PLANT, always("cornflower")),
        LILY_OF_THE_VALLEY(BlockTraits.PLANT, always("lily_of_the_valley")),
        SUNFLOWER(BlockTraits.PLANT, always("sunflower", "half", "lower")),
        LILAC(BlockTraits.PLANT, always("lilac", "half", "lower")),
        ROSE_BUSH(BlockTraits.PLANT, always("rose_bush", "half", "lower")),
        PEONY(BlockTraits.PLANT, always("peony", "half", "lower")),
        TORCHFLOWER(BlockTraits.PLANT, since(DataVersions.V1_20, "torchflower")),
        PINK_PETALS(BlockTraits.PLANT,
                since(DataVersions.V1_20, "pink_petals", "facing", "north", "flower_amount", "1")),
        PITCHER_PLANT(BlockTraits.PLANT, since(DataVersions.V1_20, "pitcher_plant", "half", "lower")),
        CLOSED_EYEBLOSSOM(BlockTraits.PLANT, since(DataVersions.V1_21_4, "closed_eyeblossom")),
        WILDFLOWERS(BlockTraits.PLANT,
                since(DataVersions.V1_21_5, "wildflowers", "facing", "north", "flower_amount", "1"));

        private final BlockTraits traits;
        private final List<Form> forms;

        Block(BlockTraits traits, Form... forms) {
            this.traits = traits;
            this.forms = List.of(forms);
        }

        public BlockTraits traits() {
            return traits;
        }

        public int since() {
            int earliest = Integer.MAX_VALUE;
            for (Form form : forms) {
                earliest = Math.min(earliest, form.from());
            }
            return earliest;
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
            return since(DataVersions.MIN_SUPPORTED, path, properties);
        }

        private static Form since(int from, String path, String... properties) {
            return Form.of(from, DataVersions.MAX_SUPPORTED, path, properties);
        }
    }

    private record Variant(Block block, Map<String, String> properties) {
    }

    private final int dataVersion;
    private final Map<Block, Form> forms = new LinkedHashMap<>();
    private final Map<Block, BlockStateEntry> states = new LinkedHashMap<>();
    private final Map<Variant, BlockStateEntry> variants = new HashMap<>();
    private final Map<BlockStateEntry, BlockTraits> traits = new HashMap<>();
    private final Map<BlockStateEntry, Block> blocks = new HashMap<>();

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
                blocks.put(state, block);
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
        return state(block, Map.of(Objects.requireNonNull(property, "property"), Objects.requireNonNull(value, "value")));
    }

    public BlockStateEntry state(Block block, Map<String, String> overrides) {
        BlockStateEntry base = state(block);
        Form form = forms.get(block);
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (!form.properties().containsKey(entry.getKey())) {
                throw new IllegalArgumentException(block + " has no property " + entry.getKey());
            }
            Objects.requireNonNull(entry.getValue(), "value");
        }
        if (overrides.isEmpty()) {
            return base;
        }
        return variants.computeIfAbsent(new Variant(block, Map.copyOf(overrides)), key -> {
            Map<String, String> properties = new LinkedHashMap<>(form.properties());
            properties.putAll(key.properties());
            BlockStateEntry state = BlockStateEntry.of(base.id(), properties);
            traits.put(state, block.traits());
            blocks.put(state, block);
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

    public Optional<Block> block(BlockStateEntry state) {
        return Optional.ofNullable(blocks.get(Objects.requireNonNull(state, "state")));
    }

    public List<Block> available() {
        return List.copyOf(states.keySet());
    }
}
