package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class TileRendererTest {

    private static WorldModel flat() {
        WorldModel model = WorldModel.create(-64, 320, 63);
        try (Edit edit = model.beginEdit("setup")) {
            edit.addTile(new TilePos(0, 0), 64, TerrainType.GRASS);
        }
        return model;
    }

    private static int elevated(TerrainType type, double height) {
        return TileRenderer.multiply(TileRenderer.terrainColor(type), 0.85 + 0.3 * (height + 64) / 384.0);
    }

    @Test
    void flatLandUsesTerrainColorWithElevation() {
        WorldModel model = flat();
        BufferedImage image = TileRenderer.render(model, new TilePos(0, 0), 0);
        assertEquals(128, image.getWidth());
        assertEquals(elevated(TerrainType.GRASS, 64), image.getRGB(10, 10) & 0xFFFFFF);
        try (Edit edit = model.beginEdit("snow")) {
            edit.setTerrain(20, 20, TerrainType.SNOW);
        }
        image = TileRenderer.render(model, new TilePos(0, 0), 0);
        assertEquals(elevated(TerrainType.SNOW, 64), image.getRGB(20, 20) & 0xFFFFFF);
    }

    @Test
    void waterMatchesExportedWaterColumns() {
        WorldModel model = flat();
        try (Edit edit = model.beginEdit("water")) {
            edit.setWaterLevel(5, 5, 70);
            edit.setWaterLevel(6, 5, 66);
            edit.setWaterLevel(7, 5, 65);
        }
        BufferedImage image = TileRenderer.render(model, new TilePos(0, 0), 0);
        int ground = TileRenderer.terrainColor(TerrainType.GRASS);
        assertEquals(TileRenderer.waterColor(ground, 5), image.getRGB(5, 5) & 0xFFFFFF);
        assertEquals(TileRenderer.waterColor(ground, 1), image.getRGB(6, 5) & 0xFFFFFF);
        assertEquals(elevated(TerrainType.GRASS, 64), image.getRGB(7, 5) & 0xFFFFFF);
        int deep = TileRenderer.waterColor(ground, 5);
        assertTrue((deep & 0xFF) > ((deep >> 16) & 0xFF));
    }

    @Test
    void slopesFacingTheLightAreBrighter() {
        WorldModel model = flat();
        try (Edit edit = model.beginEdit("ramp")) {
            for (int z = 0; z < 128; z++) {
                for (int x = 30; x < 60; x++) {
                    edit.setHeight(x, z, 64 + (x - 30));
                    edit.setHeight(x + 40, z, 94 - (x - 30));
                }
            }
        }
        BufferedImage image = TileRenderer.render(model, new TilePos(0, 0), 0);
        int flat = image.getRGB(10, 64) & 0xFF;
        int lit = image.getRGB(40, 64) & 0xFF;
        int dark = image.getRGB(80, 64) & 0xFF;
        assertTrue(lit > flat, lit + " > " + flat);
        assertTrue(dark < flat, dark + " < " + flat);
    }

    @Test
    void shadingReadsNeighbourTilesAndLodShrinksImages() {
        WorldModel model = flat();
        try (Edit edit = model.beginEdit("neighbour")) {
            edit.addTile(new TilePos(1, 0), 100, TerrainType.GRASS);
        }
        BufferedImage image = TileRenderer.render(model, new TilePos(0, 0), 0);
        assertTrue((image.getRGB(127, 64) & 0xFF) > (image.getRGB(64, 64) & 0xFF));
        for (int lod = 0; lod <= TileRenderer.MAX_LOD; lod++) {
            assertEquals(128 >> lod, TileRenderer.render(model, new TilePos(1, 0), lod).getHeight());
        }
        assertThrows(IllegalArgumentException.class, () -> TileRenderer.render(model, new TilePos(5, 5), 0));
        assertThrows(IndexOutOfBoundsException.class, () -> TileRenderer.render(model, new TilePos(0, 0), 5));
    }

    @Test
    void layerOverlayTintsByValueAndHasItsOwnCacheEntry() {
        WorldModel model = flat();
        try (Edit edit = model.beginEdit("trees")) {
            edit.setLayer(10, 10, Layer.OAK_TREES, Layer.MAX_VALUE);
            edit.setLayer(11, 10, Layer.OAK_TREES, 40);
        }
        BufferedImage plain = TileRenderer.render(model, new TilePos(0, 0), 0);
        BufferedImage tinted = TileRenderer.render(model, new TilePos(0, 0), 0, Layer.OAK_TREES);
        int base = plain.getRGB(10, 10) & 0xFFFFFF;
        assertEquals(base, tinted.getRGB(30, 30) & 0xFFFFFF);
        int full = tinted.getRGB(10, 10) & 0xFFFFFF;
        int light = tinted.getRGB(11, 10) & 0xFFFFFF;
        assertEquals(TileRenderer.mix(base, TileRenderer.layerColor(Layer.OAK_TREES),
                TileRenderer.OVERLAY_MIN + TileRenderer.OVERLAY_RANGE), full);
        assertTrue(full != light && light != base);
        TileImageCache cache = new TileImageCache(TileImageCache.DEFAULT_MAX_BYTES);
        BufferedImage none = cache.image(model, new TilePos(0, 0), 0);
        BufferedImage trees = cache.image(model, new TilePos(0, 0), 0, Layer.OAK_TREES);
        assertTrue(none != trees);
        assertSame(trees, cache.image(model, new TilePos(0, 0), 0, Layer.OAK_TREES));
        cache.invalidate(List.of(new TilePos(0, 0)));
        assertEquals(0, cache.size());
    }

    @Test
    void cacheReusesInvalidatesAndEvicts() {
        WorldModel model = flat();
        try (Edit edit = model.beginEdit("more")) {
            edit.addTile(new TilePos(1, 0), 64, TerrainType.SAND);
            edit.addTile(new TilePos(2, 0), 64, TerrainType.STONE);
        }
        TileImageCache cache = new TileImageCache(2L * 128 * 128 * 4);
        BufferedImage first = cache.image(model, new TilePos(0, 0), 0);
        assertSame(first, cache.image(model, new TilePos(0, 0), 0));
        cache.image(model, new TilePos(0, 0), 1);
        assertEquals(2, cache.size());
        cache.invalidate(List.of(new TilePos(0, 0)));
        assertEquals(0, cache.size());
        assertEquals(0, cache.bytes());

        cache.image(model, new TilePos(0, 0), 0);
        cache.image(model, new TilePos(1, 0), 0);
        cache.image(model, new TilePos(0, 0), 0);
        cache.image(model, new TilePos(2, 0), 0);
        assertEquals(2, cache.size());
        assertSame(cache.image(model, new TilePos(0, 0), 0), cache.image(model, new TilePos(0, 0), 0));
        assertEquals(2L * 128 * 128 * 4, cache.bytes());
        cache.clear();
        assertEquals(0, cache.bytes());
    }
}
