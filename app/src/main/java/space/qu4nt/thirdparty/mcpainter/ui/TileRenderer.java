package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class TileRenderer {

    static final int MAX_LOD = 4;
    static final int SHADE_MARGIN = 1 << MAX_LOD;

    static final int WATER_SHALLOW = 0x3F76E4;
    static final int WATER_DEEP = 0x14306E;
    static final int WATER_FULL_DEPTH = 24;

    private static final double SLOPE_GAIN = 0.6;
    private static final double SLOPE_LIMIT = 0.45;

    private TileRenderer() {
    }

    static int terrainColor(TerrainType type) {
        return switch (type) {
            case GRASS -> 0x6FA33F;
            case DIRT -> 0x8B6542;
            case STONE -> 0x7D7D7D;
            case SAND -> 0xE3D7A3;
            case GRAVEL -> 0x8F8580;
            case SNOW -> 0xF2F6FA;
        };
    }

    static BufferedImage render(WorldModel model, TilePos pos, int lod) {
        Objects.checkIndex(lod, MAX_LOD + 1);
        Tile tile = model.tile(pos).orElseThrow(() -> new IllegalArgumentException("no tile at " + pos));
        int step = 1 << lod;
        int size = Tile.SIZE >> lod;
        int half = step >> 1;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Sampler sampler = new Sampler(model, tile, pos);
        double range = model.maxY() - model.minY();
        for (int iz = 0; iz < size; iz++) {
            int lz = iz * step + half;
            for (int ix = 0; ix < size; ix++) {
                int lx = ix * step + half;
                int index = Tile.index(lx, lz);
                float height = tile.height(index);
                int top = Math.clamp((int) Math.floor(height), model.minY() + 1, model.maxY() - 1);
                int depth = tile.waterLevel(index) - 1 - top;
                int ground = terrainColor(tile.terrain(index));
                int color;
                if (depth > 0) {
                    color = waterColor(ground, depth);
                } else {
                    double gx = (sampler.height(lx + step, lz, height) - sampler.height(lx - step, lz, height))
                            / (2.0 * step);
                    double gz = (sampler.height(lx, lz + step, height) - sampler.height(lx, lz - step, height))
                            / (2.0 * step);
                    double shade = 1 + Math.clamp((gx + gz) * SLOPE_GAIN, -SLOPE_LIMIT, SLOPE_LIMIT);
                    double elevation = 0.85 + 0.3 * Math.clamp((height - model.minY()) / range, 0.0, 1.0);
                    color = multiply(ground, shade * elevation);
                }
                pixels[iz * size + ix] = color;
            }
        }
        return image;
    }

    static int waterColor(int ground, int depth) {
        double t = Math.min(1.0, depth / (double) WATER_FULL_DEPTH);
        int water = mix(WATER_SHALLOW, WATER_DEEP, t);
        return switch (depth) {
            case 1 -> mix(ground, water, 0.6);
            case 2 -> mix(ground, water, 0.8);
            default -> water;
        };
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int multiply(int rgb, double factor) {
        int r = (int) Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = (int) Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = (int) Math.min(255, Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private record Sampler(WorldModel model, Tile tile, TilePos pos) {

        float height(int localX, int localZ, float fallback) {
            if (localX >= 0 && localX < Tile.SIZE && localZ >= 0 && localZ < Tile.SIZE) {
                return tile.height(Tile.index(localX, localZ));
            }
            int x = pos.minBlockX() + localX;
            int z = pos.minBlockZ() + localZ;
            return model.contains(x, z) ? model.height(x, z) : fallback;
        }
    }
}
