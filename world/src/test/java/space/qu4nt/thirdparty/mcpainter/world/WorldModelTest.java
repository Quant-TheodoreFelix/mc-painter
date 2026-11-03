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
    void revisionIdentifiesEachHistoryState() {
        WorldModel model = twoTiles();
        long setup = model.revision();
        try (Edit edit = model.beginEdit("first")) {
            edit.setHeight(1, 1, 80);
        }
        long first = model.revision();
        assertTrue(first != setup);
        try (Edit edit = model.beginEdit("noop")) {
            edit.setHeight(1, 1, 80);
        }
        assertEquals(first, model.revision());
        model.undo();
        assertEquals(setup, model.revision());
        model.redo();
        assertEquals(first, model.revision());
        model.undo();
        try (Edit edit = model.beginEdit("second")) {
            edit.setHeight(2, 2, 90);
        }
        assertTrue(model.revision() != first && model.revision() != setup);
        assertEquals(0, WorldModel.create(-64, 320, 62).revision());
    }

    @Test
    void removedTilesComeBackWithUndo() {
        WorldModel model = twoTiles();
        try (Edit edit = model.beginEdit("raise")) {
            edit.setHeight(130, 5, 99);
        }
        long raised = model.revision();
        try (Edit edit = model.beginEdit("remove")) {
            edit.setHeight(130, 6, 70);
            assertTrue(edit.removeTile(new TilePos(1, 0)));
            assertFalse(edit.removeTile(new TilePos(1, 0)));
            assertFalse(edit.removeTile(new TilePos(5, 5)));
            assertFalse(edit.setHeight(130, 5, 10));
        }
        assertEquals(List.of(new TilePos(0, 0)), model.tilePositions());
        assertFalse(model.contains(130, 5));
        assertEquals("remove", model.undoLabel().orElseThrow());
        assertTrue(model.undo());
        assertEquals(raised, model.revision());
        assertEquals(99f, model.height(130, 5));
        assertEquals(64f, model.height(130, 6));
        assertTrue(model.redo());
        assertEquals(1, model.tileCount());
        assertTrue(model.undo());
        assertEquals(2, model.tileCount());
    }

    @Test
    void addingAndRemovingInOneEditLeavesNoHistory() {
        WorldModel model = twoTiles();
        long before = model.revision();
        try (Edit edit = model.beginEdit("blip")) {
            assertTrue(edit.addTile(new TilePos(3, 3), 64, TerrainType.SAND));
            assertTrue(edit.removeTile(new TilePos(3, 3)));
        }
        assertEquals(before, model.revision());
        assertEquals("setup", model.undoLabel().orElseThrow());
        try (Edit edit = model.beginEdit("swap")) {
            edit.removeTile(new TilePos(0, 0));
            edit.addTile(new TilePos(0, 0), 10, TerrainType.STONE);
        }
        assertEquals(10f, model.height(0, 0));
        model.undo();
        assertEquals(64f, model.height(0, 0));
        assertEquals(TerrainType.GRASS, model.terrain(0, 0));
    }

    @Test
    void usedBiomesListsOnlyPaintedBiomes() {
        WorldModel model = twoTiles();
        assertEquals(List.of(WorldModel.DEFAULT_BIOME), model.usedBiomes());
        try (Edit edit = model.beginEdit("paint")) {
            edit.setBiome(3, 3, "minecraft:desert");
            edit.setBiome(200, 3, "mymod:glow/caves");
            edit.setBiome(200, 3, "minecraft:cherry_grove");
        }
        assertEquals(List.of(WorldModel.DEFAULT_BIOME, "minecraft:desert", "minecraft:cherry_grove"),
                model.usedBiomes());
        assertEquals(4, model.biomes().size());
        try (Edit edit = model.beginEdit("remove")) {
            edit.removeTile(new TilePos(0, 0));
        }
        assertEquals(List.of(WorldModel.DEFAULT_BIOME, "minecraft:cherry_grove"), model.usedBiomes());
        try (Edit edit = model.beginEdit("clear")) {
            edit.removeTile(new TilePos(1, 0));
        }
        assertEquals(List.of(), model.usedBiomes());
    }

    @Test
    void seedChangesAreUndoable() {
        WorldModel model = WorldModel.builder(-64, 320, 62).generation(new GenerationSettings(42)).build();
        assertEquals(42, model.generation().seed());
        assertEquals(0, WorldModel.create(-64, 320, 62).generation().seed());
        long start = model.revision();
        try (Edit edit = model.beginEdit("same")) {
            edit.setGeneration(new GenerationSettings(42));
        }
        assertEquals(start, model.revision());
        assertFalse(model.canUndo());
        try (Edit edit = model.beginEdit("seed")) {
            edit.setGeneration(model.generation().withSeed(7));
            edit.setGeneration(model.generation().withSeed(-9));
        }
        assertEquals(-9, model.generation().seed());
        assertTrue(model.revision() != start);
        assertTrue(model.undo());
        assertEquals(42, model.generation().seed());
        assertEquals(start, model.revision());
        assertTrue(model.redo());
        assertEquals(-9, model.generation().seed());
        try (Edit edit = model.beginEdit("tiles")) {
            edit.addTile(new TilePos(0, 0), 64, TerrainType.GRASS);
        }
        model.undo();
        assertEquals(-9, model.generation().seed());
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

    @Test
    void snapshotsCopySelectedTilesAndIgnoreLaterEdits() {
        WorldModel model = WorldModel.builder(-64, 320, 62).generation(new GenerationSettings(5, false)).build();
        try (Edit edit = model.beginEdit("setup")) {
            edit.addTile(new TilePos(0, 0), 70, TerrainType.GRASS);
            edit.addTile(new TilePos(1, 0), 80, TerrainType.SAND);
            edit.setBiome(3, 3, "minecraft:desert");
            edit.setLayer(4, 4, Layer.PEONY, 99);
        }
        WorldModel copy = model.snapshot(List.of(new TilePos(0, 0), new TilePos(5, 5)));
        assertEquals(1, copy.tileCount());
        assertFalse(copy.contains(130, 0));
        assertEquals(5, copy.generation().seed());
        assertEquals(model.minY(), copy.minY());
        assertEquals("minecraft:desert", copy.biome(3, 3));
        assertEquals(99, copy.layer(4, 4, Layer.PEONY));
        try (Edit edit = model.beginEdit("later")) {
            edit.setHeight(3, 3, 90);
            edit.setBiome(5, 5, "minecraft:swamp");
            edit.setLayer(4, 4, Layer.PEONY, 0);
        }
        assertEquals(70, copy.height(3, 3));
        assertEquals(99, copy.layer(4, 4, Layer.PEONY));
        assertEquals("minecraft:plains", copy.biome(5, 5));
        assertEquals(0, copy.revision());
    }

    private static WorldModel flatArea() {
        WorldModel.Builder builder = WorldModel.builder(-64, 320, 62).bounds(new TileRect(-5, -5, 5, 5))
                .base(BaseTerrain.flat(64.5f, TerrainType.GRASS)).editable(new TileRect(0, 0, 0, 0))
                .generation(new GenerationSettings(7, true));
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                builder.baseTile(new TilePos(x, z));
            }
        }
        return builder.build();
    }

    @Test
    void flatBaseTilesAreSharedUntilEdited() {
        WorldModel model = flatArea();
        assertEquals(9, model.tileCount());
        assertTrue(model.isBaseTile(new TilePos(0, 0)));
        assertEquals(Layer.INITIAL_ORE_VALUE, model.layer(5, 5, Layer.COAL_ORE));
        try (Edit edit = model.beginEdit("paint")) {
            assertTrue(edit.setHeight(5, 5, 90));
            assertFalse(edit.setHeight(-5, 5, 90));
            assertFalse(edit.setHeight(130, 5, 90));
        }
        assertEquals(90f, model.height(5, 5));
        assertEquals(64.5f, model.height(6, 5));
        assertEquals(64.5f, model.height(-5, 5));
        assertEquals(64.5f, model.height(130, 5));
        assertFalse(model.isBaseTile(new TilePos(0, 0)));
        assertTrue(model.isBaseTile(new TilePos(1, 0)));
        assertEquals(java.util.Set.of(new TilePos(0, 0)), model.dirtyTiles());

        model.markClean(model.dirtyTiles());
        assertTrue(model.dirtyTiles().isEmpty());
        assertTrue(model.undo());
        assertEquals(64.5f, model.height(5, 5));
        assertTrue(model.isBaseTile(new TilePos(0, 0)));
        assertEquals(java.util.Set.of(new TilePos(0, 0)), model.dirtyTiles());
        assertTrue(model.redo());
        assertEquals(90f, model.height(5, 5));
        assertEquals(64.5f, model.height(10, 10));
    }

    @Test
    void resettingAFlatTileRestoresTheBaseAndVoidTilesAreRemoved() {
        WorldModel model = flatArea();
        try (Edit edit = model.beginEdit("paint")) {
            edit.setTerrain(3, 3, TerrainType.SAND);
        }
        try (Edit edit = model.beginEdit("reset")) {
            assertTrue(edit.resetTile(new TilePos(0, 0)));
            assertFalse(edit.resetTile(new TilePos(0, 0)));
            assertFalse(edit.resetTile(new TilePos(1, 0)));
            assertFalse(edit.addTile(new TilePos(2, 2), 70, TerrainType.SAND));
        }
        assertEquals(TerrainType.GRASS, model.terrain(3, 3));
        assertTrue(model.isBaseTile(new TilePos(0, 0)));
        assertTrue(model.undo());
        assertEquals(TerrainType.SAND, model.terrain(3, 3));

        WorldModel empty = WorldModel.builder(-64, 320, 62).editable(new TileRect(0, 0, 1, 0)).build();
        try (Edit edit = empty.beginEdit("add")) {
            assertTrue(edit.addTile(new TilePos(1, 0), 70, TerrainType.SAND));
            assertFalse(edit.addTile(new TilePos(2, 0), 70, TerrainType.SAND));
        }
        try (Edit edit = empty.beginEdit("reset")) {
            assertTrue(edit.resetTile(new TilePos(1, 0)));
        }
        assertEquals(0, empty.tileCount());
        assertTrue(empty.isBaseTile(new TilePos(1, 0)));
    }

    @Test
    void tilesMustLieInsideTheBounds() {
        WorldModel.Builder builder = WorldModel.builder(-64, 320, 62).bounds(new TileRect(0, 0, 0, 0))
                .base(BaseTerrain.flat(64, TerrainType.GRASS));
        builder.baseTile(new TilePos(1, 0));
        assertThrows(IllegalArgumentException.class, builder::build);
        assertThrows(IllegalStateException.class,
                () -> WorldModel.builder(-64, 320, 62).baseTile(new TilePos(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> WorldModel.builder(-64, 320, 62)
                .base(BaseTerrain.flat(400, TerrainType.GRASS)).build());
    }
}
