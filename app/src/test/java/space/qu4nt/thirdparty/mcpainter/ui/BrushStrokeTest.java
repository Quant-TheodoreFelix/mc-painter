package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SequencedSet;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class BrushStrokeTest {

    private final List<SequencedSet<TilePos>> changes = new ArrayList<>();
    private final List<Integer> limits = new ArrayList<>();
    private final BrushStroke.Listener listener = new BrushStroke.Listener() {
        @Override
        public void changed(SequencedSet<TilePos> tiles) {
            changes.add(tiles);
        }

        @Override
        public void tileLimitReached(int limit) {
            limits.add(limit);
        }
    };

    private static BrushSettings settings(Tool tool, int radius) {
        BrushSettings settings = new BrushSettings();
        settings.setTool(tool);
        settings.setRadius(radius);
        settings.setFalloff(Brush.Falloff.CONSTANT);
        settings.setStrengthPercent(100);
        return settings;
    }

    @Test
    void oneStrokeIsOneUndoStep() {
        WorldModel model = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        BrushStroke stroke = BrushStroke.begin(model, settings(Tool.RAISE, 2), Tool.RAISE, "올리기", 64, 64, listener);
        stroke.repeat();
        stroke.dragTo(68, 64);
        stroke.finish();
        assertTrue(model.height(64, 64) >= 66);
        assertEquals("올리기", model.undoLabel().orElseThrow());
        assertTrue(model.undo());
        assertEquals(64f, model.height(64, 64));
        assertFalse(model.canUndo());
        assertFalse(changes.isEmpty());
        assertTrue(changes.getFirst().contains(new TilePos(0, 0)));
    }

    @Test
    void dragsInterpolateWithoutGaps() {
        WorldModel model = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        BrushSettings sand = settings(Tool.TERRAIN, 0);
        sand.setTerrain(TerrainType.SAND);
        BrushStroke stroke = BrushStroke.begin(model, sand, Tool.TERRAIN, "t", 20, 60, listener);
        stroke.dragTo(40, 60);
        stroke.repeat();
        stroke.finish();
        for (int x = 20; x <= 40; x++) {
            assertEquals(TerrainType.SAND, model.terrain(x, 60), "x=" + x);
            assertEquals(TerrainType.GRASS, model.terrain(x, 59));
            assertEquals(TerrainType.GRASS, model.terrain(x, 61));
        }
        assertEquals(TerrainType.GRASS, model.terrain(41, 60));
        BrushSettings stone = settings(Tool.TERRAIN, 0);
        stone.setTerrain(TerrainType.STONE);
        BrushStroke second = BrushStroke.begin(model, stone, Tool.TERRAIN, "t", 20, 80, listener);
        second.dragTo(40, 90);
        second.finish();
        for (int x = 20; x <= 40; x++) {
            int z = (int) Math.round(80 + (x - 20) * 0.5);
            boolean painted = false;
            for (int dz = -1; dz <= 1; dz++) {
                painted |= model.terrain(x, z + dz) == TerrainType.STONE;
            }
            assertTrue(painted, "gap at x=" + x);
        }
        assertThrows(IllegalStateException.class, () -> second.dragTo(0, 0));
    }

    @Test
    void flattenUsesTheStartingColumnAndInverseLowers() {
        WorldModel model = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        try (Edit edit = model.beginEdit("hill")) {
            edit.setHeight(10, 10, 80.7f);
        }
        BrushStroke flatten = BrushStroke.begin(model, settings(Tool.FLATTEN, 3), Tool.FLATTEN, "f", 10, 10,
                listener);
        flatten.finish();
        assertEquals(80.5f, model.height(8, 10), 1e-4);
        assertEquals(Tool.LOWER, Tool.RAISE.inverse().orElseThrow());
        assertTrue(Tool.TERRAIN.inverse().isEmpty());
        BrushStroke lower = BrushStroke.begin(model, settings(Tool.RAISE, 0), Tool.LOWER, "l", 30, 30, listener);
        lower.finish();
        assertEquals(64f - BrushStroke.RAISE_PER_DAB, model.height(30, 30), 1e-4);
    }

    @Test
    void paintsWaterAndBiome() {
        WorldModel model = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        BrushSettings water = settings(Tool.WATER, 1);
        water.setWaterLevel(70);
        BrushStroke.begin(model, water, Tool.WATER, "w", 5, 5, listener).finish();
        assertEquals(70, model.waterLevel(5, 5));
        assertEquals(63, model.waterLevel(5, 8));
        BrushSettings biome = settings(Tool.BIOME, 1);
        biome.setBiome("desert");
        BrushStroke.begin(model, biome, Tool.BIOME, "b", 5, 5, listener).finish();
        assertEquals("minecraft:desert", model.biome(5, 5));
    }

    @Test
    void addsTilesUntilTheLimit() {
        WorldModel model = WorldModel.builder(-64, 320, 63).maxTiles(3).build();
        BrushSettings add = settings(Tool.ADD_TILES, 10);
        BrushStroke stroke = BrushStroke.begin(model, add, Tool.ADD_TILES, "a", 0, 0, listener);
        assertEquals(3, model.tileCount());
        stroke.dragTo(300, 300);
        stroke.finish();
        assertEquals(3, model.tileCount());
        assertEquals(List.of(3), limits);
        assertEquals(64f, model.height(-5, -5));
        assertFalse(model.contains(5, 5));
        assertTrue(model.undo());
        assertEquals(0, model.tileCount());
        assertTrue(model.beginEdit("next") != null);
    }

    @Test
    void autoAddCreatesTilesUnderAnyBrushWithinOneUndoStep() {
        WorldModel model = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        BrushSettings off = settings(Tool.RAISE, 4);
        BrushStroke.begin(model, off, Tool.RAISE, "r", 200, 20, listener).finish();
        assertEquals(1, model.tileCount());

        BrushSettings on = settings(Tool.RAISE, 4);
        on.setAutoAddTiles(true);
        on.setTerrain(TerrainType.SAND);
        long before = model.revision();
        BrushStroke stroke = BrushStroke.begin(model, on, Tool.RAISE, "r", 200, 20, listener);
        stroke.dragTo(260, 20);
        stroke.finish();
        assertEquals(3, model.tileCount());
        assertTrue(model.contains(260, 20));
        assertTrue(model.height(200, 20) > 64);
        assertEquals(64f, model.height(250, 100));
        assertEquals(TerrainType.SAND, model.terrain(250, 100));
        assertTrue(changes.getLast().contains(new TilePos(2, 0)));
        model.undo();
        assertEquals(1, model.tileCount());
        assertEquals(before, model.revision());

        BrushSettings flatten = settings(Tool.FLATTEN, 2);
        flatten.setAutoAddTiles(true);
        BrushStroke.begin(model, flatten, Tool.FLATTEN, "f", -10, 10, listener).finish();
        assertEquals(64.5f, model.height(-10, 10), 1e-4);
    }

    @Test
    void deletesTouchedTilesWithoutAutoAdd() {
        WorldModel model = Worlds.blank(3, 1, 64, 63, TerrainType.GRASS);
        List<TilePos> start = model.tilePositions();
        TilePos left = start.getFirst();
        TilePos middle = start.get(1);
        int x = middle.minBlockX() + 5;
        int z = middle.minBlockZ() + 64;
        BrushSettings delete = settings(Tool.DELETE_TILES, 10);
        delete.setAutoAddTiles(true);
        long before = model.revision();
        BrushStroke stroke = BrushStroke.begin(model, delete, Tool.DELETE_TILES, "d", x, z, listener);
        stroke.dragTo(x, z + 300);
        stroke.finish();
        assertEquals(List.of(start.getLast()), model.tilePositions());
        assertFalse(model.contains(left.minBlockX(), left.minBlockZ()));
        assertTrue(changes.getFirst().contains(start.getLast()));
        assertEquals(Tool.DELETE_TILES, Tool.ADD_TILES.inverse().orElseThrow());
        assertEquals(Tool.ADD_TILES, Tool.DELETE_TILES.inverse().orElseThrow());
        assertTrue(model.undo());
        assertEquals(start, model.tilePositions());
        assertEquals(before, model.revision());
    }

    @Test
    void paintsTheChosenFlowersTogetherAsOneLayer() {
        WorldModel model = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        BrushSettings paint = settings(Tool.LAYER, 3);
        assertEquals(List.of(Layer.OAK_TREES), paint.paintLayers());
        paint.setFlowers(List.of(Layer.PEONY, Layer.CORNFLOWER, Layer.PEONY));
        assertEquals(List.of(Layer.CORNFLOWER, Layer.PEONY), paint.flowers());
        assertEquals(List.of(Layer.OAK_TREES), paint.paintLayers());
        paint.setLayerGroup(LayerGroup.FLOWERS);
        assertEquals(List.of(Layer.CORNFLOWER, Layer.PEONY), paint.paintLayers());
        assertThrows(IllegalArgumentException.class, () -> paint.setFlowers(List.of()));
        assertThrows(IllegalArgumentException.class, () -> paint.setFlowers(List.of(Layer.GRASS)));
        long before = model.revision();
        BrushStroke stroke = BrushStroke.begin(model, paint, Tool.LAYER, "f", 10, 10, listener);
        stroke.repeat();
        stroke.finish();
        int value = model.layer(10, 10, Layer.PEONY);
        assertTrue(value > 0);
        assertEquals(value, model.layer(10, 10, Layer.CORNFLOWER));
        assertEquals(0, model.layer(10, 10, Layer.DANDELION));
        BrushStroke.begin(model, paint, Tool.LAYER_ERASE, "e", 10, 10, listener).finish();
        assertTrue(model.layer(10, 10, Layer.PEONY) < value);
        assertEquals(model.layer(10, 10, Layer.PEONY), model.layer(10, 10, Layer.CORNFLOWER));
        model.undo();
        model.undo();
        assertEquals(before, model.revision());
        assertEquals(0, model.layer(10, 10, Layer.PEONY));
    }

    @Test
    void paintsAndErasesTheSelectedLayer() {
        WorldModel model = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        BrushSettings paint = settings(Tool.LAYER, 3);
        paint.setLayerGroup(LayerGroup.GRASS);
        BrushStroke stroke = BrushStroke.begin(model, paint, Tool.LAYER, "l", 10, 10, listener);
        int once = model.layer(10, 10, Layer.GRASS);
        assertEquals(Math.round(BrushStroke.LAYER_PER_DAB * Layer.MAX_VALUE), once);
        stroke.repeat();
        stroke.repeat();
        stroke.finish();
        assertEquals(3 * once, model.layer(10, 10, Layer.GRASS));
        assertEquals(0, model.layer(10, 10, Layer.POPPY));
        assertEquals(Tool.LAYER_ERASE, Tool.LAYER.inverse().orElseThrow());
        BrushStroke erase = BrushStroke.begin(model, paint, Tool.LAYER_ERASE, "e", 10, 10, listener);
        erase.finish();
        assertEquals(2 * once, model.layer(10, 10, Layer.GRASS));
        model.undo();
        model.undo();
        assertEquals(0, model.layer(10, 10, Layer.GRASS));
        assertEquals(0, Tool.LAYER.shortcut());
        assertEquals(Tool.NO_SHORTCUT, Tool.LAYER_ERASE.shortcut());
        assertFalse(Tool.LAYER_ERASE.visible());
        assertEquals(9, Tool.DELETE_TILES.shortcut());
    }

    @Test
    void touchedTilesFollowTheBrushShape() {
        Brush round = new Brush(20, Brush.Falloff.CONSTANT);
        assertEquals(List.of(new TilePos(0, 0)), BrushStroke.touchedTiles(round, 64, 64));
        assertEquals(4, BrushStroke.touchedTiles(round, 0, 0).size());
        assertEquals(3, BrushStroke.touchedTiles(round, 112, 112).size());
        Brush square = new Brush(20, Brush.Falloff.CONSTANT, BrushShape.SQUARE);
        assertEquals(4, BrushStroke.touchedTiles(square, 112, 112).size());
    }

    @Test
    void autoAddStopsAtTheTileLimit() {
        WorldModel model = WorldModel.builder(-64, 320, 63).maxTiles(1).build();
        BrushSettings paint = settings(Tool.TERRAIN, 3);
        paint.setAutoAddTiles(true);
        BrushStroke stroke = BrushStroke.begin(model, paint, Tool.TERRAIN, "t", 0, 0, listener);
        stroke.dragTo(0, 40);
        stroke.finish();
        assertEquals(1, model.tileCount());
        assertEquals(List.of(1), limits);
    }

    @Test
    void tilesAroundCoversTheMargin() {
        SequencedSet<TilePos> tiles = BrushStroke.tilesAround(0, 0, 16);
        assertEquals(List.of(new TilePos(-1, -1), new TilePos(0, -1), new TilePos(-1, 0), new TilePos(0, 0)),
                List.copyOf(tiles));
        assertEquals(1, BrushStroke.tilesAround(64, 64, 16).size());
    }
}
