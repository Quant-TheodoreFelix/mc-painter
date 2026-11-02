package space.qu4nt.thirdparty.mcpainter.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorldModelTest {

    private static WorldModel twoTiles() {
        WorldModel model = WorldModel.create(-64, 320, 62);
        try (Edit edit = model.beginEdit("setup")) {
            edit.addTile(new TilePos(0, 0), 64, TerrainType.GRASS);
            edit.addTile(new TilePos(1, 0), 64, TerrainType.GRASS);
        }
        return model;
    }

    @Test
    void storesColumnsAcrossTiles() {
        WorldModel model = twoTiles();
        assertEquals(2, model.tileCount());
        assertTrue(model.contains(255, 127));
        assertFalse(model.contains(256, 0));
        assertFalse(model.contains(-1, 0));
        try (Edit edit = model.beginEdit("paint")) {
            assertTrue(edit.setHeight(130, 5, 90.5f));
            assertTrue(edit.setTerrain(130, 5, TerrainType.SAND));
            assertTrue(edit.setWaterLevel(130, 5, 70));
            assertTrue(edit.setBiome(130, 5, "minecraft:desert"));
            assertFalse(edit.setHeight(-5, 5, 10));
        }
        assertEquals(90.5f, model.height(130, 5));
        assertEquals(TerrainType.SAND, model.terrain(130, 5));
        assertEquals(70, model.waterLevel(130, 5));
        assertEquals("minecraft:desert", model.biome(130, 5));
        assertEquals("minecraft:plains", model.biome(0, 0));
        assertEquals(62, model.waterLevel(0, 0));
    }

    @Test
    void clampsHeightsAndRejectsInvalidInput() {
        WorldModel model = twoTiles();
        try (Edit edit = model.beginEdit("extreme")) {
            edit.setHeight(0, 0, 10_000);
            edit.setHeight(1, 0, -10_000);
            assertThrows(IllegalArgumentException.class, () -> edit.setHeight(2, 0, Float.NaN));
            assertThrows(IllegalArgumentException.class, () -> edit.setBiome(2, 0, "minecraft:../x"));
            assertThrows(IllegalArgumentException.class, () -> edit.setBiome(2, 0, "Plains"));
        }
        assertEquals(320f, model.height(0, 0));
        assertEquals(-64f, model.height(1, 0));
    }

    @Test
    void undoAndRedoRestoreTiles() {
        WorldModel model = twoTiles();
        try (Edit edit = model.beginEdit("raise")) {
            edit.setHeight(10, 10, 100);
            edit.setHeight(200, 10, 110);
        }
        try (Edit edit = model.beginEdit("sand")) {
            edit.setTerrain(10, 10, TerrainType.SAND);
        }
        assertEquals("sand", model.undoLabel().orElseThrow());
        assertTrue(model.undo());
        assertEquals(TerrainType.GRASS, model.terrain(10, 10));
        assertEquals(100f, model.height(10, 10));
        assertTrue(model.undo());
        assertEquals(64f, model.height(10, 10));
        assertEquals(64f, model.height(200, 10));
        assertTrue(model.undo());
        assertEquals(0, model.tileCount());
        assertFalse(model.undo());

        assertTrue(model.redo());
        assertTrue(model.redo());
        assertEquals(110f, model.height(200, 10));
        assertTrue(model.redo());
        assertEquals(TerrainType.SAND, model.terrain(10, 10));
        assertFalse(model.redo());
    }

    @Test
    void newEditClearsRedoAndNoOpEditsLeaveNoHistory() {
        WorldModel model = twoTiles();
        try (Edit edit = model.beginEdit("raise")) {
            edit.setHeight(1, 1, 80);
        }
        model.undo();
        try (Edit edit = model.beginEdit("same")) {
            edit.setHeight(1, 1, 64);
        }
        assertEquals("setup", model.undoLabel().orElseThrow());
        assertTrue(model.canRedo());
        try (Edit edit = model.beginEdit("lower")) {
            edit.setHeight(1, 1, 50);
        }
        assertEquals("lower", model.undoLabel().orElseThrow());
        assertFalse(model.canRedo());
    }

    @Test
    void onlyOneEditAtATime() {
        WorldModel model = twoTiles();
        Edit first = model.beginEdit("first");
        assertThrows(IllegalStateException.class, () -> model.beginEdit("second"));
        assertThrows(IllegalStateException.class, model::undo);
        first.close();
        assertThrows(IllegalStateException.class, () -> first.setHeight(0, 0, 1));
    }

    @Test
    void tileLimitIsEnforced() {
        WorldModel model = WorldModel.builder(-64, 320, 62).maxTiles(1).build();
        try (Edit edit = model.beginEdit("tiles")) {
            edit.addTile(new TilePos(0, 0), 64, TerrainType.GRASS);
            assertThrows(IllegalStateException.class, () -> edit.addTile(new TilePos(1, 0), 64, TerrainType.GRASS));
        }
    }

    @Test
    void builderValidatesLoadedTiles() {
        float[] heights = new float[Tile.AREA];
        byte[] terrain = new byte[Tile.AREA];
        short[] water = new short[Tile.AREA];
        short[] biomes = new short[Tile.AREA];
        WorldModel.Builder builder = WorldModel.builder(-64, 320, 62).biomes(List.of("minecraft:plains"));
        builder.tile(new TilePos(0, 0), heights, terrain, water, biomes);
        assertThrows(IllegalArgumentException.class, () -> builder.tile(new TilePos(0, 0), heights, terrain, water, biomes));

        float[] nan = heights.clone();
        nan[5] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> builder.tile(new TilePos(1, 0), nan, terrain, water, biomes));
        byte[] badTerrain = terrain.clone();
        badTerrain[5] = 99;
        assertThrows(IllegalArgumentException.class, () -> builder.tile(new TilePos(1, 0), heights, badTerrain, water, biomes));
        assertThrows(IllegalArgumentException.class,
                () -> builder.tile(new TilePos(1, 0), new float[3], terrain, water, biomes));

        short[] badBiome = biomes.clone();
        badBiome[0] = 4;
        WorldModel.Builder second = WorldModel.builder(-64, 320, 62);
        second.tile(new TilePos(0, 0), heights, terrain, water, badBiome);
        assertThrows(IllegalArgumentException.class, second::build);
        assertThrows(IllegalArgumentException.class, () -> WorldModel.builder(-64, 320, 62)
                .biomes(List.of("minecraft:plains", "minecraft:plains")));
    }
}
