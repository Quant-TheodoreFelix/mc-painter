package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkData;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkSection;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.HeightmapCodec;
import space.qu4nt.thirdparty.mcpainter.anvil.region.ChunkCompression;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RawChunk;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionFile;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionWriter;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtCompression;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtIo;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.Tag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class WorldExporterTest {

    private static final int DATA_VERSION = 3953;

    @TempDir
    Path world;

    private void writeTemplate() throws IOException {
        CompoundTag data = CompoundTag.builder().put("DataVersion", new IntTag(DATA_VERSION)).build();
        try (OutputStream out = Files.newOutputStream(world.resolve("level.dat"))) {
            NbtIo.write(out, RootTag.of(CompoundTag.builder().put("Data", data).build()), NbtCompression.GZIP);
        }
        List<Tag> sections = new ArrayList<>();
        for (int y = -4; y < 20; y++) {
            sections.add(CompoundTag.builder()
                    .put("block_states", CompoundTag.builder().put("palette", ListTag.of(TagType.COMPOUND,
                            CompoundTag.builder().put("Name", new StringTag("minecraft:air")).build())).build())
                    .put("biomes", CompoundTag.builder().put("palette", ListTag.of(TagType.STRING,
                            new StringTag("minecraft:the_void"))).build())
                    .put("Y", new ByteTag((byte) y))
                    .build());
        }
        RootTag prototype = RootTag.of(CompoundTag.builder()
                .put("Status", new StringTag("minecraft:full"))
                .put("xPos", new IntTag(0))
                .put("zPos", new IntTag(0))
                .put("yPos", new IntTag(-4))
                .put("DataVersion", new IntTag(DATA_VERSION))
                .put("sections", ListTag.of(TagType.COMPOUND, sections))
                .build());
        Files.createDirectories(world.resolve("region"));
        new RegionWriter().put(0, 0, RawChunk.encode(prototype, ChunkCompression.ZLIB, 0))
                .writeTo(world.resolve("region/r.0.0.mca"));
    }

    private static WorldModel model() {
        WorldModel model = WorldModel.create(-64, 320, 63);
        try (Edit edit = model.beginEdit("setup")) {
            edit.addTile(new TilePos(0, 0), 70.4f, TerrainType.GRASS);
            edit.setHeight(20, 3, 50);
            edit.setTerrain(21, 3, TerrainType.SAND);
            edit.setBiome(42, 42, "minecraft:desert");
        }
        return model;
    }

    @Test
    void exportsColumnsBiomesAndHeightmaps() throws IOException {
        writeTemplate();
        WorldExporter.Result result = WorldExporter.export(model(), world);
        assertEquals(1, result.regionsWritten());
        assertEquals(1, result.chunksReplaced());
        assertEquals(63, result.chunksCreated());

        try (RegionFile region = RegionFile.open(world.resolve("region/r.0.0.mca"))) {
            ChunkData origin = ChunkData.parse(region.readChunk(1, 0).orElseThrow());
            assertEquals(1, origin.x());
            assertEquals("minecraft:grass_block", blockAt(origin, 22, 70, 3));
            assertEquals("minecraft:dirt", blockAt(origin, 20, 50, 3));
            assertEquals("minecraft:water", blockAt(origin, 20, 62, 3));
            assertEquals("minecraft:air", blockAt(origin, 20, 63, 3));
            assertEquals("minecraft:sand", blockAt(origin, 21, 70, 3));
            assertEquals("minecraft:sand", blockAt(origin, 21, 67, 3));
            assertEquals("minecraft:stone", blockAt(origin, 21, 66, 3));
            assertEquals("minecraft:bedrock", blockAt(origin, 21, -64, 3));
            assertEquals("minecraft:air", blockAt(origin, 22, 71, 3));

            CompoundTag heightmaps = region.readChunk(1, 0).orElseThrow().tag()
                    .get("Heightmaps", CompoundTag.class).orElseThrow();
            int column = HeightmapCodec.columnIndex(4, 3);
            assertEquals(50 + 1 + 64, decode(heightmaps, "OCEAN_FLOOR")[column]);
            assertEquals(62 + 1 + 64, decode(heightmaps, "MOTION_BLOCKING")[column]);
            assertEquals(62 + 1 + 64, decode(heightmaps, "WORLD_SURFACE")[column]);
            assertEquals(70 + 1 + 64, decode(heightmaps, "WORLD_SURFACE")[HeightmapCodec.columnIndex(5, 3)]);

            ChunkData desert = ChunkData.parse(region.readChunk(2, 2).orElseThrow());
            ChunkSection ground = desert.sections().stream().filter(s -> s.y() == 4).findFirst().orElseThrow();
            assertEquals("minecraft:desert", ground.biomes().orElseThrow().get(ChunkSection.biomeIndex(2, 0, 2)).toString());
            assertEquals("minecraft:plains", ground.biomes().orElseThrow().get(ChunkSection.biomeIndex(0, 0, 0)).toString());
        }
    }

    @Test
    void refusesTemplatesWithoutPrototypeOrMismatchedHeight() throws IOException {
        writeTemplate();
        WorldModel tooTall = WorldModel.create(-64, 2032, 63);
        assertThrows(IOException.class, () -> WorldExporter.export(tooTall, world));
        Files.delete(world.resolve("region/r.0.0.mca"));
        assertThrows(IOException.class, () -> WorldExporter.export(model(), world));
    }

    private static int[] decode(CompoundTag heightmaps, String type) throws IOException {
        long[] data = heightmaps.get(type, LongArrayTag.class).orElseThrow().toArray();
        int[] heights = HeightmapCodec.decode(data, 384);
        assertArrayEquals(heights, HeightmapCodec.decode(data, 384));
        return heights;
    }

    private static String blockAt(ChunkData chunk, int worldX, int y, int worldZ) {
        int sectionY = Math.floorDiv(y, 16);
        ChunkSection section = chunk.sections().stream().filter(s -> s.y() == sectionY).findFirst().orElseThrow();
        return section.blocks().orElseThrow()
                .get(ChunkSection.blockIndex(worldX & 15, Math.floorMod(y, 16), worldZ & 15)).id().toString();
    }
}
