package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;
import java.util.Map;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;
import space.qu4nt.thirdparty.mcpainter.anvil.world.UnsupportedVersionException;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtWriter;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;

class ChunkDataTest {

    private static final int LEGACY = 3953;
    private static final int ID_KEYS = DataVersions.PALETTE_ID_KEYS;

    private static CompoundTag legacyEntry(String name, String... properties) {
        CompoundTag.Builder builder = CompoundTag.builder();
        if (properties.length > 0) {
            CompoundTag.Builder props = CompoundTag.builder();
            for (int i = 0; i < properties.length; i += 2) {
                props.put(properties[i], new StringTag(properties[i + 1]));
            }
            builder.put("Properties", props.build());
        }
        return builder.put("Name", new StringTag(name)).build();
    }

    private static CompoundTag container(ListTag palette, long[] data) {
        CompoundTag.Builder builder = CompoundTag.builder();
        if (data != null) {
            builder.put("data", LongArrayTag.of(data));
        }
        return builder.put("palette", palette).build();
    }

    private static RootTag chunk(int dataVersion, CompoundTag... sections) {
        return RootTag.of(CompoundTag.builder()
                .put("DataVersion", new IntTag(dataVersion))
                .put("xPos", new IntTag(3))
                .put("zPos", new IntTag(-7))
                .put("yPos", new IntTag(-4))
                .put("Status", new StringTag("minecraft:full"))
                .put("sections", ListTag.of(TagType.COMPOUND, List.of(sections)))
                .build());
    }

    private static CompoundTag legacySection(int y) {
        int[] indices = new int[ChunkSection.BLOCKS];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i < 256 ? 1 : 0;
        }
        CompoundTag blocks = container(
                ListTag.of(TagType.COMPOUND, legacyEntry("minecraft:air"), legacyEntry("minecraft:oak_log", "axis", "x")),
                PackedIndices.pack(indices, 4));
        CompoundTag biomes = container(ListTag.of(TagType.STRING, new StringTag("minecraft:plains")), null);
        return CompoundTag.builder()
                .put("Y", new ByteTag((byte) y))
                .put("block_states", blocks)
                .put("biomes", biomes)
                .put("SkyLight", ByteArrayTag.of(new byte[2048]))
                .build();
    }

    @Test
    void packedIndicesRoundTripForEveryWidth() throws ChunkFormatException {
        Random random = new Random(7);
        for (int bits = 1; bits <= 12; bits++) {
            int paletteSize = 1 << bits;
            int[] indices = new int[ChunkSection.BLOCKS];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = random.nextInt(paletteSize);
            }
            long[] packed = PackedIndices.pack(indices, bits);
            assertEquals(PackedIndices.longCount(indices.length, bits), packed.length);
            assertEquals(List.of(), diff(indices, PackedIndices.unpack(packed, indices.length, bits, paletteSize)));
        }
        assertEquals(342, PackedIndices.longCount(4096, 5));
        assertEquals(4, PackedIndices.longCount(64, 3));
    }

    private static List<Integer> diff(int[] a, int[] b) {
        return java.util.stream.IntStream.range(0, a.length).filter(i -> a[i] != b[i]).boxed().toList();
    }

    @Test
    void indicesDoNotSpanLongs() {
        int[] indices = new int[13];
        indices[12] = 31;
        long[] packed = PackedIndices.pack(indices, 5);
        assertEquals(0L, packed[0]);
        assertEquals(31L, packed[1]);
    }

    @Test
    void packedDataIsValidated() {
        assertThrows(ChunkFormatException.class, () -> PackedIndices.unpack(new long[10], 4096, 4, 16));
        long[] packed = PackedIndices.pack(new int[] {5}, 4);
        assertThrows(ChunkFormatException.class, () -> PackedIndices.unpack(packed, 1, 4, 5));
        assertEquals(0, PackedIndices.bitsFor(1, 4));
        assertEquals(4, PackedIndices.bitsFor(2, 4));
        assertEquals(5, PackedIndices.bitsFor(17, 4));
        assertEquals(1, PackedIndices.bitsFor(2, 0));
        assertEquals(6, PackedIndices.bitsFor(64, 0));
    }

    @Test
    void parsesLegacyChunkAndRoundTrips() throws Exception {
        CompoundTag lightOnly = CompoundTag.builder()
                .put("Y", new ByteTag((byte) -5))
                .put("SkyLight", ByteArrayTag.of(new byte[2048]))
                .build();
        RootTag root = chunk(LEGACY, lightOnly, legacySection(-4));
        ChunkData data = ChunkData.parse(root);

        assertEquals(3, data.x());
        assertEquals(-7, data.z());
        assertEquals("minecraft:full", data.status());
        ChunkSection section = data.sections().get(1);
        BlockStateEntry log = section.blocks().orElseThrow().get(ChunkSection.blockIndex(5, 0, 5));
        assertEquals(ResourceLocation.minecraft("oak_log"), log.id());
        assertEquals("x", log.properties().orElseThrow().get("axis"));
        assertEquals(ResourceLocation.minecraft("air"),
                section.blocks().orElseThrow().get(ChunkSection.blockIndex(0, 1, 0)).id());
        assertEquals(ResourceLocation.minecraft("plains"), section.biomes().orElseThrow().get(0));
        assertTrue(data.sections().get(0).blocks().isEmpty());

        assertEquals(root, data.toNbt());
    }

    @Test
    void editingGrowsPaletteAndBitWidth() throws Exception {
        ChunkData data = ChunkData.parse(chunk(LEGACY, legacySection(0)));
        ChunkSection section = data.sections().getFirst();
        Paletted<BlockStateEntry> blocks = section.blocks().orElseThrow();
        for (int i = 0; i < 15; i++) {
            blocks = blocks.with(i + 512, BlockStateEntry.withoutProperties(ResourceLocation.minecraft("block_" + i)));
        }
        ChunkData edited = data.withSections(List.of(section.withBlocks(blocks)));
        ChunkData reparsed = ChunkData.parse(edited.toNbt());
        Paletted<BlockStateEntry> result = reparsed.sections().getFirst().blocks().orElseThrow();
        assertEquals(17, result.palette().size());
        assertEquals(ResourceLocation.minecraft("block_14"), result.get(526).id());
        CompoundTag encoded = edited.toNbt().tag().get("sections", ListTag.class).orElseThrow()
                .get(0, CompoundTag.class).get("block_states", CompoundTag.class).orElseThrow();
        assertEquals(PackedIndices.longCount(4096, 5),
                encoded.get("data", LongArrayTag.class).orElseThrow().length());
    }

    @Test
    void unusedPaletteEntriesAreDroppedOnWrite() throws Exception {
        CompoundTag section = CompoundTag.builder()
                .put("Y", new ByteTag((byte) 0))
                .put("block_states", container(ListTag.of(TagType.COMPOUND,
                        legacyEntry("minecraft:stone"), legacyEntry("minecraft:dirt")), new long[256]))
                .build();
        ChunkData data = ChunkData.parse(chunk(LEGACY, section));
        CompoundTag encoded = data.toNbt().tag().get("sections", ListTag.class).orElseThrow()
                .get(0, CompoundTag.class).get("block_states", CompoundTag.class).orElseThrow();
        assertEquals(1, encoded.get("palette", ListTag.class).orElseThrow().size());
        assertFalse(encoded.contains("data"));
    }

    @Test
    void parsesIdKeyedPalettes() throws Exception {
        CompoundTag strings = CompoundTag.builder()
                .put("Y", new ByteTag((byte) 0))
                .put("block_states", container(ListTag.of(TagType.STRING,
                        new StringTag("minecraft:stone"), new StringTag("minecraft:dirt")), new long[256]))
                .build();
        CompoundTag mixed = CompoundTag.builder()
                .put("Y", new ByteTag((byte) 1))
                .put("block_states", container(ListTag.of(TagType.COMPOUND,
                        CompoundTag.builder().put("", new StringTag("minecraft:stone")).build(),
                        CompoundTag.builder().put("id", new StringTag("minecraft:grass_block")).build(),
                        CompoundTag.builder().put("id", new StringTag("minecraft:oak_log"))
                                .put("properties", CompoundTag.builder().put("axis", new StringTag("z")).build())
                                .build()),
                        PackedIndices.pack(new int[4096], 4)))
                .build();
        ChunkData data = ChunkData.parse(chunk(ID_KEYS, strings, mixed));

        List<BlockStateEntry> first = data.sections().get(0).blocks().orElseThrow().palette();
        assertTrue(first.get(0).isDefaultElided());
        List<BlockStateEntry> second = data.sections().get(1).blocks().orElseThrow().palette();
        assertTrue(second.get(0).isDefaultElided());
        assertTrue(second.get(1).isDefaultElided());
        assertEquals("z", second.get(2).properties().orElseThrow().get("axis"));
    }

    @Test
    void writesIdKeyedPalettesInCompactForm() {
        Paletted<BlockStateEntry> defaults = Paletted.of(List.of(
                BlockStateEntry.defaultState(ResourceLocation.minecraft("stone")),
                BlockStateEntry.withoutProperties(ResourceLocation.minecraft("dirt"))), alternating());
        ListTag stringForm = PaletteCodec.writeBlocks(defaults, ID_KEYS).get("palette", ListTag.class).orElseThrow();
        assertEquals(TagType.STRING, stringForm.elementType());

        Map<String, String> axis = Map.of("axis", "x");
        Paletted<BlockStateEntry> mixed = Paletted.of(List.of(
                BlockStateEntry.defaultState(ResourceLocation.minecraft("stone")),
                BlockStateEntry.of(ResourceLocation.minecraft("oak_log"), axis)), alternating());
        ListTag compoundForm = PaletteCodec.writeBlocks(mixed, ID_KEYS).get("palette", ListTag.class).orElseThrow();
        assertEquals(TagType.COMPOUND, compoundForm.elementType());
        CompoundTag stone = compoundForm.get(0, CompoundTag.class);
        assertEquals(List.of(""), List.copyOf(stone.keys()));
        CompoundTag log = compoundForm.get(1, CompoundTag.class);
        assertEquals(List.of("id", "properties"), List.copyOf(log.keys()));
    }

    private static int[] alternating() {
        int[] indices = new int[ChunkSection.BLOCKS];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i % 2;
        }
        return indices;
    }

    @Test
    void propertyOrderIsPreserved() throws Exception {
        CompoundTag section = CompoundTag.builder()
                .put("Y", new ByteTag((byte) 0))
                .put("block_states", container(ListTag.of(TagType.COMPOUND,
                        legacyEntry("minecraft:leaf_litter", "segment_amount", "2", "facing", "south")), null))
                .build();
        RootTag root = chunk(LEGACY, section);
        ChunkData data = ChunkData.parse(root);
        assertEquals(List.of("segment_amount", "facing"), List.copyOf(
                data.sections().getFirst().blocks().orElseThrow().get(0).properties().orElseThrow().keySet()));
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        NbtWriter.write(expected, root);
        NbtWriter.write(actual, data.toNbt());
        assertArrayEquals(expected.toByteArray(), actual.toByteArray());
    }

    @Test
    void legacyWriteRefusesElidedProperties() {
        Paletted<BlockStateEntry> elided = Paletted.filled(
                BlockStateEntry.defaultState(ResourceLocation.minecraft("oak_log")), ChunkSection.BLOCKS);
        assertThrows(IllegalStateException.class, () -> PaletteCodec.writeBlocks(elided, LEGACY));
    }

    @Test
    void rejectsMalformedChunks() {
        assertThrows(UnsupportedVersionException.class, () -> ChunkData.parse(chunk(DataVersions.MIN_SUPPORTED - 1)));
        assertThrows(ChunkFormatException.class, () -> ChunkData.parse(chunk(LEGACY, legacySection(0), legacySection(0))));

        CompoundTag badName = CompoundTag.builder()
                .put("Y", new ByteTag((byte) 0))
                .put("block_states", container(ListTag.of(TagType.COMPOUND, legacyEntry("Minecraft:../x")), null))
                .build();
        assertThrows(ChunkFormatException.class, () -> ChunkData.parse(chunk(LEGACY, badName)));

        CompoundTag wrongKind = CompoundTag.builder()
                .put("Y", new ByteTag((byte) 0))
                .put("block_states", new StringTag("nope"))
                .build();
        assertThrows(ChunkFormatException.class, () -> ChunkData.parse(chunk(LEGACY, wrongKind)));

        CompoundTag missingData = CompoundTag.builder()
                .put("Y", new ByteTag((byte) 0))
                .put("block_states", container(ListTag.of(TagType.COMPOUND,
                        legacyEntry("minecraft:stone"), legacyEntry("minecraft:dirt")), null))
                .build();
        assertThrows(ChunkFormatException.class, () -> ChunkData.parse(chunk(LEGACY, missingData)));

        CompoundTag legacyInNewFormat = CompoundTag.builder()
                .put("Y", new ByteTag((byte) 0))
                .put("block_states", container(ListTag.of(TagType.COMPOUND, legacyEntry("minecraft:stone")), null))
                .build();
        assertThrows(ChunkFormatException.class, () -> ChunkData.parse(chunk(ID_KEYS, legacyInNewFormat)));
    }
}
