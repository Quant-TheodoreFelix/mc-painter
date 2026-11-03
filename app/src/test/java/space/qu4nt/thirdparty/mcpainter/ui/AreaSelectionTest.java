package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;

class AreaSelectionTest {

    private static final TileRect BOUNDS = new TileRect(-10, -10, 9, 9);

    @Test
    void createsMovesAndResizesInsideTheBounds() {
        AreaSelection selection = new AreaSelection();
        selection.press(new TilePos(2, 3), 0, BOUNDS);
        assertEquals(AreaSelection.Drag.CREATE, selection.drag());
        selection.dragTo(new TilePos(40, -2), BOUNDS);
        selection.release();
        assertFalse(selection.dragging());
        assertEquals(Optional.of(new TileRect(2, -2, 9, 3)), selection.rect());

        selection.press(new TilePos(5, 0), 0, BOUNDS);
        assertEquals(AreaSelection.Drag.MOVE, selection.drag());
        selection.dragTo(new TilePos(-50, 0), BOUNDS);
        assertEquals(Optional.of(new TileRect(-10, -2, -3, 3)), selection.rect());
        selection.release();

        selection.press(new TilePos(-3, 0), AreaSelection.EAST | AreaSelection.SOUTH, BOUNDS);
        selection.dragTo(new TilePos(0, 7), BOUNDS);
        assertEquals(Optional.of(new TileRect(-10, -2, 0, 7)), selection.rect());
        selection.dragTo(new TilePos(-20, -20), BOUNDS);
        assertEquals(Optional.of(new TileRect(-10, -2, -10, -2)), selection.rect());
        selection.release();

        selection.press(new TilePos(50, 50), 0, BOUNDS);
        assertFalse(selection.dragging());
    }

    @Test
    void findsEdgesNearThePointer() {
        assertEquals(AreaSelection.WEST, AreaSelection.edgesNear(102, 150, 100, 100, 300, 200, 6));
        assertEquals(AreaSelection.EAST | AreaSelection.SOUTH,
                AreaSelection.edgesNear(298, 203, 100, 100, 300, 200, 6));
        assertEquals(0, AreaSelection.edgesNear(200, 150, 100, 100, 300, 200, 6));
        assertEquals(0, AreaSelection.edgesNear(90, 300, 100, 100, 300, 200, 6));
        assertTrue((AreaSelection.edgesNear(100, 100, 100, 100, 300, 200, 6) & AreaSelection.NORTH) != 0);
    }
}
