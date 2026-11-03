package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;

final class AreaSelection {

    static final int WEST = 1;
    static final int EAST = 2;
    static final int NORTH = 4;
    static final int SOUTH = 8;

    enum Drag {
        NONE,
        CREATE,
        MOVE,
        RESIZE
    }

    private TileRect rect;
    private Drag drag = Drag.NONE;
    private int edges;
    private TilePos anchor;
    private TileRect start;

    Optional<TileRect> rect() {
        return Optional.ofNullable(rect);
    }

    void set(TileRect value) {
        rect = value;
        drag = Drag.NONE;
    }

    Drag drag() {
        return drag;
    }

    boolean dragging() {
        return drag != Drag.NONE;
    }

    void press(TilePos tile, int edgesHit, TileRect bounds) {
        anchor = tile;
        start = rect;
        if (rect != null && edgesHit != 0) {
            drag = Drag.RESIZE;
            edges = edgesHit;
        } else if (rect != null && rect.contains(tile)) {
            drag = Drag.MOVE;
        } else if (bounds.contains(tile)) {
            drag = Drag.CREATE;
            rect = new TileRect(tile.x(), tile.z(), tile.x(), tile.z());
        } else {
            drag = Drag.NONE;
        }
    }

    void dragTo(TilePos tile, TileRect bounds) {
        switch (drag) {
            case CREATE -> rect = TileRect.of(anchor, clamp(tile, bounds));
            case MOVE -> {
                long dx = (long) tile.x() - anchor.x();
                long dz = (long) tile.z() - anchor.z();
                TileRect shifted = new TileRect(shift(start.minX(), dx), shift(start.minZ(), dz),
                        shift(start.maxX(), dx), shift(start.maxZ(), dz));
                rect = shifted.width() == start.width() && shifted.depth() == start.depth()
                        ? shifted.clampInto(bounds) : start;
            }
            case RESIZE -> {
                TilePos at = clamp(tile, bounds);
                int minX = (edges & WEST) != 0 ? Math.min(at.x(), start.maxX()) : start.minX();
                int maxX = (edges & EAST) != 0 ? Math.max(at.x(), start.minX()) : start.maxX();
                int minZ = (edges & NORTH) != 0 ? Math.min(at.z(), start.maxZ()) : start.minZ();
                int maxZ = (edges & SOUTH) != 0 ? Math.max(at.z(), start.minZ()) : start.maxZ();
                rect = new TileRect(minX, minZ, maxX, maxZ);
            }
            case NONE -> {
            }
        }
    }

    void release() {
        drag = Drag.NONE;
    }

    static int edgesNear(double mouseX, double mouseZ, double left, double top, double right, double bottom,
            double tolerance) {
        boolean insideX = mouseX >= left - tolerance && mouseX <= right + tolerance;
        boolean insideZ = mouseZ >= top - tolerance && mouseZ <= bottom + tolerance;
        int found = 0;
        if (insideZ && Math.abs(mouseX - left) <= tolerance) {
            found |= WEST;
        }
        if (insideZ && Math.abs(mouseX - right) <= tolerance) {
            found |= EAST;
        }
        if (insideX && Math.abs(mouseZ - top) <= tolerance) {
            found |= NORTH;
        }
        if (insideX && Math.abs(mouseZ - bottom) <= tolerance) {
            found |= SOUTH;
        }
        return found;
    }

    private static TilePos clamp(TilePos tile, TileRect bounds) {
        return new TilePos(Math.clamp(tile.x(), bounds.minX(), bounds.maxX()),
                Math.clamp(tile.z(), bounds.minZ(), bounds.maxZ()));
    }

    private static int shift(int value, long delta) {
        return Math.clamp(value + delta, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
