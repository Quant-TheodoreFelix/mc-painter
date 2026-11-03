package space.qu4nt.thirdparty.mcpainter.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LayerTest {

    private static WorldModel oneTile(GenerationSettings generation) {
        WorldModel model = WorldModel.builder(-64, 320, 62).generation(generation).build();
        try (Edit edit = model.beginEdit("setup")) {
            edit.addTile(new TilePos(0, 0), 64, TerrainType.GRASS);
        }
        return model;
    }

    @Test
    void layersStartEmptyAndStayUniformUntilPainted() {
        WorldModel model = oneTile(GenerationSettings.DEFAULT);
        Tile tile = model.tile(new TilePos(0, 0)).orElseThrow();
        for (Layer layer : Layer.values()) {
            assertTrue(tile.layerUniform(layer));
            assertTrue(tile.layerEmpty(layer));
        }
        assertEquals(Tile.ESTIMATED_BYTES, tile.estimatedBytes());
        try (Edit edit = model.beginEdit("trees")) {
            assertTrue(edit.setLayer(5, 6, Layer.OAK_TREES, 300));
            assertFalse(edit.setLayer(500, 6, Layer.OAK_TREES, 10));
        }
        assertEquals(Layer.MAX_VALUE, model.layer(5, 6, Layer.OAK_TREES));
        assertEquals(0, model.layer(6, 6, Layer.OAK_TREES));
        assertFalse(tile.layerUniform(Layer.OAK_TREES));
        assertEquals(Tile.ESTIMATED_BYTES + Tile.AREA, tile.estimatedBytes());
        assertTrue(model.undo());
        assertEquals(0, model.layer(5, 6, Layer.OAK_TREES));
        assertTrue(model.redo());
        assertEquals(Layer.MAX_VALUE, model.layer(5, 6, Layer.OAK_TREES));
    }

    @Test
    void fillsAndNoOpPaintingLeaveNoHistory() {
        WorldModel model = oneTile(GenerationSettings.DEFAULT);
        long start = model.revision();
        try (Edit edit = model.beginEdit("zero")) {
            edit.setLayer(1, 1, Layer.GRASS, 0);
            edit.setLayer(2, 2, Layer.POPPY, 9);
            edit.setLayer(2, 2, Layer.POPPY, 0);
        }
        assertEquals(start, model.revision());
        try (Edit edit = model.beginEdit("fill")) {
            assertTrue(edit.fillLayer(new TilePos(0, 0), Layer.COAL_ORE, 40));
            assertFalse(edit.fillLayer(new TilePos(1, 0), Layer.COAL_ORE, 40));
        }
        assertEquals(40, model.layer(127, 127, Layer.COAL_ORE));
        Tile tile = model.tile(new TilePos(0, 0)).orElseThrow();
        assertEquals(40, tile.layerFill(Layer.COAL_ORE));
        byte[] expected = new byte[Tile.AREA];
        Arrays.fill(expected, (byte) 40);
        assertTrue(Arrays.equals(expected, tile.layerValues(Layer.COAL_ORE)));
        try (Edit edit = model.beginEdit("same")) {
            edit.setLayer(3, 3, Layer.COAL_ORE, 41);
            edit.setLayer(3, 3, Layer.COAL_ORE, 40);
        }
        assertEquals("fill", model.undoLabel().orElseThrow());
        assertThrows(IllegalStateException.class, () -> {
            try (Edit edit = model.beginEdit("paint")) {
                edit.setLayer(3, 3, Layer.COAL_ORE, 41);
            }
            tile.layerFill(Layer.COAL_ORE);
        });
    }

    @Test
    void initialOresFillNewTiles() {
        WorldModel model = oneTile(new GenerationSettings(3, true));
        assertEquals(Layer.INITIAL_ORE_VALUE, model.layer(10, 10, Layer.COAL_ORE));
        assertEquals(Layer.INITIAL_ORE_VALUE, model.layer(10, 10, Layer.IRON_ORE));
        assertEquals(0, model.layer(10, 10, Layer.OAK_TREES));
        assertTrue(model.tile(new TilePos(0, 0)).orElseThrow().layerUniform(Layer.COAL_ORE));
        assertEquals(new GenerationSettings(9, true), model.generation().withSeed(9));
        assertEquals(0, oneTile(GenerationSettings.DEFAULT).layer(10, 10, Layer.IRON_ORE));
    }

    @Test
    void sprayRaisesAndLowersWithinRange() {
        WorldModel model = oneTile(GenerationSettings.DEFAULT);
        Brush brush = new Brush(4, Brush.Falloff.LINEAR);
        try (Edit edit = model.beginEdit("spray")) {
            TerrainOps.sprayLayer(edit, 20, 20, brush, Layer.GRASS, 0.5f);
        }
        assertEquals(128, model.layer(20, 20, Layer.GRASS));
        int edge = model.layer(24, 20, Layer.GRASS);
        assertTrue(edge >= 1 && edge < 128, "edge " + edge);
        assertEquals(0, model.layer(26, 20, Layer.GRASS));
        try (Edit edit = model.beginEdit("more")) {
            for (int i = 0; i < 5; i++) {
                TerrainOps.sprayLayer(edit, 20, 20, brush, Layer.GRASS, 1f);
            }
        }
        assertEquals(Layer.MAX_VALUE, model.layer(20, 20, Layer.GRASS));
        try (Edit edit = model.beginEdit("erase")) {
            for (int i = 0; i < 3; i++) {
                TerrainOps.sprayLayer(edit, 20, 20, brush, Layer.GRASS, -1f);
            }
        }
        assertEquals(0, model.layer(20, 20, Layer.GRASS));
        assertThrows(IllegalArgumentException.class, () -> {
            try (Edit edit = model.beginEdit("bad")) {
                TerrainOps.sprayLayer(edit, 0, 0, brush, Layer.GRASS, 2f);
            }
        });
        assertEquals(Layer.COAL_ORE, Layer.fromId("coalOre").orElseThrow());
        assertTrue(Layer.fromId("gold").isEmpty());
    }

    @Test
    void builderLoadsAndValidatesLayers() {
        WorldModel.Builder builder = WorldModel.builder(-64, 320, 62);
        float[] heights = new float[Tile.AREA];
        Arrays.fill(heights, 64);
        builder.tile(new TilePos(0, 0), heights, new byte[Tile.AREA], new short[Tile.AREA], new short[Tile.AREA]);
        byte[] trees = new byte[Tile.AREA];
        trees[Tile.index(4, 5)] = (byte) 200;
        builder.layer(new TilePos(0, 0), Layer.OAK_TREES, trees).layerFill(new TilePos(0, 0), Layer.IRON_ORE, 77);
        assertThrows(IllegalArgumentException.class, () -> builder.layer(new TilePos(0, 0), Layer.GRASS, new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> builder.layerFill(new TilePos(0, 0), Layer.GRASS, 256));
        assertThrows(IllegalArgumentException.class, () -> builder.layerFill(new TilePos(1, 0), Layer.GRASS, 1));
        trees[Tile.index(4, 5)] = 0;
        WorldModel model = builder.build();
        assertEquals(200, model.layer(4, 5, Layer.OAK_TREES));
        assertEquals(77, model.layer(100, 100, Layer.IRON_ORE));
    }

    @Test
    void flowerLayersAreListedInOrderAndReportedWhenUsed() {
        assertEquals(21, Layer.flowers().size());
        assertEquals(Layer.DANDELION, Layer.flowers().getFirst());
        assertEquals(Layer.WILDFLOWERS, Layer.flowers().getLast());
        assertTrue(Layer.flowers().stream().allMatch(Layer::flower));
        assertFalse(Layer.GRASS.flower());
        assertTrue(Layer.fromId("flowers").isEmpty());
        assertEquals(Layer.LILY_OF_THE_VALLEY, Layer.fromId("lilyOfTheValley").orElseThrow());
        WorldModel model = oneTile(new GenerationSettings(1, true));
        assertEquals(Set.of(Layer.COAL_ORE, Layer.IRON_ORE), model.usedLayers());
        try (Edit edit = model.beginEdit("flowers")) {
            edit.setLayer(3, 3, Layer.PEONY, 10);
            edit.setLayer(4, 4, Layer.TORCHFLOWER, 10);
            edit.setLayer(4, 4, Layer.TORCHFLOWER, 0);
        }
        assertEquals(Set.of(Layer.COAL_ORE, Layer.IRON_ORE, Layer.PEONY), model.usedLayers());
    }
}
