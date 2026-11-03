package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Objects;

public final class TerrainOps {

    private TerrainOps() {
    }

    @FunctionalInterface
    private interface ColumnAction {
        void apply(int x, int z, double weight);
    }

    private static void forEachColumn(Edit edit, int centerX, int centerZ, Brush brush, ColumnAction action) {
        Objects.requireNonNull(edit, "edit");
        Objects.requireNonNull(brush, "brush");
        WorldModel model = edit.model();
        int r = brush.radius();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double weight = brush.weight(dx, dz);
                int x = centerX + dx;
                int z = centerZ + dz;
                if (weight > 0 && model.contains(x, z)) {
                    action.apply(x, z, weight);
                }
            }
        }
    }

    public static void raise(Edit edit, int centerX, int centerZ, Brush brush, float amount) {
        requireFinite(amount, "amount");
        WorldModel model = edit.model();
        forEachColumn(edit, centerX, centerZ, brush,
                (x, z, w) -> edit.setHeight(x, z, (float) (model.height(x, z) + amount * w)));
    }

    public static void flatten(Edit edit, int centerX, int centerZ, Brush brush, float target, float strength) {
        requireFinite(target, "target");
        float s = requireStrength(strength);
        WorldModel model = edit.model();
        forEachColumn(edit, centerX, centerZ, brush, (x, z, w) -> {
            float h = model.height(x, z);
            edit.setHeight(x, z, (float) (h + (target - h) * s * w));
        });
    }

    public static void smooth(Edit edit, int centerX, int centerZ, Brush brush, float strength) {
        float s = requireStrength(strength);
        WorldModel model = edit.model();
        int r = brush.radius();
        int size = 2 * r + 3;
        float[] heights = new float[size * size];
        boolean[] present = new boolean[size * size];
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int i = (dz + r + 1) * size + (dx + r + 1);
                if (model.contains(x, z)) {
                    heights[i] = model.height(x, z);
                    present[i] = true;
                }
            }
        }
        forEachColumn(edit, centerX, centerZ, brush, (x, z, w) -> {
            int cx = x - centerX + r + 1;
            int cz = z - centerZ + r + 1;
            double sum = 0;
            int count = 0;
            for (int oz = -1; oz <= 1; oz++) {
                for (int ox = -1; ox <= 1; ox++) {
                    int i = (cz + oz) * size + (cx + ox);
                    if (present[i]) {
                        sum += heights[i];
                        count++;
                    }
                }
            }
            float h = heights[cz * size + cx];
            double average = sum / count;
            edit.setHeight(x, z, (float) (h + (average - h) * s * w));
        });
    }

    public static void paintTerrain(Edit edit, int centerX, int centerZ, Brush brush, TerrainType type,
            double threshold) {
        Objects.requireNonNull(type, "type");
        forEachColumn(edit, centerX, centerZ, brush, (x, z, w) -> {
            if (w >= threshold) {
                edit.setTerrain(x, z, type);
            }
        });
    }

    public static void setWaterLevel(Edit edit, int centerX, int centerZ, Brush brush, int level, double threshold) {
        forEachColumn(edit, centerX, centerZ, brush, (x, z, w) -> {
            if (w >= threshold) {
                edit.setWaterLevel(x, z, level);
            }
        });
    }

    public static void paintBiome(Edit edit, int centerX, int centerZ, Brush brush, String biomeId,
            double threshold) {
        WorldModel.requireBiomeId(biomeId);
        forEachColumn(edit, centerX, centerZ, brush, (x, z, w) -> {
            if (w >= threshold) {
                edit.setBiome(x, z, biomeId);
            }
        });
    }

    public static void sprayLayer(Edit edit, int centerX, int centerZ, Brush brush, Layer layer, float amount) {
        Objects.requireNonNull(layer, "layer");
        if (!(amount >= -1 && amount <= 1)) {
            throw new IllegalArgumentException("amount must be in [-1, 1]");
        }
        WorldModel model = edit.model();
        forEachColumn(edit, centerX, centerZ, brush, (x, z, w) -> {
            double delta = amount * w * Layer.MAX_VALUE;
            long step = Math.round(delta);
            if (step == 0 && delta != 0) {
                step = delta > 0 ? 1 : -1;
            }
            edit.setLayer(x, z, layer, Math.clamp(model.layer(x, z, layer) + step, 0, Layer.MAX_VALUE));
        });
    }

    private static void requireFinite(float value, String what) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(what + " must be finite");
        }
    }

    private static float requireStrength(float strength) {
        if (!(strength >= 0 && strength <= 1)) {
            throw new IllegalArgumentException("strength must be in [0, 1]");
        }
        return strength;
    }
}
