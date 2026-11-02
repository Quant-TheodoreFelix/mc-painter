package space.qu4nt.thirdparty.mcpainter.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkData;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkSection;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.HeightmapCodec;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.Paletted;
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

    private ChunkRebuilder() {
    }

    public static RootTag rebuild(ChunkData base, int chunkX, int chunkZ, BlockSource blocks,
            Predicate<BlockStateEntry> isAir) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(isAir, "isAir");

        List<ChunkSection> sections = new ArrayList<>(base.sections().size());
        int minSectionY = Integer.MAX_VALUE;
        int maxSectionY = Integer.MIN_VALUE;
        for (ChunkSection section : base.sections()) {
            CompoundTag source = withoutKeys(section.source(), LIGHT_KEYS);
            if (section.blocks().isEmpty()) {
                sections.add(new ChunkSection(section.y(), Optional.empty(), section.biomes(), source));
                continue;
            }
            minSectionY = Math.min(minSectionY, section.y());
            maxSectionY = Math.max(maxSectionY, section.y());
            Paletted<BlockStateEntry> filled = fillSection(chunkX, section.y(), chunkZ, blocks);
            sections.add(new ChunkSection(section.y(), Optional.of(filled), section.biomes(), source));
        }
        if (minSectionY == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("base chunk has no block sections");
        }
        int minY = minSectionY * 16;
        int worldHeight = (maxSectionY - minSectionY + 1) * 16;
        int[] heights = surfaceHeights(chunkX, chunkZ, minY, worldHeight, blocks, isAir);

        RootTag rebuilt = base.withSections(sections).toNbt();
        CompoundTag.Builder root = rebuilt.tag().toBuilder()
                .put("xPos", new IntTag(chunkX))
                .put("zPos", new IntTag(chunkZ))
                .put("isLightOn", new ByteTag((byte) 0))
                .put("Heightmaps", heightmaps(heights, worldHeight))
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
                    Integer index = lookup.get(block);
                    if (index == null) {
                        index = palette.size();
                        palette.add(block);
                        lookup.put(block, index);
                    }
                    indices[ChunkSection.blockIndex(x, y, z)] = index;
                }
            }
        }
        return Paletted.of(palette, indices);
    }

    private static int[] surfaceHeights(int chunkX, int chunkZ, int minY, int worldHeight, BlockSource blocks,
            Predicate<BlockStateEntry> isAir) {
        int[] heights = new int[HeightmapCodec.COLUMNS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int height = 0;
                for (int y = minY + worldHeight - 1; y >= minY; y--) {
                    if (!isAir.test(blocks.blockAt(chunkX * 16 + x, y, chunkZ * 16 + z))) {
                        height = y + 1 - minY;
                        break;
                    }
                }
                heights[HeightmapCodec.columnIndex(x, z)] = height;
            }
        }
        return heights;
    }

    private static CompoundTag heightmaps(int[] heights, int worldHeight) {
        CompoundTag.Builder builder = CompoundTag.builder();
        for (String type : HeightmapCodec.FULL_CHUNK_TYPES) {
            builder.put(type, LongArrayTag.of(HeightmapCodec.encode(heights, worldHeight)));
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
