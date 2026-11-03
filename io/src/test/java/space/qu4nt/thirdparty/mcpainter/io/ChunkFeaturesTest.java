package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.anvil.world.DataVersions;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class ChunkFeaturesTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static WorldModel world(TerrainType terrain, float height, Layer layer, int value, int tiles) {
        WorldModel model = WorldModel.builder(MIN_Y, MAX_Y, 63).generation(new GenerationSettings(77)).build();
        try (Edit edit = model.beginEdit("setup")) {
            for (int tz = 0; tz < tiles; tz++) {
                for (int tx = 0; tx < tiles; tx++) {
                    TilePos pos = new TilePos(tx, tz);
                    edit.addTile(pos, height, terrain);
                    if (layer != null) {
                        edit.fillLayer(pos, layer, value);
                    }
                }
            }
        }
        return model;
    }

    private record Region(Map<String, String> blocks, BlockCatalog catalog) {

        String id(int x, int y, int z) {
            return blocks.getOrDefault(x + "," + y + "," + z, "");
        }
    }

    private static Region features(WorldModel model, int dataVersion, int fromChunk, int toChunk) {
        BlockCatalog catalog = new BlockCatalog(dataVersion);
        Map<String, String> blocks = new HashMap<>();
        for (int cz = fromChunk; cz <= toChunk; cz++) {
            for (int cx = fromChunk; cx <= toChunk; cx++) {
                WorldExporter.ChunkColumns columns = new WorldExporter.ChunkColumns(model, catalog, cx, cz, MIN_Y,
                        MAX_Y);
                ChunkFeatures features = ChunkFeatures.generate(model, catalog, columns::blockAt, cx, cz, MIN_Y,
                        MAX_Y);
                for (int y = MIN_Y + 1; y < MAX_Y; y++) {
                    for (int z = cz * 16; z < cz * 16 + 16; z++) {
                        for (int x = cx * 16; x < cx * 16 + 16; x++) {
                            BlockStateEntry state = features.at(x, y, z);
                            if (state != null) {
                                blocks.put(x + "," + y + "," + z, state.id().path()
                                        + state.properties().filter(p -> !p.isEmpty()).map(Object::toString).orElse(""));
                            }
                        }
                    }
                }
            }
        }
        return new Region(blocks, catalog);
    }

    @Test
    void oresOnlyReplaceStoneAndUseDeepslateVariantsBelowZero() {
        Region none = features(world(TerrainType.STONE, 150, null, 0, 1), DataVersions.V26_3, 1, 2);
        assertTrue(none.blocks().isEmpty());
        WorldModel model = world(TerrainType.STONE, 150, Layer.IRON_ORE, Layer.MAX_VALUE, 1);
        try (Edit edit = model.beginEdit("coal")) {
            edit.fillLayer(new TilePos(0, 0), Layer.COAL_ORE, Layer.INITIAL_ORE_VALUE);
        }
        Region region = features(model, DataVersions.V26_3, 1, 2);
        int coal = 0;
        int iron = 0;
        int deepIron = 0;
        for (Map.Entry<String, String> entry : region.blocks().entrySet()) {
            int y = Integer.parseInt(entry.getKey().split(",")[1]);
            switch (entry.getValue()) {
                case "coal_ore" -> {
                    coal++;
                    assertTrue(y >= 0 - 2 && y <= 192 + 2, entry.toString());
                }
                case "iron_ore" -> {
                    iron++;
                    assertTrue(y >= 0, entry.toString());
                }
                case "deepslate_iron_ore" -> {
                    deepIron++;
                    assertTrue(y < WorldExporter.DEEPSLATE_BLEND, entry.toString());
                }
                case "deepslate_coal_ore" -> assertTrue(y < WorldExporter.DEEPSLATE_BLEND, entry.toString());
                default -> throw new AssertionError("unexpected " + entry);
            }
        }
        assertTrue(coal > 50 && iron > 50 && deepIron > 50, coal + " " + iron + " " + deepIron);
        Region dirt = features(world(TerrainType.DIRT, -50, Layer.IRON_ORE, Layer.MAX_VALUE, 1), DataVersions.V26_3, 1, 1);
        assertTrue(dirt.blocks().values().stream().allMatch(v -> v.equals("deepslate_iron_ore")));
        assertTrue(dirt.blocks().keySet().stream().allMatch(k -> Integer.parseInt(k.split(",")[1]) < -53));
    }

    @Test
    void treesAreWholeAcrossChunkBordersAndLeavesNeverDecay() {
        WorldModel model = world(TerrainType.GRASS, 70, Layer.OAK_TREES, Layer.MAX_VALUE, 1);
        Region region = features(model, 4671, 0, 7);
        List<int[]> tops = new ArrayList<>();
        int leaves = 0;
        for (Map.Entry<String, String> entry : region.blocks().entrySet()) {
            String[] p = entry.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            String value = entry.getValue();
            if (value.startsWith("oak_log") && !region.id(x, y + 1, z).startsWith("oak_log")) {
                tops.add(new int[] {x, y, z});
                assertEquals("dirt", region.id(x, 70, z));
                assertTrue(y - 70 >= ChunkFeatures.MIN_TRUNK && y - 70 < ChunkFeatures.MIN_TRUNK
                        + ChunkFeatures.TRUNK_VARIATION, entry.toString());
            }
            if (value.startsWith("oak_leaves")) {
                leaves++;
                assertTrue(value.contains("waterlogged=false") && value.contains("persistent=false"), value);
                int distance = Integer.parseInt(value.replaceAll(".*distance=(\\d).*", "$1"));
                assertTrue(distance >= 1 && distance <= 6, value);
                assertTrue(y > 71, entry.toString());
            }
        }
        assertTrue(tops.size() > 300, "trees " + tops.size());
        assertTrue(leaves > tops.size() * 20, "leaves " + leaves);
        int crossing = 0;
        for (int[] top : tops) {
            int x = top[0];
            int y = top[1];
            int z = top[2];
            int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] side : sides) {
                int sx = x + side[0];
                int sz = z + side[1];
                if (sx < 0 || sz < 0 || sx >= 128 || sz >= 128) {
                    continue;
                }
                String block = region.id(sx, y, sz);
                assertTrue(block.startsWith("oak_leaves") || block.startsWith("oak_log"), x + "," + y + "," + z);
                if ((sx >> 4) != (x >> 4) || (sz >> 4) != (z >> 4)) {
                    crossing++;
                }
            }
            assertTrue(region.id(x, y + 1, z).startsWith("oak_leaves"));
        }
        assertTrue(crossing > 10, "crossing " + crossing);
    }

    @Test
    void oldVersionsGetTheirOwnPropertySets() {
        WorldModel model = world(TerrainType.GRASS, 70, Layer.OAK_TREES, Layer.MAX_VALUE, 1);
        Region old = features(model, DataVersions.V1_18, 2, 2);
        String leaf = old.blocks().values().stream().filter(v -> v.startsWith("oak_leaves")).findFirst().orElseThrow();
        assertFalse(leaf.contains("waterlogged"), leaf);
        Region snapshot = features(model, DataVersions.V1_18_2 + 10, 2, 2);
        assertTrue(snapshot.blocks().isEmpty());
        WorldModel meadow = world(TerrainType.GRASS, 70, Layer.GRASS, Layer.MAX_VALUE, 1);
        assertTrue(features(meadow, DataVersions.V1_18, 2, 2).blocks().containsValue("grass"));
        assertFalse(features(meadow, DataVersions.V1_18, 2, 2).blocks().containsValue("short_grass"));
        assertTrue(features(meadow, DataVersions.V26_3, 2, 2).blocks().containsValue("short_grass"));
        Region between = features(meadow, DataVersions.V1_20_2 + 5, 2, 2);
        assertTrue(between.blocks().values().stream().allMatch(v -> v.startsWith("tall_grass")));
    }

    @Test
    void plantsGrowOnlyOnDryGrass() {
        WorldModel model = world(TerrainType.GRASS, 70, Layer.GRASS, Layer.MAX_VALUE, 1);
        try (Edit edit = model.beginEdit("more")) {
            edit.fillLayer(new TilePos(0, 0), Layer.FLOWERS, Layer.MAX_VALUE);
            for (int z = 0; z < 16; z++) {
                for (int x = 16; x < 32; x++) {
                    edit.setTerrain(x, z, TerrainType.SAND);
                    edit.setTerrain(x, z + 16, TerrainType.GRASS);
                    edit.setWaterLevel(x, z + 16, 75);
                }
            }
        }
        Region region = features(model, DataVersions.V26_3, 0, 1);
        int flowers = 0;
        int shortGrass = 0;
        int tall = 0;
        for (Map.Entry<String, String> entry : region.blocks().entrySet()) {
            String[] p = entry.getKey().split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            assertFalse(x >= 16 && x < 32 && z < 32, "plant on sand or under water " + entry);
            switch (entry.getValue()) {
                case "dandelion", "poppy" -> flowers++;
                case "short_grass" -> shortGrass++;
                case "tall_grass{half=lower}" -> {
                    tall++;
                    assertEquals("tall_grass{half=upper}", region.id(x, y + 1, z));
                }
                case "tall_grass{half=upper}" -> assertEquals("tall_grass{half=lower}", region.id(x, y - 1, z));
                default -> throw new AssertionError(entry.toString());
            }
            assertTrue(y == 71 || y == 72, entry.toString());
        }
        int columns = 256 * 2;
        assertTrue(Math.abs(flowers - columns * ChunkFeatures.FLOWER_COVER) < columns * 0.08, "flowers " + flowers);
        double grassShare = (1 - ChunkFeatures.FLOWER_COVER) * ChunkFeatures.GRASS_COVER * columns;
        assertTrue(Math.abs(shortGrass + tall - grassShare) < columns * 0.08, "grass " + (shortGrass + tall));
        assertTrue(tall > 0);
    }

    @Test
    void featuresDoNotDependOnFarAwayTiles() {
        WorldModel small = world(TerrainType.GRASS, 70, Layer.OAK_TREES, 200, 1);
        WorldModel large = world(TerrainType.GRASS, 70, Layer.OAK_TREES, 200, 1);
        try (Edit edit = large.beginEdit("far")) {
            edit.addTile(new TilePos(9, 9), 90, TerrainType.DIRT);
            edit.fillLayer(new TilePos(9, 9), Layer.OAK_TREES, Layer.MAX_VALUE);
            edit.fillLayer(new TilePos(9, 9), Layer.IRON_ORE, Layer.MAX_VALUE);
        }
        assertEquals(features(small, DataVersions.V26_3, 0, 7).blocks(), features(large, DataVersions.V26_3, 0, 7).blocks());
        WorldModel reseeded = world(TerrainType.GRASS, 70, Layer.OAK_TREES, 200, 1);
        try (Edit edit = reseeded.beginEdit("seed")) {
            edit.setGeneration(reseeded.generation().withSeed(78));
        }
        assertFalse(features(small, DataVersions.V26_3, 0, 3).blocks()
                .equals(features(reseeded, DataVersions.V26_3, 0, 3).blocks()));
    }
}
