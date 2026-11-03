package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.io.Workspace;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class OverviewRenderer {

    static final int PENDING = 0xFFA640;
    static final double PENDING_MIX = 0.35;
    static final int MAX_THUMBS = 20_000;
    static final int CELL = Tile.SIZE / Workspace.THUMB_SIDE;

    record Grid(TileRect rect, int chunkArgb, int tileArgb, long tileSpacing) {
    }

    private record Key(double originX, double originZ, double zoom, int width, int height, long version, Grid grid) {
    }

    private final TileImageCache cache;
    private final LinkedHashMap<TilePos, BufferedImage> thumbs = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TilePos, BufferedImage> eldest) {
            return size() > MAX_THUMBS;
        }
    };
    private Key key;
    private BufferedImage image;

    OverviewRenderer(TileImageCache cache) {
        this.cache = cache;
    }

    void clearThumbs() {
        thumbs.clear();
        key = null;
    }

    BufferedImage thumb(Workspace workspace, TilePos pos) {
        BufferedImage cached = thumbs.get(pos);
        if (cached != null) {
            return cached;
        }
        Optional<Workspace.Entry> entry = workspace.entry(pos);
        if (entry.isEmpty()) {
            return null;
        }
        int[] pixels = entry.get().thumb();
        BufferedImage made = new BufferedImage(Workspace.THUMB_SIDE, Workspace.THUMB_SIDE, BufferedImage.TYPE_INT_RGB);
        made.setRGB(0, 0, Workspace.THUMB_SIDE, Workspace.THUMB_SIDE, pixels, 0, Workspace.THUMB_SIDE);
        thumbs.put(pos, made);
        return made;
    }

    BufferedImage render(Viewport viewport, int width, int height, WorldModel model, Workspace workspace, int background,
            long version, Grid grid) {
        Key next = new Key(viewport.originX(), viewport.originZ(), viewport.zoom(), width, height, version, grid);
        if (next.equals(key) && image != null) {
            return image;
        }
        BufferedImage out = image != null && image.getWidth() == width && image.getHeight() == height ? image
                : new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        Arrays.fill(pixels, background);
        TileRect bounds = workspace.spec().bounds();
        int base = Session.baseColor(workspace.spec());
        if (base >= 0) {
            int x0 = clampPixel(viewport.screenX(bounds.minBlockX()), width);
            int x1 = clampPixel(viewport.screenX(bounds.maxBlockX() + 1.0), width);
            int z0 = clampPixel(viewport.screenZ(bounds.minBlockZ()), height);
            int z1 = clampPixel(viewport.screenZ(bounds.maxBlockZ() + 1.0), height);
            for (int z = z0; z < z1; z++) {
                Arrays.fill(pixels, z * width + x0, z * width + x1, base);
            }
        }
        Optional<TileRect> visible = visibleTiles(viewport, width, height).intersect(bounds);
        if (visible.isPresent()) {
            TileRect view = visible.get();
            for (TilePos pos : workspace.storedIn(view)) {
                if (model.tile(pos).isPresent()) {
                    continue;
                }
                Optional<Workspace.Entry> entry = workspace.entry(pos);
                if (entry.isPresent()) {
                    blit(pixels, width, height, viewport, pos, entry.get().thumb(), entry.get().pending());
                }
            }
            int[] scratch = new int[Workspace.THUMB_PIXELS];
            model.forEachTile((pos, tile) -> {
                if (tile.shared() || !view.contains(pos)) {
                    return;
                }
                BufferedImage rendered = cache.image(model, pos, TileRenderer.MAX_LOD, List.of(), null);
                rendered.getRGB(0, 0, Workspace.THUMB_SIDE, Workspace.THUMB_SIDE, scratch, 0, Workspace.THUMB_SIDE);
                blit(pixels, width, height, viewport, pos, scratch, false);
            });
        }
        if (grid != null) {
            paintGrid(pixels, width, height, viewport, grid);
        }
        key = next;
        image = out;
        return out;
    }

    private static void paintGrid(int[] pixels, int width, int height, Viewport viewport, Grid grid) {
        TileRect rect = grid.rect();
        int x0 = clampPixel(viewport.screenX(rect.minBlockX()), width);
        int x1 = clampPixel(viewport.screenX(rect.maxBlockX() + 1.0), width);
        int z0 = clampPixel(viewport.screenZ(rect.minBlockZ()), height);
        int z1 = clampPixel(viewport.screenZ(rect.maxBlockZ() + 1.0), height);
        if (x0 >= x1 || z0 >= z1) {
            return;
        }
        double scale = viewport.scale();
        if ((grid.chunkArgb() >>> 24) != 0 && 16 * scale >= MapView.GRID_MIN_PIXELS) {
            lines(pixels, width, viewport, x0, x1, z0, z1, 16, grid.chunkArgb());
        }
        if ((grid.tileArgb() >>> 24) != 0) {
            lines(pixels, width, viewport, x0, x1, z0, z1, grid.tileSpacing(), grid.tileArgb());
        }
    }

    private static void lines(int[] pixels, int width, Viewport viewport, int x0, int x1, int z0, int z1,
            long spacing, int argb) {
        double alpha = (argb >>> 24) / 255.0;
        int color = argb & 0xFFFFFF;
        double left = viewport.worldX(x0);
        double right = viewport.worldX(x1);
        for (long x = Math.ceilDiv((long) Math.floor(left), spacing) * spacing; x <= right; x += spacing) {
            int sx = (int) Math.floor(viewport.screenX(x));
            if (sx < x0 || sx >= x1) {
                continue;
            }
            for (int z = z0; z < z1; z++) {
                int i = z * width + sx;
                pixels[i] = TileRenderer.mix(pixels[i], color, alpha);
            }
        }
        double top = viewport.worldZ(z0);
        double bottom = viewport.worldZ(z1);
        for (long z = Math.ceilDiv((long) Math.floor(top), spacing) * spacing; z <= bottom; z += spacing) {
            int sz = (int) Math.floor(viewport.screenZ(z));
            if (sz < z0 || sz >= z1) {
                continue;
            }
            int row = sz * width;
            for (int x = x0; x < x1; x++) {
                pixels[row + x] = TileRenderer.mix(pixels[row + x], color, alpha);
            }
        }
    }

    static TileRect visibleTiles(Viewport viewport, int width, int height) {
        int minX = Math.floorDiv((int) Math.floor(Math.clamp(viewport.worldX(0), -1e9, 1e9)), Tile.SIZE);
        int maxX = Math.floorDiv((int) Math.floor(Math.clamp(viewport.worldX(width), -1e9, 1e9)), Tile.SIZE);
        int minZ = Math.floorDiv((int) Math.floor(Math.clamp(viewport.worldZ(0), -1e9, 1e9)), Tile.SIZE);
        int maxZ = Math.floorDiv((int) Math.floor(Math.clamp(viewport.worldZ(height), -1e9, 1e9)), Tile.SIZE);
        return new TileRect(minX, minZ, Math.max(minX, maxX), Math.max(minZ, maxZ));
    }

    private static void blit(int[] pixels, int width, int height, Viewport viewport, TilePos pos, int[] thumb,
            boolean pending) {
        double sx0 = viewport.screenX(pos.minBlockX());
        double sx1 = viewport.screenX(pos.minBlockX() + (double) Tile.SIZE);
        double sz0 = viewport.screenZ(pos.minBlockZ());
        double sz1 = viewport.screenZ(pos.minBlockZ() + (double) Tile.SIZE);
        int x0 = clampPixel(sx0, width);
        int x1 = Math.max(Math.min(x0 + 1, width), clampPixel(sx1, width));
        int z0 = clampPixel(sz0, height);
        int z1 = Math.max(Math.min(z0 + 1, height), clampPixel(sz1, height));
        if (x0 >= width || z0 >= height || x1 <= 0 || z1 <= 0) {
            return;
        }
        double perPixel = Tile.SIZE / Math.max(1e-9, sx1 - sx0);
        int[] columns = new int[x1 - x0];
        for (int x = x0; x < x1; x++) {
            double block = (x + 0.5 - sx0) * perPixel;
            columns[x - x0] = Math.clamp((int) (block / CELL), 0, Workspace.THUMB_SIDE - 1);
        }
        for (int z = z0; z < z1; z++) {
            double block = (z + 0.5 - sz0) * perPixel;
            int row = Math.clamp((int) (block / CELL), 0, Workspace.THUMB_SIDE - 1) * Workspace.THUMB_SIDE;
            int offset = z * width;
            for (int x = x0; x < x1; x++) {
                int color = thumb[row + columns[x - x0]] & 0xFFFFFF;
                pixels[offset + x] = pending ? TileRenderer.mix(color, PENDING, PENDING_MIX) : color;
            }
        }
    }

    private static int clampPixel(double value, int limit) {
        return (int) Math.clamp(Math.floor(value), 0, limit);
    }
}
