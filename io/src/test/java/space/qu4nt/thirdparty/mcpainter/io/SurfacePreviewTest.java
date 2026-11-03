package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.io.BlockCatalog.Block;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class SurfacePreviewTest {

    private static WorldModel mixedWorld() {
        WorldModel model = WorldModel.builder(-64, 320, 63).generation(new GenerationSettings(91, true)).build();
        try (Edit edit = model.beginEdit("setup")) {
            for (int tz = 0; tz < 2; tz++) {
                for (int tx = 0; tx < 2; tx++) {
                    edit.addTile(new TilePos(tx, tz), 70, TerrainType.GRASS);
                }
            }
            edit.fillLayer(new TilePos(0, 0), Layer.OAK_TREES, Layer.MAX_VALUE);
            edit.fillLayer(new TilePos(1, 0), Layer.POPPY, Layer.MAX_VALUE);
            edit.fillLayer(new TilePos(1, 0), Layer.PEONY, Layer.MAX_VALUE / 2);
            edit.fillLayer(new TilePos(1, 0), Layer.GRASS, Layer.MAX_VALUE);
            for (int z = 128; z < 256; z++) {
                for (int x = 0; x < 128; x++) {
                    edit.setTerrain(x, z, TerrainType.STONE);
                    edit.setHeight(x, z, 70 + (x + z) % 17);
                }
                for (int x = 128; x < 256; x++) {
                    edit.setTerrain(x, z, TerrainType.SAND);
                    edit.setHeight(x, z, 55 + (x % 9));
                }
            }
            for (int x = 0; x < 256; x++) {
                edit.setHeight(x, 127, 72);
                edit.setLayer(x, 126, Layer.OAK_TREES, Layer.MAX_VALUE);
            }
        }
        return model;
    }

    private record Top(Block block, int y) {
    }

    private static Map<Long, Top> scan(WorldModel model) {
        BlockCatalog catalog = new BlockCatalog(SurfacePreview.DATA_VERSION);
        BlockStateEntry air = catalog.state(Block.AIR);
        Map<Long, Top> tops = new HashMap<>();
        for (int cz = 0; cz < 16; cz++) {
            for (int cx = 0; cx < 16; cx++) {
                WorldExporter.ChunkColumns columns = new WorldExporter.ChunkColumns(model, catalog, cx, cz,
                        model.minY(), model.maxY());
                ChunkFeatures features = ChunkFeatures.generate(model, catalog, columns::blockAt, cx, cz,
                        model.minY(), model.maxY());
                for (int z = cz * 16; z < cz * 16 + 16; z++) {
                    for (int x = cx * 16; x < cx * 16 + 16; x++) {
                        for (int y = model.maxY() - 1; y > model.minY(); y--) {
                            BlockStateEntry state = features.at(x, y, z);
                            if (state == null) {
                                state = columns.blockAt(x, y, z);
                            }
                            if (!state.equals(air)) {
                                tops.put(((long) x << 32) | (z & 0xFFFFFFFFL),
                                        new Top(catalog.block(state).orElseThrow(), y));
                                break;
                            }
                        }
                    }
                }
            }
        }
        return tops;
    }

    @Test
    void topBlocksMatchTheExportedColumns() {
        WorldModel model = mixedWorld();
        Map<Long, Top> expected = scan(model);
        Map<Block, Integer> seen = new HashMap<>();
        for (TilePos pos : model.tilePositions()) {
            SurfacePreview.Surface surface = SurfacePreview.render(model, pos);
            for (int z = -1; z < Tile.SIZE; z++) {
                if (!surface.known(0, z)) {
                    assertEquals(-1, z);
                    assertEquals(0, pos.z());
                    continue;
                }
                for (int x = 0; x < Tile.SIZE; x++) {
                    int wx = pos.minBlockX() + x;
                    int wz = pos.minBlockZ() + z;
                    Top top = expected.get(((long) wx << 32) | (wz & 0xFFFFFFFFL));
                    assertEquals(top, new Top(surface.block(x, z), surface.height(x, z)), wx + "," + wz);
                    seen.merge(top.block(), 1, Integer::sum);
                }
            }
        }
        for (Block block : List.of(Block.OAK_LEAVES, Block.POPPY, Block.PEONY, Block.SHORT_GRASS, Block.WATER,
                Block.STONE, Block.COAL_ORE, Block.SAND, Block.GRASS_BLOCK)) {
            assertTrue(seen.containsKey(block), block + " in " + seen);
        }
    }

    @Test
    void snapshotsOfTheNeighbourhoodGiveTheSameSurface() {
        WorldModel model = mixedWorld();
        for (TilePos pos : model.tilePositions()) {
            List<TilePos> around = new ArrayList<>();
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    around.add(new TilePos(pos.x() + dx, pos.z() + dz));
                }
            }
            SurfacePreview.Surface full = SurfacePreview.render(model, pos);
            SurfacePreview.Surface local = SurfacePreview.render(model.snapshot(around), pos);
            for (int z = -1; z < Tile.SIZE; z++) {
                assertEquals(full.known(0, z), local.known(0, z));
                if (!full.known(0, z)) {
                    continue;
                }
                for (int x = 0; x < Tile.SIZE; x++) {
                    assertEquals(full.block(x, z), local.block(x, z), pos + " " + x + "," + z);
                    assertEquals(full.height(x, z), local.height(x, z));
                    assertEquals(full.waterDepth(x, z), local.waterDepth(x, z));
                }
            }
        }
    }

    @Test
    void reportsWaterDepthAndTheRowNorthOfTheTile() {
        WorldModel model = mixedWorld();
        SurfacePreview.Surface lake = SurfacePreview.render(model, new TilePos(1, 1));
        assertEquals(Block.WATER, lake.block(7, 10));
        assertEquals(62, lake.height(7, 10));
        assertEquals(62 - 55, lake.waterDepth(7, 10));
        assertEquals(62 - 57, lake.waterDepth(0, 10));
        assertEquals(Block.SAND, lake.block(6, 10));
        assertEquals(63, lake.height(6, 10));
        assertEquals(0, lake.waterDepth(6, 10));
        assertTrue(lake.known(5, -1));
        SurfacePreview.Surface top = SurfacePreview.render(model, new TilePos(0, 0));
        assertFalse(top.known(5, -1));
        assertThrows(IllegalStateException.class, () -> top.block(5, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> top.block(5, -2));
        assertThrows(IllegalArgumentException.class, () -> SurfacePreview.render(model, new TilePos(7, 7)));
    }
}
