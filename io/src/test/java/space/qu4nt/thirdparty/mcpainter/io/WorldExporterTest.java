package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import space.qu4nt.thirdparty.mcpainter.world.BaseTerrain;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
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
            assertEquals("minecraft:deepslate", blockAt(origin, 21, -63, 3));
            assertEquals("minecraft:deepslate", blockAt(origin, 21, -1, 3));
            assertEquals("minecraft:deepslate", blockAt(origin, 21, 0, 3));
            assertEquals("minecraft:stone", blockAt(origin, 21, 8, 3));
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
    void deepslateBlendsIntoStoneDeterministically() throws IOException {
        writeTemplate();
        WorldModel model = WorldModel.builder(-64, 320, 63).generation(new GenerationSettings(5)).build();
        try (Edit edit = model.beginEdit("setup")) {
            edit.addTile(new TilePos(0, 0), 70, TerrainType.STONE);
        }
        WorldExporter.export(model, world);
        int[] counts = new int[WorldExporter.DEEPSLATE_BLEND];
        StringBuilder first = new StringBuilder();
        try (RegionFile region = RegionFile.open(world.resolve("region/r.0.0.mca"))) {
            ChunkData chunk = ChunkData.parse(region.readChunk(3, 3).orElseThrow());
            for (int y = 0; y < WorldExporter.DEEPSLATE_BLEND; y++) {
                for (int z = 48; z < 64; z++) {
                    for (int x = 48; x < 64; x++) {
                        boolean deep = blockAt(chunk, x, y, z).equals("minecraft:deepslate");
                        counts[y] += deep ? 1 : 0;
                        first.append(deep ? '1' : '0');
                    }
                }
            }
        }
        assertEquals(256, counts[0]);
        for (int y = 1; y < WorldExporter.DEEPSLATE_BLEND; y++) {
            double expected = 256.0 * (WorldExporter.DEEPSLATE_BLEND - y) / WorldExporter.DEEPSLATE_BLEND;
            assertTrue(Math.abs(counts[y] - expected) < 40, "y " + y + " count " + counts[y]);
            assertTrue(counts[y] <= counts[y - 1] + 30, "y " + y);
        }
        WorldExporter.export(model, world);
        StringBuilder second = new StringBuilder();
        try (RegionFile region = RegionFile.open(world.resolve("region/r.0.0.mca"))) {
            ChunkData chunk = ChunkData.parse(region.readChunk(3, 3).orElseThrow());
            for (int y = 0; y < WorldExporter.DEEPSLATE_BLEND; y++) {
                for (int z = 48; z < 64; z++) {
                    for (int x = 48; x < 64; x++) {
                        second.append(blockAt(chunk, x, y, z).equals("minecraft:deepslate") ? '1' : '0');
                    }
                }
            }
        }
        assertEquals(first.toString(), second.toString());
    }

    @Test
    void treesCountForMotionBlockingButNotWithoutLeaves() throws IOException {
        writeTemplate();
        WorldModel model = WorldModel.builder(-64, 320, 63).generation(new GenerationSettings(11)).build();
        try (Edit edit = model.beginEdit("forest")) {
            edit.addTile(new TilePos(0, 0), 70, TerrainType.GRASS);
            edit.fillLayer(new TilePos(0, 0), Layer.OAK_TREES, Layer.MAX_VALUE);
        }
        WorldExporter.export(model, world);
        int leafColumns = 0;
        try (RegionFile region = RegionFile.open(world.resolve("region/r.0.0.mca"))) {
            CompoundTag heightmaps = region.readChunk(2, 2).orElseThrow().tag()
                    .get("Heightmaps", CompoundTag.class).orElseThrow();
            int[] blocking = decode(heightmaps, "MOTION_BLOCKING");
            int[] noLeaves = decode(heightmaps, "MOTION_BLOCKING_NO_LEAVES");
            int[] surface = decode(heightmaps, "WORLD_SURFACE");
            for (int i = 0; i < HeightmapCodec.COLUMNS; i++) {
                assertTrue(blocking[i] >= noLeaves[i]);
                assertTrue(surface[i] >= blocking[i]);
                if (blocking[i] > noLeaves[i]) {
                    leafColumns++;
                    int above = noLeaves[i] - (70 + 1 + 64);
                    assertTrue(above == 0 || (above >= ChunkFeatures.MIN_TRUNK
                            && above < ChunkFeatures.MIN_TRUNK + ChunkFeatures.TRUNK_VARIATION), "above " + above);
                }
            }
        }
        assertTrue(leafColumns > 50, "leaf columns " + leafColumns);
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

    private static int chunkCount(Path file) throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        int count = 0;
        try (RegionFile region = RegionFile.open(file)) {
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    if (region.readChunk(x, z).isPresent()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Test
    void writesOnlyTheChosenTilesInParallel() throws IOException {
        writeTemplate();
        WorldModel model = WorldModel.create(-64, 320, 63);
        try (Edit edit = model.beginEdit("setup")) {
            for (TilePos pos : List.of(new TilePos(0, 0), new TilePos(5, 0), new TilePos(-1, 0))) {
                edit.addTile(pos, 70, TerrainType.GRASS);
            }
        }
        List<Long> reports = new ArrayList<>();
        WorldExporter.Result result = WorldExporter.export(model, world,
                List.of(new TilePos(5, 0), new TilePos(-1, 0), new TilePos(9, 9)), 3,
                (done, total) -> reports.add(total));
        assertEquals(2, result.regionsWritten());
        assertEquals(128, result.chunksCreated());
        assertEquals(1, chunkCount(world.resolve("region/r.0.0.mca")));
        assertEquals(64, chunkCount(world.resolve("region/r.1.0.mca")));
        assertEquals(64, chunkCount(world.resolve("region/r.-1.0.mca")));
        assertEquals(List.of(2L, 2L, 2L), reports);
    }

    @Test
    void stopsWhenCancelledAndEstimatesTheWork() throws IOException {
        writeTemplate();
        WorldModel model = WorldModel.create(-64, 320, 63);
        try (Edit edit = model.beginEdit("setup")) {
            for (int x = 0; x < 12; x += 4) {
                edit.addTile(new TilePos(x, 0), 70, TerrainType.GRASS);
            }
        }
        int[] calls = new int[1];
        WorldExporter.Result result = WorldExporter.export(model, world, model.tilePositions(), 1,
                (done, total) -> ++calls[0] < 2);
        assertTrue(result.cancelled());
        assertEquals(1, result.regionsWritten());

        WorldExporter.Estimate estimate = WorldExporter.estimate(390_625, 24_649, 4);
        assertEquals(25_000_000L, estimate.chunks());
        assertEquals(25_000_000L * 4096 + 24_649L * 8192, estimate.bytes());
        assertEquals((long) Math.ceil(390_625 * WorldExporter.SECONDS_PER_TILE / 4), estimate.seconds());
        assertEquals(157L * 157L, WorldExporter.regionCount(TileRect.centeredBlocks(80_000, 80_000)));
        assertEquals(List.of(new TileRect(-1, 0, -1, 0), new TileRect(0, 0, 2, 0)),
                WorldExporter.regionRects(new TileRect(-1, 0, 2, 0)));
    }

    @Test
    void exportsAWholeFlatWorkspaceRegionByRegion() throws IOException {
        writeTemplate();
        WorldSpec spec = new WorldSpec(-64, 320, 63, List.of("minecraft:plains"), new GenerationSettings(1, false),
                new TileRect(-2, -1, 1, 0), BaseTerrain.flat(70.5f, TerrainType.GRASS));
        try (Workspace workspace = Workspace.create(world.resolve("ws"), spec, "flat")) {
            WorldModel area = AreaLoader.load(workspace, new TileRect(0, 0, 0, 0), 64, 2, Progress.NONE);
            assertEquals(java.util.Optional.of(new TileRect(0, 0, 0, 0)), area.editable());
            assertEquals(6, area.tileCount());
            try (Edit edit = area.beginEdit("paint")) {
                edit.setHeight(5, 5, 100);
                assertFalse(edit.setHeight(-5, 5, 100));
            }
            TilePos edited = new TilePos(0, 0);
            workspace.write(List.of(new Workspace.Write(edited, area.tile(edited).orElseThrow(), new int[64], true)),
                    true);
            WorldExporter.Result result = AreaLoader.exportWorkspace(workspace, spec.bounds(), world, 2,
                    Progress.NONE);
            assertEquals(4, result.regionsWritten());
            assertEquals(8 * 64, result.chunksCreated() + result.chunksReplaced());
            try (RegionFile region = RegionFile.open(world.resolve("region/r.0.0.mca"))) {
                ChunkData chunk = ChunkData.parse(region.readChunk(0, 0).orElseThrow());
                assertEquals("minecraft:grass_block", blockAt(chunk, 5, 100, 5));
                assertEquals("minecraft:grass_block", blockAt(chunk, 6, 70, 5));
            }
        }
        WorldSpec empty = new WorldSpec(-64, 320, 63, List.of("minecraft:plains"), GenerationSettings.DEFAULT,
                TileRect.WORLD, BaseTerrain.VOID);
        try (Workspace workspace = Workspace.create(world.resolve("ws2"), empty, "void")) {
            WorldModel model = WorldModel.create(-64, 320, 63);
            try (Edit edit = model.beginEdit("add")) {
                edit.addTile(new TilePos(40, 40), 70, TerrainType.SAND);
            }
            workspace.write(List.of(new Workspace.Write(new TilePos(40, 40),
                    model.tile(new TilePos(40, 40)).orElseThrow(), new int[64], true)), true);
            assertEquals(List.of(new TileRect(40, 40, 43, 43)), AreaLoader.regionsToWrite(workspace, TileRect.WORLD));
            WorldExporter.Result result = AreaLoader.exportWorkspace(workspace, TileRect.WORLD, world, 2,
                    Progress.NONE);
            assertEquals(1, result.regionsWritten());
            assertEquals(64, result.chunksCreated());
        }
    }
}
