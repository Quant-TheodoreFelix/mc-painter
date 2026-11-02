package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Arrays;

public sealed interface BrushShape permits BrushShape.Circle, BrushShape.Square, BrushShape.Mask {

    Circle CIRCLE = new Circle();
    Square SQUARE = new Square();

    record Circle() implements BrushShape {
    }

    record Square() implements BrushShape {
    }

    record Mask(int size, byte[] data) implements BrushShape {

        public static final int MAX_SIZE = 512;

        public Mask {
            if (size < 1 || size > MAX_SIZE) {
                throw new IllegalArgumentException("mask size must be in [1, " + MAX_SIZE + "]");
            }
            if (data == null || data.length != size * size) {
                throw new IllegalArgumentException("mask needs " + size * size + " values");
            }
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        public int value(int x, int z) {
            return data[z * size + x] & 0xFF;
        }

        double sample(int dx, int dz, int radius) {
            double span = 2.0 * radius + 1;
            double u = Math.clamp((dx + radius + 0.5) / span * size - 0.5, 0, size - 1);
            double v = Math.clamp((dz + radius + 0.5) / span * size - 0.5, 0, size - 1);
            int x0 = (int) Math.floor(u);
            int z0 = (int) Math.floor(v);
            int x1 = Math.min(x0 + 1, size - 1);
            int z1 = Math.min(z0 + 1, size - 1);
            double fx = u - x0;
            double fz = v - z0;
            double top = value(x0, z0) * (1 - fx) + value(x1, z0) * fx;
            double bottom = value(x0, z1) * (1 - fx) + value(x1, z1) * fx;
            return (top * (1 - fz) + bottom * fz) / 255.0;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Mask mask && mask.size == size && Arrays.equals(mask.data, data);
        }

        @Override
        public int hashCode() {
            return 31 * size + Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "Mask[size=" + size + "]";
        }
    }

    default boolean square() {
        return !(this instanceof Circle);
    }
}
