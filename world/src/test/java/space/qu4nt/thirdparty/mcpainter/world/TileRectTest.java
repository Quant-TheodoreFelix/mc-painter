package space.qu4nt.thirdparty.mcpainter.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TileRectTest {

    @Test
    void worldCoversTheVanillaBorder() {
        assertEquals(new TileRect(-234_375, -234_375, 234_374, 234_374), TileRect.WORLD);
        assertTrue(TileRect.WORLD.minBlockX() <= -TileRect.WORLD_BLOCK_LIMIT);
        assertTrue(TileRect.WORLD.maxBlockX() >= TileRect.WORLD_BLOCK_LIMIT - 1);
        assertEquals(468_750L * 468_750L, TileRect.WORLD.count());
    }

    @Test
    void centeredSizesRoundUpToWholeTiles() {
        assertEquals(new TileRect(-2, -2, 1, 1), TileRect.centered(4, 4));
        assertEquals(new TileRect(-1, -1, 1, 0), TileRect.centered(3, 2));
        TileRect blocks = TileRect.centeredBlocks(80_000, 80_000);
        assertEquals(625, blocks.width());
        assertEquals(new TileRect(-312, -312, 312, 312), blocks);
        assertEquals(2, TileRect.centeredBlocks(129, 1).width());
        assertEquals(1, TileRect.centeredBlocks(129, 1).depth());
        assertEquals(TileRect.WORLD, TileRect.centered(TileRect.WORLD.width(), TileRect.WORLD.depth()));
        assertThrows(IllegalArgumentException.class, () -> TileRect.centered(0, 4));
        assertThrows(IllegalArgumentException.class, () -> TileRect.centered(TileRect.WORLD.width() + 1L, 4));
    }

    @Test
    void geometryHelpers() {
        TileRect rect = TileRect.ofBlocks(130, -1, -5, 300);
        assertEquals(new TileRect(-1, -1, 1, 2), rect);
        assertEquals(-128, rect.minBlockX());
        assertEquals(383, rect.maxBlockZ());
        assertTrue(rect.contains(new TilePos(1, 2)));
        assertFalse(rect.contains(new TilePos(2, 2)));
        assertEquals(new TileRect(-2, -2, 2, 3), rect.expand(1));
        assertEquals(Optional.of(new TileRect(0, 0, 1, 2)), rect.intersect(new TileRect(0, 0, 5, 5)));
        assertTrue(rect.intersect(new TileRect(5, 5, 6, 6)).isEmpty());
        assertEquals(new TileRect(3, 4, 5, 7), rect.clampInto(new TileRect(3, 4, 10, 10)));
        assertEquals(new TileRect(0, 0, 1, 1), new TileRect(-5, -5, 5, 5).clampInto(new TileRect(0, 0, 1, 1)));
        TileRect edge = new TileRect(Integer.MAX_VALUE - 1, 0, Integer.MAX_VALUE, 0).expand(5);
        assertEquals(Integer.MAX_VALUE, edge.maxX());
        assertThrows(IllegalArgumentException.class, () -> new TileRect(1, 0, 0, 0));
    }
}
