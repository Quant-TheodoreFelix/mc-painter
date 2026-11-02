package space.qu4nt.thirdparty.mcpainter.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkData;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkSection;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.HeightmapCodec;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.Paletted;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongTag;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;

public final class ChunkRebuilder {

    private static final List<String> LIGHT_KEYS = List.of("BlockLight", "SkyLight");
    private static final List<String> PROTO_ONLY_KEYS =
            List.of("blending_data", "below_zero_retrogen", "CarvingMasks", "entities", "Lights");

    @FunctionalInterface
    public interface BlockSource {
        BlockStateEntry blockAt(int worldX, int y, int worldZ);
    }

    @FunctionalInterface
    public interface BiomeSource {
        ResourceLocation biomeAt(int worldX, int y, int worldZ);
    }

    private ChunkRebuilder() {
    }

    public static RootTag rebuild(ChunkData base, int chunkX, int chunkZ, BlockSource blocks,
            Function<BlockStateEntry, BlockTraits> traits) {
        return rebuild(base, chunkX, chunkZ, blocks, traits, null);
    }

    public static RootTag rebuild(ChunkData base, int chunkX, int chunkZ, BlockSource blocks,
            Function<BlockStateEntry, BlockTraits> traits, BiomeSource biomeSource) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(traits, "traits");

        List<ChunkSection> sections = new ArrayList<>(base.sections().size());
        int minSectionY = Integer.MAX_VALUE;
        int maxSectionY = Integer.MIN_VALUE;
        for (ChunkSection section : base.sections()) {
            CompoundTag source = withoutKeys(section.source(), LIGHT_KEYS);
            Optional<Paletted<ResourceLocation>> biomes = section.biomes();
            if (biomeSource != null && biomes.isPresent()) {
                biomes = Optional.of(fillBiomes(chunkX, section.y(), chunkZ, biomeSource));
            }
            if (section.blocks().isEmpty()) {
                sections.add(new ChunkSection(section.y(), Optional.empty(), biomes, source));
                continue;
            }
            minSectionY = Math.min(minSectionY, section.y());
            maxSectionY = Math.max(maxSectionY, section.y());
            Paletted<BlockStateEntry> filled = fillSection(chunkX, section.y(), chunkZ, blocks);
            sections.add(new ChunkSection(section.y(), Optional.of(filled), biomes, source));
        }
        if (minSectionY == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("base chunk has no block sections");
        }
        int minY = minSectionY * 16;
        int worldHeight = (maxSectionY - minSectionY + 1) * 16;
        CompoundTag heightmaps = heightmaps(chunkX, chunkZ, minY, worldHeight, blocks, traits);

        RootTag rebuilt = base.withSections(sections).toNbt();
        CompoundTag.Builder root = rebuilt.tag().toBuilder()
                .put("xPos", new IntTag(chunkX))
                .put("zPos", new IntTag(chunkZ))
                .put("isLightOn", new ByteTag((byte) 0))
                .put("Heightmaps", heightmaps)
                .put("block_entities", ListTag.empty())
                .put("block_ticks", ListTag.empty())
                .put("fluid_ticks", ListTag.empty())
                .put("structures", CompoundTag.builder()
                        .put("References", CompoundTag.empty())
                        .put("starts", CompoundTag.empty())
                        .build())
                .put("InhabitedTime", new LongTag(0));
        rebuilt.tag().get("PostProcessing", ListTag.class)
                .ifPresent(post -> root.put("PostProcessing", emptyLists(post.size())));
        for (String key : PROTO_ONLY_KEYS) {
            root.remove(key);
        }
        return new RootTag(rebuilt.name(), root.build());
    }

    private static Paletted<BlockStateEntry> fillSection(int chunkX, int sectionY, int chunkZ, BlockSource blocks) {
        List<BlockStateEntry> palette = new ArrayList<>();
        Map<BlockStateEntry, Integer> lookup = new HashMap<>();
        int[] indices = new int[ChunkSection.BLOCKS];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockStateEntry block = Objects.requireNonNull(
                            blocks.blockAt(chunkX * 16 + x, sectionY * 16 + y, chunkZ * 16 + z), "block");
                    indices[ChunkSection.blockIndex(x, y, z)] = intern(block, palette, lookup);
                }
            }
        }
        return Paletted.of(palette, indices);
    }

    private static Paletted<ResourceLocation> fillBiomes(int chunkX, int sectionY, int chunkZ, BiomeSource source) {
        List<ResourceLocation> palette = new ArrayList<>();
        Map<ResourceLocation, Integer> lookup = new HashMap<>();
        int[] indices = new int[ChunkSection.BIOMES];
        for (int cy = 0; cy < 4; cy++) {
            for (int cz = 0; cz < 4; cz++) {
                for (int cx = 0; cx < 4; cx++) {
                    ResourceLocation biome = Objects.requireNonNull(source.biomeAt(chunkX * 16 + cx * 4 + 2,
                            sectionY * 16 + cy * 4 + 2, chunkZ * 16 + cz * 4 + 2), "biome");
                    indices[ChunkSection.biomeIndex(cx, cy, cz)] = intern(biome, palette, lookup);
                }
            }
        }
        return Paletted.of(palette, indices);
    }

    private static <T> int intern(T value, List<T> palette, Map<T, Integer> lookup) {
        Integer index = lookup.get(value);
        if (index == null) {
            index = palette.size();
            palette.add(value);
            lookup.put(value, index);
        }
        return index;
    }

    private static CompoundTag heightmaps(int chunkX, int chunkZ, int minY, int worldHeight, BlockSource blocks,
            Function<BlockStateEntry, BlockTraits> traits) {
        List<String> types = HeightmapCodec.FULL_CHUNK_TYPES;
        int[][] heights = new int[types.size()][HeightmapCodec.COLUMNS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int column = HeightmapCodec.columnIndex(x, z);
                boolean[] found = new boolean[types.size()];
                int remaining = types.size();
                for (int y = minY + worldHeight - 1; y >= minY && remaining > 0; y--) {
                    BlockTraits block = Objects.requireNonNull(
                            traits.apply(blocks.blockAt(chunkX * 16 + x, y, chunkZ * 16 + z)), "traits");
                    for (int t = 0; t < types.size(); t++) {
                        if (!found[t] && block.countsFor(types.get(t))) {
                            found[t] = true;
                            heights[t][column] = y + 1 - minY;
                            remaining--;
                        }
                    }
                }
            }
        }
        CompoundTag.Builder builder = CompoundTag.builder();
        for (int t = 0; t < types.size(); t++) {
            builder.put(types.get(t), LongArrayTag.of(HeightmapCodec.encode(heights[t], worldHeight)));
        }
        return builder.build();
    }

    private static ListTag emptyLists(int count) {
        List<Tag> lists = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lists.add(ListTag.empty());
        }
        return count == 0 ? ListTag.empty() : ListTag.of(TagType.LIST, lists);
    }

    private static CompoundTag withoutKeys(CompoundTag tag, List<String> keys) {
        CompoundTag.Builder builder = tag.toBuilder();
        for (String key : keys) {
            builder.remove(key);
        }
        return builder.build();
    }
}
