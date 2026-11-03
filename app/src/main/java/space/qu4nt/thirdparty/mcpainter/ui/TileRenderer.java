package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
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
    static final double OVERLAY_MIN = 0.3;
    static final double OVERLAY_RANGE = 0.65;
    static final int FLOWER_MIX = 0xF050C8;
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

    static int layerColor(Layer layer) {
        return switch (layer) {
            case COAL_ORE -> 0x1A1A1A;
            case IRON_ORE -> 0xE0915A;
            case OAK_TREES -> 0x002A00;
            case GRASS -> 0xB8F040;
            case DANDELION -> 0xF5D90A;
            case POPPY -> 0xD32F2F;
            case BLUE_ORCHID -> 0x29A8E0;
            case ALLIUM -> 0xA855E0;
            case AZURE_BLUET -> 0xC8D8F0;
            case RED_TULIP -> 0xF0402A;
            case ORANGE_TULIP -> 0xF58A1F;
            case WHITE_TULIP -> 0xF4F4F4;
            case PINK_TULIP -> 0xF59AC0;
            case OXEYE_DAISY -> 0xEAE3B8;
            case CORNFLOWER -> 0x3F6FE8;
            case LILY_OF_THE_VALLEY -> 0xDDF2DD;
            case SUNFLOWER -> 0xFFB300;
            case LILAC -> 0xC79BE0;
            case ROSE_BUSH -> 0xB8143C;
            case PEONY -> 0xF7B8D8;
            case TORCHFLOWER -> 0xFF6A2A;
            case PINK_PETALS -> 0xF06AA8;
            case PITCHER_PLANT -> 0x3FB0A8;
            case EYEBLOSSOM -> 0xF08A30;
            case WILDFLOWERS -> 0xF5E66A;
        };
    }

    static int overlayColor(List<Layer> overlay) {
        return overlay.size() == 1 ? layerColor(overlay.getFirst()) : FLOWER_MIX;
    }

    static BufferedImage render(WorldModel model, TilePos pos, int lod) {
        return render(model, pos, lod, List.of());
    }

    static BufferedImage render(WorldModel model, TilePos pos, int lod, List<Layer> overlay) {
        Objects.requireNonNull(overlay, "overlay");
        int overlayColor = overlay.isEmpty() ? 0 : overlayColor(overlay);
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
                if (!overlay.isEmpty()) {
                    int value = 0;
                    for (Layer layer : overlay) {
                        value = Math.max(value, tile.layer(layer, index));
                    }
                    if (value > 0) {
                        color = mix(color, overlayColor, OVERLAY_MIN + OVERLAY_RANGE * value / Layer.MAX_VALUE);
                    }
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
