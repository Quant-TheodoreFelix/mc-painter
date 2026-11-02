package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewportTest {

    @Test
    void convertsBetweenScreenAndWorld() {
        Viewport view = new Viewport();
        view.centerOn(100, -50, 800, 600);
        assertEquals(100, view.worldX(400), 1e-9);
        assertEquals(-50, view.worldZ(300), 1e-9);
        assertEquals(400, view.screenX(100), 1e-9);
        assertEquals(17.25, view.worldX(view.screenX(17.25)), 1e-9);
    }

    @Test
    void zoomKeepsTheAnchorUnderTheCursor() {
        Viewport view = new Viewport();
        view.centerOn(0, 0, 800, 600);
        double before = view.worldX(123);
        double beforeZ = view.worldZ(456);
        assertTrue(view.zoomAt(2, 123, 456));
        assertEquals(4, view.scale());
        assertEquals(before, view.worldX(123), 1e-9);
        assertEquals(beforeZ, view.worldZ(456), 1e-9);
        assertTrue(view.zoomAt(-5, 10, 10));
        assertEquals(-3, view.zoom());
        assertEquals(3, view.lod());
        assertEquals(13, view.percent());
    }

    @Test
    void clampsZoomAndOrigin() {
        Viewport view = new Viewport();
        assertTrue(view.setZoom(99, 0, 0));
        assertEquals(Viewport.MAX_ZOOM, view.zoom());
        assertFalse(view.zoomAt(1, 0, 0));
        view.setZoom(Viewport.MIN_ZOOM, 0, 0);
        assertEquals(Viewport.MIN_ZOOM, view.zoom());
        view.pan(-1e12, 1e12);
        assertEquals(Viewport.WORLD_LIMIT, view.originX());
        assertEquals(-Viewport.WORLD_LIMIT, view.originZ());
        assertThrows(IllegalArgumentException.class, () -> view.pan(Double.NaN, 0));
    }

    @Test
    void panMovesByScreenPixels() {
        Viewport view = new Viewport();
        view.setZoom(1, 0, 0);
        double x = view.originX();
        view.pan(10, 0);
        assertEquals(x - 5, view.originX(), 1e-9);
    }

    @Test
    void fitChoosesLargestZoomThatShowsEverything() {
        Viewport view = new Viewport();
        view.fit(-256, -256, 256, 256, 800, 600);
        assertEquals(0, view.zoom());
        assertEquals(0, view.worldX(400), 1e-9);
        view.fit(0, 0, 128, 128, 800, 600);
        assertEquals(Viewport.MAX_FIT_ZOOM, view.zoom());
        view.fit(-100_000, 0, 100_000, 10, 800, 600);
        assertEquals(Viewport.MIN_ZOOM, view.zoom());
    }
}
