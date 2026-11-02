package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Objects;

public record Brush(int radius, Falloff falloff, BrushShape shape) {

    public static final int MAX_RADIUS = 1024;

    public enum Falloff {
        CONSTANT,
        LINEAR,
        SMOOTH
    }

    public Brush {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("brush radius must be in [0, " + MAX_RADIUS + "]");
        }
        Objects.requireNonNull(falloff, "falloff");
        Objects.requireNonNull(shape, "shape");
    }

    public Brush(int radius, Falloff falloff) {
        this(radius, falloff, BrushShape.CIRCLE);
    }

    public double weight(int dx, int dz) {
        double distance;
        if (shape.square()) {
            if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
                return 0;
            }
            distance = Math.max(Math.abs(dx), Math.abs(dz));
        } else {
            distance = Math.sqrt((double) dx * dx + (double) dz * dz);
            if (distance > radius + 0.5) {
                return 0;
            }
        }
        double strength = radius == 0 ? 1 : falloff(Math.min(1.0, distance / (radius + 0.5)));
        return shape instanceof BrushShape.Mask mask ? strength * mask.sample(dx, dz, radius) : strength;
    }

    private double falloff(double t) {
        return switch (falloff) {
            case CONSTANT -> 1;
            case LINEAR -> 1 - t;
            case SMOOTH -> 1 - t * t * (3 - 2 * t);
        };
    }
}
