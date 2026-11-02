package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkData;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkSection;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.HeightmapCodec;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongTag;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ShortTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;

class ChunkRebuilderTest {

    private static ChunkData base(int dataVersion) throws Exception {
        List<Tag> sections = new ArrayList<>();
        for (int y = -4; y < 20; y++) {
            sections.add(CompoundTag.builder()
                    .put("block_states", CompoundTag.builder().put("palette", ListTag.of(TagType.COMPOUND,
                            CompoundTag.builder().put("Name", new StringTag("minecraft:air")).build())).build())
                    .put("biomes", CompoundTag.builder().put("palette", ListTag.of(TagType.STRING,
                            new StringTag("minecraft:plains"))).build())
                    .put("SkyLight", ByteArrayTag.of(new byte[2048]))
                    .put("Y", new ByteTag((byte) y))
                    .build());
        }
        List<Tag> post = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            post.add(ListTag.of(TagType.SHORT, new ShortTag((short) i)));
        }
        return ChunkData.parse(RootTag.of(CompoundTag.builder()
                .put("Status", new StringTag("minecraft:full"))
                .put("xPos", new IntTag(9))
                .put("zPos", new IntTag(9))
                .put("yPos", new IntTag(-4))
                .put("block_entities", ListTag.of(TagType.COMPOUND, CompoundTag.empty()))
                .put("InhabitedTime", new LongTag(500))
                .put("isLightOn", new ByteTag((byte) 1))
                .put("PostProcessing", ListTag.of(TagType.LIST, post))
                .put("blending_data", CompoundTag.empty())
                .put("DataVersion", new IntTag(dataVersion))
                .put("sections", ListTag.of(TagType.COMPOUND, sections))
                .build()));
    }

    @Test
    void rebuildsBlocksResetsStateAndComputesHeightmaps() throws Exception {
        RootTag rebuilt = ChunkRebuilder.rebuild(base(3953), 2, -1,
                (x, y, z) -> SmokeTest.blockAt(x, y, z, -64), block -> block.equals(SmokeTest.AIR));
        ChunkData data = ChunkData.parse(rebuilt);
        CompoundTag root = rebuilt.tag();

        assertEquals(2, data.x());
        assertEquals(-1, data.z());
        assertEquals(new ByteTag((byte) 0), root.get("isLightOn").orElseThrow());
        assertEquals(0, root.get("block_entities", ListTag.class).orElseThrow().size());
        assertEquals(new LongTag(0), root.get("InhabitedTime").orElseThrow());
        assertFalse(root.contains("blending_data"));
        ListTag post = root.get("PostProcessing", ListTag.class).orElseThrow();
        assertEquals(24, post.size());
        assertTrue(post.get(0, ListTag.class).isEmpty());

        ChunkSection bottom = data.sections().getFirst();
        assertEquals(SmokeTest.BEDROCK, bottom.blocks().orElseThrow().get(ChunkSection.blockIndex(0, 0, 0)));
        assertEquals(SmokeTest.STONE, bottom.blocks().orElseThrow().get(ChunkSection.blockIndex(0, 1, 0)));
        ChunkSection ground = data.sections().stream().filter(s -> s.y() == 3).findFirst().orElseThrow();
        assertEquals(SmokeTest.GRASS, ground.blocks().orElseThrow().get(ChunkSection.blockIndex(0, 12, 0)));
        assertFalse(ground.source().contains("SkyLight"));
        assertEquals("minecraft:plains", ground.biomes().orElseThrow().get(0).toString());

        int[] expected = new int[HeightmapCodec.COLUMNS];
        Arrays.fill(expected, SmokeTest.GROUND_Y + 1 + 64);
        CompoundTag heightmaps = root.get("Heightmaps", CompoundTag.class).orElseThrow();
        assertEquals(HeightmapCodec.FULL_CHUNK_TYPES, List.copyOf(heightmaps.keys()));
        for (String type : HeightmapCodec.FULL_CHUNK_TYPES) {
            long[] packed = heightmaps.get(type, LongArrayTag.class).orElseThrow().toArray();
            assertEquals(37, packed.length);
            assertArrayEquals(expected, HeightmapCodec.decode(packed, 384));
        }
    }

    @Test
    void heightmapCodecUsesNineBitsForOverworld() throws Exception {
        assertEquals(9, HeightmapCodec.bitsFor(384));
        assertEquals(9, HeightmapCodec.bitsFor(256));
        assertEquals(8, HeightmapCodec.bitsFor(255));
        int[] heights = new int[HeightmapCodec.COLUMNS];
        for (int i = 0; i < heights.length; i++) {
            heights[i] = i * 384 / 255;
        }
        assertArrayEquals(heights, HeightmapCodec.decode(HeightmapCodec.encode(heights, 384), 384));
    }
}
