package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Objects;

public record Brush(int radius, Falloff falloff) {

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
    }

    public double weight(int dx, int dz) {
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (distance > radius + 0.5) {
            return 0;
        }
        if (radius == 0) {
            return 1;
        }
        double t = Math.min(1.0, distance / (radius + 0.5));
        return switch (falloff) {
            case CONSTANT -> 1;
            case LINEAR -> 1 - t;
            case SMOOTH -> 1 - t * t * (3 - 2 * t);
        };
    }
}
