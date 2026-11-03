package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
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

    private record Key(double originX, double originZ, double zoom, int width, int height, long version) {
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
            long version) {
        Key next = new Key(viewport.originX(), viewport.originZ(), viewport.zoom(), width, height, version);
        if (next.equals(key) && image != null) {
            return image;
        }
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
        TileRect bounds = workspace.spec().bounds();
        int base = Session.baseColor(workspace.spec());
        int[] blockX = new int[width];
        for (int sx = 0; sx < width; sx++) {
            blockX[sx] = (int) Math.floor(viewport.worldX(sx + 0.5));
        }
        TilePos last = null;
        int[] lastThumb = null;
        int lastFlat = background;
        boolean lastPending = false;
        int[] scratch = new int[Workspace.THUMB_PIXELS];
        for (int sz = 0; sz < height; sz++) {
            int bz = (int) Math.floor(viewport.worldZ(sz + 0.5));
            int tz = Math.floorDiv(bz, Tile.SIZE);
            int cellZ = ((bz & (Tile.SIZE - 1)) >> 4) * Workspace.THUMB_SIDE;
            int row = sz * width;
            for (int sx = 0; sx < width; sx++) {
                int bx = blockX[sx];
                int tx = Math.floorDiv(bx, Tile.SIZE);
                if (last == null || last.x() != tx || last.z() != tz) {
                    last = new TilePos(tx, tz);
                    lastThumb = null;
                    lastPending = false;
                    lastFlat = background;
                    if (bounds.contains(last)) {
                        lastFlat = base < 0 ? background : base;
                        if (model.tile(last).isPresent()) {
                            BufferedImage tile = cache.image(model, last, TileRenderer.MAX_LOD, List.of(), null);
                            tile.getRGB(0, 0, Workspace.THUMB_SIDE, Workspace.THUMB_SIDE, scratch, 0,
                                    Workspace.THUMB_SIDE);
                            lastThumb = scratch.clone();
                        } else {
                            Optional<Workspace.Entry> entry = workspace.entry(last);
                            if (entry.isPresent()) {
                                lastThumb = entry.get().thumb();
                                lastPending = entry.get().pending();
                            }
                        }
                    }
                }
                int color = lastThumb == null ? lastFlat
                        : lastThumb[cellZ + ((bx & (Tile.SIZE - 1)) >> 4)] & 0xFFFFFF;
                if (lastPending) {
                    color = TileRenderer.mix(color, PENDING, PENDING_MIX);
                }
                pixels[row + sx] = color;
            }
        }
        key = next;
        image = out;
        return out;
    }
}
