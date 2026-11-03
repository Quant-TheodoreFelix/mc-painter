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
        assertEquals(-3, view.zoom(), 1e-9);
        assertEquals(3, view.lod());
        assertEquals(13, view.percent());
    }

    @Test
    void zoomsInFractionalStepsAndSnapsMenuSteps() {
        Viewport view = new Viewport();
        view.centerOn(0, 0, 800, 600);
        double before = view.worldX(200);
        assertTrue(view.zoomAt(-0.2, 200, 100));
        assertEquals(-0.2, view.zoom(), 1e-9);
        assertEquals(0, view.lod());
        assertEquals(87, view.percent());
        assertEquals(before, view.worldX(200), 1e-9);
        assertTrue(view.zoomAt(-1, 200, 100));
        assertEquals(1, view.lod());
        assertTrue(view.zoomInAt(0, 0));
        assertEquals(-1, view.zoom(), 1e-9);
        assertTrue(view.zoomInAt(0, 0));
        assertEquals(0, view.zoom(), 1e-9);
        assertTrue(view.zoomAt(0.3, 0, 0));
        assertTrue(view.zoomOutAt(0, 0));
        assertEquals(0, view.zoom(), 1e-9);
        assertFalse(view.zoomAt(1e-9, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> view.zoomAt(Double.NaN, 0, 0));
    }

    @Test
    void clampsZoomAndOrigin() {
        Viewport view = new Viewport();
        assertTrue(view.setZoom(99, 0, 0));
        assertEquals(Viewport.MAX_ZOOM, view.zoom(), 1e-9);
        assertFalse(view.zoomAt(1, 0, 0));
        assertFalse(view.zoomInAt(0, 0));
        view.setZoom(Viewport.MIN_ZOOM, 0, 0);
        assertEquals(Viewport.MIN_ZOOM, view.zoom(), 1e-9);
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
        assertEquals(0, view.zoom(), 1e-9);
        assertEquals(0, view.worldX(400), 1e-9);
        view.fit(0, 0, 128, 128, 800, 600);
        assertEquals(Viewport.MAX_FIT_ZOOM, view.zoom(), 1e-9);
        view.fit(-100_000, 0, 100_000, 10, 800, 600);
        assertEquals(-9, view.zoom(), 1e-9);
        view.fit(-30_000_000, 0, 30_000_000, 10, 800, 600);
        assertEquals(Viewport.MIN_ZOOM, view.zoom(), 1e-9);
    }
}
