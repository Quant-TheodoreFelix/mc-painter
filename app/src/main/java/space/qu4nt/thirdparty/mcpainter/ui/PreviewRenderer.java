package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.io.BlockCatalog.Block;
import space.qu4nt.thirdparty.mcpainter.io.FlowerBlocks;
import space.qu4nt.thirdparty.mcpainter.io.SurfacePreview;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.Tile;

final class PreviewRenderer {

    static final double HIGH = 1.0;
    static final double NORMAL = 220 / 255.0;
    static final double LOW = 180 / 255.0;

    private static final Map<Block, Integer> COLORS = colors();

    private PreviewRenderer() {
    }

    private static Map<Block, Integer> colors() {
        Map<Block, Integer> colors = new EnumMap<>(Block.class);
        colors.put(Block.AIR, 0x000000);
        colors.put(Block.BEDROCK, 0x707070);
        colors.put(Block.STONE, 0x707070);
        colors.put(Block.DEEPSLATE, 0x646464);
        colors.put(Block.DIRT, 0x976D4D);
        colors.put(Block.GRASS_BLOCK, 0x7FB238);
        colors.put(Block.SAND, 0xF7E9A3);
        colors.put(Block.GRAVEL, 0x857F7A);
        colors.put(Block.SNOW_BLOCK, 0xFFFFFF);
        colors.put(Block.WATER, 0x4040FF);
        colors.put(Block.COAL_ORE, 0x4A4A4A);
        colors.put(Block.IRON_ORE, 0x9A8270);
        colors.put(Block.DEEPSLATE_COAL_ORE, 0x3C3C3C);
        colors.put(Block.DEEPSLATE_IRON_ORE, 0x857064);
        colors.put(Block.OAK_LOG, 0x8F7748);
        colors.put(Block.OAK_LEAVES, 0x007C00);
        colors.put(Block.SHORT_GRASS, 0x5E9E2A);
        colors.put(Block.TALL_GRASS, 0x4C8C22);
        for (Layer flower : Layer.flowers()) {
            colors.put(FlowerBlocks.block(flower), TileRenderer.layerColor(flower));
        }
        for (Block block : Block.values()) {
            if (!colors.containsKey(block)) {
                throw new ExceptionInInitializerError("no preview color for " + block);
            }
        }
        return colors;
    }

    static int baseColor(Block block) {
        return COLORS.get(Objects.requireNonNull(block, "block"));
    }

    static double shade(SurfacePreview.Surface surface, int localX, int localZ) {
        int parity = (localX + localZ) & 1;
        if (surface.block(localX, localZ) == Block.WATER) {
            double d = surface.waterDepth(localX, localZ) * 0.1 + parity * 0.2;
            return d < 0.5 ? HIGH : d > 0.9 ? LOW : NORMAL;
        }
        if (!surface.known(localX, localZ - 1)) {
            return NORMAL;
        }
        double d = surface.height(localX, localZ) - surface.height(localX, localZ - 1) + (parity - 0.5) * 0.4;
        return d > 0.6 ? HIGH : d < -0.6 ? LOW : NORMAL;
    }

    static int color(SurfacePreview.Surface surface, int localX, int localZ) {
        return TileRenderer.multiply(baseColor(surface.block(localX, localZ)), shade(surface, localX, localZ));
    }

    static BufferedImage render(SurfacePreview.Surface surface, Tile tile, int lod, List<Layer> overlay) {
        Objects.checkIndex(lod, TileRenderer.MAX_LOD + 1);
        Objects.requireNonNull(overlay, "overlay");
        int step = 1 << lod;
        int size = Tile.SIZE >> lod;
        int half = step >> 1;
        int overlayColor = overlay.isEmpty() ? 0 : TileRenderer.overlayColor(overlay);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int iz = 0; iz < size; iz++) {
            int lz = iz * step + half;
            for (int ix = 0; ix < size; ix++) {
                int lx = ix * step + half;
                int color = color(surface, lx, lz);
                if (!overlay.isEmpty()) {
                    int index = Tile.index(lx, lz);
                    int value = 0;
                    for (Layer layer : overlay) {
                        value = Math.max(value, tile.layer(layer, index));
                    }
                    if (value > 0) {
                        color = TileRenderer.mix(color, overlayColor,
                                TileRenderer.OVERLAY_MIN + TileRenderer.OVERLAY_RANGE * value / Layer.MAX_VALUE);
                    }
                }
                pixels[iz * size + ix] = color;
            }
        }
        return image;
    }
}
