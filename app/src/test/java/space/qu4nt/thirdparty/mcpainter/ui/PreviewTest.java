package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.io.BlockCatalog.Block;
import space.qu4nt.thirdparty.mcpainter.io.SurfacePreview;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class PreviewTest {

    private static WorldModel world(int tiles) {
        WorldModel model = WorldModel.builder(-64, 320, 63).generation(new GenerationSettings(3, false)).build();
        try (Edit edit = model.beginEdit("setup")) {
            for (int tx = 0; tx < tiles; tx++) {
                edit.addTile(new TilePos(tx, 0), 70, TerrainType.GRASS);
            }
        }
        return model;
    }

    @Test
    void shadesByTheBlockToTheNorthLikeTheGameMap() {
        WorldModel model = world(1);
        try (Edit edit = model.beginEdit("steps")) {
            edit.setHeight(10, 10, 72);
            edit.setHeight(10, 11, 72);
            edit.setHeight(10, 12, 71);
            edit.setTerrain(20, 20, TerrainType.SAND);
            for (int z = 30; z < 40; z++) {
                for (int x = 30; x < 40; x++) {
                    edit.setLayer(x, z, Layer.CORNFLOWER, Layer.MAX_VALUE);
                }
            }
            for (int z = 40; z < 60; z++) {
                edit.setHeight(5, z, 50);
                edit.setWaterLevel(5, z, 63);
            }
            edit.setHeight(6, 40, 61);
            edit.setWaterLevel(6, 40, 63);
        }
        SurfacePreview.Surface surface = SurfacePreview.render(model, new TilePos(0, 0));
        assertEquals(PreviewRenderer.HIGH, PreviewRenderer.shade(surface, 10, 10));
        assertEquals(PreviewRenderer.NORMAL, PreviewRenderer.shade(surface, 10, 11));
        assertEquals(PreviewRenderer.LOW, PreviewRenderer.shade(surface, 10, 12));
        assertEquals(PreviewRenderer.LOW, PreviewRenderer.shade(surface, 10, 13));
        assertEquals(PreviewRenderer.NORMAL, PreviewRenderer.shade(surface, 50, 0));
        assertEquals(PreviewRenderer.LOW, PreviewRenderer.shade(surface, 5, 41));
        assertEquals(PreviewRenderer.HIGH, PreviewRenderer.shade(surface, 6, 40));
        assertEquals(TileRenderer.multiply(0xF7E9A3, PreviewRenderer.NORMAL), PreviewRenderer.color(surface, 20, 20));
        int fx = -1;
        int fz = -1;
        for (int z = 30; z < 40 && fx < 0; z++) {
            for (int x = 30; x < 40; x++) {
                if (surface.block(x, z) == Block.CORNFLOWER && surface.block(x, z - 1) != Block.CORNFLOWER) {
                    fx = x;
                    fz = z;
                    break;
                }
            }
        }
        assertTrue(fx >= 0);
        assertEquals(71, surface.height(fx, fz));
        assertEquals(TileRenderer.multiply(TileRenderer.layerColor(Layer.CORNFLOWER), PreviewRenderer.HIGH),
                PreviewRenderer.color(surface, fx, fz));
        for (Block block : Block.values()) {
            assertTrue(PreviewRenderer.baseColor(block) >= 0);
        }
        BufferedImage plain = PreviewRenderer.render(surface, model.tile(new TilePos(0, 0)).orElseThrow(), 0, List.of());
        assertEquals(PreviewRenderer.color(surface, 20, 20), plain.getRGB(20, 20) & 0xFFFFFF);
        BufferedImage tinted = PreviewRenderer.render(surface, model.tile(new TilePos(0, 0)).orElseThrow(), 0,
                List.of(Layer.CORNFLOWER));
        assertEquals(TileRenderer.mix(PreviewRenderer.color(surface, fx, fz), TileRenderer.layerColor(Layer.CORNFLOWER),
                TileRenderer.OVERLAY_MIN + TileRenderer.OVERLAY_RANGE), tinted.getRGB(fx, fz) & 0xFFFFFF);
        assertEquals(PreviewRenderer.color(surface, 20, 20), tinted.getRGB(20, 20) & 0xFFFFFF);
        assertEquals(32, PreviewRenderer.render(surface, model.tile(new TilePos(0, 0)).orElseThrow(), 2, List.of())
                .getWidth());
    }

    private static final class Harness {
        final Deque<Runnable> jobs = new ArrayDeque<>();
        final List<TilePos> ready = new ArrayList<>();
        final AtomicInteger renders = new AtomicInteger();
        WorldModel model;
        boolean fail;
        final PreviewCache cache;

        Harness(WorldModel model, int maxInFlight, long maxBytes) {
            this.model = model;
            cache = new PreviewCache(() -> this.model, jobs::add, Runnable::run, (snapshot, pos) -> {
                renders.incrementAndGet();
                if (fail) {
                    throw new IllegalStateException("broken");
                }
                return SurfacePreview.render(snapshot, pos);
            }, ready::add, maxInFlight, maxBytes);
        }

        void runAll() {
            while (!jobs.isEmpty()) {
                jobs.removeFirst().run();
            }
        }
    }

    @Test
    void limitsWorkInFlightAndFeedsTheNextTileWhenOneFinishes() {
        Harness h = new Harness(world(5), 2, PreviewCache.DEFAULT_MAX_BYTES);
        for (int tx = 0; tx < 5; tx++) {
            assertTrue(h.cache.surface(new TilePos(tx, 0)).isEmpty());
        }
        assertEquals(2, h.jobs.size());
        assertEquals(5, h.cache.pendingCount());
        h.jobs.removeFirst().run();
        assertEquals(List.of(new TilePos(0, 0)), h.ready);
        assertEquals(2, h.jobs.size());
        h.cache.keepOnly(Set.of(new TilePos(4, 0)));
        h.runAll();
        assertEquals(List.of(new TilePos(0, 0), new TilePos(1, 0), new TilePos(2, 0), new TilePos(4, 0)), h.ready);
        assertTrue(h.cache.current(new TilePos(4, 0)));
        assertFalse(h.cache.current(new TilePos(3, 0)));
        assertTrue(h.cache.surface(new TilePos(4, 0)).isPresent());
        assertTrue(h.jobs.isEmpty());
    }

    @Test
    void keepsShowingTheOldSurfaceUntilTheEditedOneArrives() {
        Harness h = new Harness(world(2), 4, PreviewCache.DEFAULT_MAX_BYTES);
        TilePos pos = new TilePos(1, 0);
        h.cache.surface(pos);
        h.runAll();
        SurfacePreview.Surface first = h.cache.surface(pos).orElseThrow();
        assertEquals(Block.GRASS_BLOCK, first.block(3, 3));
        try (Edit edit = h.model.beginEdit("sand")) {
            edit.setTerrain(3, 3, TerrainType.SAND);
            edit.setTerrain(131, 3, TerrainType.SAND);
        }
        h.cache.invalidate(List.of(new TilePos(0, 0)));
        assertFalse(h.cache.current(pos));
        assertSame(first, h.cache.surface(pos).orElseThrow());
        assertEquals(1, h.jobs.size());
        h.cache.invalidate(List.of(new TilePos(0, 0)));
        h.runAll();
        assertFalse(h.cache.current(pos));
        assertEquals(Block.SAND, h.cache.surface(pos).orElseThrow().block(3, 3));
        h.runAll();
        assertTrue(h.cache.current(pos));
        int before = h.renders.get();
        h.cache.surface(pos);
        assertEquals(before, h.renders.get());
    }

    @Test
    void dropsResultsAfterClearAndStopsRetryingFailures() {
        Harness h = new Harness(world(1), 4, PreviewCache.DEFAULT_MAX_BYTES);
        TilePos pos = new TilePos(0, 0);
        h.cache.surface(pos);
        h.cache.clear();
        h.runAll();
        assertTrue(h.ready.isEmpty());
        assertEquals(0, h.cache.size());
        h.fail = true;
        h.cache.surface(pos);
        h.runAll();
        h.cache.surface(pos);
        h.cache.surface(pos);
        assertTrue(h.jobs.isEmpty());
        assertEquals(2, h.renders.get());
        h.fail = false;
        h.cache.invalidate(List.of(pos));
        h.cache.surface(pos);
        h.runAll();
        assertTrue(h.cache.current(pos));
        assertTrue(h.cache.surface(new TilePos(9, 9)).isEmpty());
        h.runAll();
        assertEquals(0, h.cache.pendingCount());
    }

    @Test
    void evictsTheOldestSurfacesBeyondTheByteLimit() {
        Harness h = new Harness(world(4), 4, 2 * SurfacePreview.Surface.BYTES);
        for (int tx = 0; tx < 4; tx++) {
            h.cache.surface(new TilePos(tx, 0));
        }
        h.runAll();
        assertEquals(2, h.cache.size());
        assertTrue(h.cache.current(new TilePos(3, 0)));
        assertFalse(h.cache.current(new TilePos(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new PreviewCache(() -> null, r -> { }, r -> { },
                (m, p) -> null, p -> { }, 0, PreviewCache.DEFAULT_MAX_BYTES));
    }
}
