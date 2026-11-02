package space.qu4nt.thirdparty.mcpainter.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainOpsTest {

    private static WorldModel flat(float height) {
        WorldModel model = WorldModel.create(-64, 320, 62);
        try (Edit edit = model.beginEdit("setup")) {
            edit.addTile(new TilePos(0, 0), height, TerrainType.GRASS);
        }
        return model;
    }

    @Test
    void brushWeightsFollowFalloff() {
        Brush smooth = new Brush(10, Brush.Falloff.SMOOTH);
        assertEquals(1.0, smooth.weight(0, 0));
        assertTrue(smooth.weight(5, 0) > 0 && smooth.weight(5, 0) < 1);
        assertEquals(0.0, smooth.weight(11, 0));
        assertEquals(1.0, new Brush(10, Brush.Falloff.CONSTANT).weight(7, 7));
        assertEquals(1.0, new Brush(0, Brush.Falloff.LINEAR).weight(0, 0));
        assertEquals(0.0, new Brush(0, Brush.Falloff.LINEAR).weight(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Brush(-1, Brush.Falloff.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> new Brush(Brush.MAX_RADIUS + 1, Brush.Falloff.LINEAR));
    }

    @Test
    void raiseIsStrongestAtCenterAndStaysInsideTiles() {
        WorldModel model = flat(64);
        try (Edit edit = model.beginEdit("raise")) {
            TerrainOps.raise(edit, 2, 2, new Brush(5, Brush.Falloff.LINEAR), 10);
        }
        assertEquals(74f, model.height(2, 2), 1e-4);
        assertTrue(model.height(5, 2) > 64 && model.height(5, 2) < 74);
        assertEquals(64f, model.height(20, 20));
    }

    @Test
    void flattenMovesTowardTarget() {
        WorldModel model = flat(100);
        try (Edit edit = model.beginEdit("flatten")) {
            TerrainOps.flatten(edit, 50, 50, new Brush(3, Brush.Falloff.CONSTANT), 60, 1);
        }
        assertEquals(60f, model.height(50, 50));
        assertEquals(60f, model.height(52, 50));
        assertEquals(100f, model.height(54, 50));
        try (Edit edit = model.beginEdit("bad")) {
            assertThrows(IllegalArgumentException.class,
                    () -> TerrainOps.flatten(edit, 0, 0, new Brush(1, Brush.Falloff.CONSTANT), 1, 2));
        }
    }

    @Test
    void smoothReducesASpike() {
        WorldModel model = flat(64);
        try (Edit edit = model.beginEdit("spike")) {
            edit.setHeight(30, 30, 100);
        }
        try (Edit edit = model.beginEdit("smooth")) {
            TerrainOps.smooth(edit, 30, 30, new Brush(2, Brush.Falloff.CONSTANT), 1);
        }
        assertEquals(68f, model.height(30, 30), 1e-4);
        assertEquals(68f, model.height(31, 30), 1e-4);
        assertEquals(64f, model.height(33, 30), 1e-4);
    }

    @Test
    void paintOpsRespectThreshold() {
        WorldModel model = flat(64);
        Brush brush = new Brush(4, Brush.Falloff.LINEAR);
        try (Edit edit = model.beginEdit("paint")) {
            TerrainOps.paintTerrain(edit, 10, 10, brush, TerrainType.SAND, 0.5);
            TerrainOps.setWaterLevel(edit, 10, 10, brush, 80, 0.5);
            TerrainOps.paintBiome(edit, 10, 10, brush, "minecraft:beach", 0.5);
        }
        assertEquals(TerrainType.SAND, model.terrain(10, 10));
        assertEquals(TerrainType.GRASS, model.terrain(14, 10));
        assertEquals(80, model.waterLevel(11, 10));
        assertEquals("minecraft:beach", model.biome(10, 10));
        assertEquals("minecraft:plains", model.biome(14, 10));
    }
}
