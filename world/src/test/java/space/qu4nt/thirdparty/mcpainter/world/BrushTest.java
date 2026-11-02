package space.qu4nt.thirdparty.mcpainter.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrushTest {

    @Test
    void circleWeightsMatchTheOriginalFormula() {
        for (Brush.Falloff falloff : Brush.Falloff.values()) {
            Brush brush = new Brush(5, falloff);
            assertEquals(BrushShape.CIRCLE, brush.shape());
            for (int dz = -7; dz <= 7; dz++) {
                for (int dx = -7; dx <= 7; dx++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    double t = Math.min(1.0, distance / 5.5);
                    double expected = distance > 5.5 ? 0 : switch (falloff) {
                        case CONSTANT -> 1;
                        case LINEAR -> 1 - t;
                        case SMOOTH -> 1 - t * t * (3 - 2 * t);
                    };
                    assertEquals(expected, brush.weight(dx, dz), 1e-12, falloff + " " + dx + "," + dz);
                }
            }
        }
        assertEquals(1, new Brush(0, Brush.Falloff.LINEAR).weight(0, 0));
        assertEquals(0, new Brush(0, Brush.Falloff.LINEAR).weight(1, 0));
    }

    @Test
    void squareCoversCornersAndUsesSquareDistance() {
        Brush square = new Brush(4, Brush.Falloff.CONSTANT, BrushShape.SQUARE);
        assertEquals(1, square.weight(4, 4));
        assertEquals(0, square.weight(5, 0));
        assertEquals(0, new Brush(4, Brush.Falloff.CONSTANT).weight(4, 4));
        Brush linear = new Brush(4, Brush.Falloff.LINEAR, BrushShape.SQUARE);
        assertEquals(linear.weight(4, 0), linear.weight(4, 3), 1e-12);
        assertTrue(BrushShape.SQUARE.square());
    }

    @Test
    void masksSampleBilinearlyAcrossTheFootprint() {
        byte[] gradient = new byte[4 * 4];
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                gradient[z * 4 + x] = (byte) (x * 85);
            }
        }
        BrushShape.Mask mask = new BrushShape.Mask(4, gradient);
        Brush brush = new Brush(8, Brush.Falloff.CONSTANT, mask);
        assertEquals(0, brush.weight(-8, 0), 1e-9);
        assertEquals(1, brush.weight(8, -8), 1e-9);
        assertEquals(0.5, brush.weight(0, 3), 1e-9);
        assertTrue(brush.weight(-3, 0) < brush.weight(3, 0));
        assertEquals(0, brush.weight(9, 0));

        byte[] dot = new byte[3 * 3];
        dot[4] = (byte) 255;
        Brush tiny = new Brush(0, Brush.Falloff.LINEAR, new BrushShape.Mask(3, dot));
        assertEquals(1, tiny.weight(0, 0), 1e-9);
    }

    @Test
    void masksValidateCopyAndCompareByContent() {
        byte[] data = {1, 2, 3, 4};
        BrushShape.Mask mask = new BrushShape.Mask(2, data);
        data[0] = 9;
        assertEquals(1, mask.value(0, 0));
        mask.data()[1] = 9;
        assertEquals(2, mask.value(1, 0));
        assertEquals(new BrushShape.Mask(2, new byte[] {1, 2, 3, 4}), mask);
        assertEquals(new BrushShape.Mask(2, new byte[] {1, 2, 3, 4}).hashCode(), mask.hashCode());
        assertNotEquals(new BrushShape.Mask(2, new byte[] {1, 2, 3, 5}), mask);
        assertEquals("Mask[size=2]", mask.toString());
        assertThrows(IllegalArgumentException.class, () -> new BrushShape.Mask(2, new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> new BrushShape.Mask(0, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new BrushShape.Mask(BrushShape.Mask.MAX_SIZE + 1,
                        new byte[(BrushShape.Mask.MAX_SIZE + 1) * (BrushShape.Mask.MAX_SIZE + 1)]));
        assertThrows(NullPointerException.class, () -> new Brush(1, Brush.Falloff.LINEAR, null));
    }
}
