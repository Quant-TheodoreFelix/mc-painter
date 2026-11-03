package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import space.qu4nt.thirdparty.mcpainter.io.SurfacePreview;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class TileImageCache {

    static final long DEFAULT_MAX_BYTES = 128L * 1024 * 1024;

    private record Key(TilePos pos, int lod, List<Layer> overlay, SurfacePreview.Surface surface) {
    }

    private final long maxBytes;
    private final LinkedHashMap<Key, BufferedImage> images = new LinkedHashMap<>(256, 0.75f, true);
    private long bytes;

    TileImageCache(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("cache size must be positive");
        }
        this.maxBytes = maxBytes;
    }

    BufferedImage image(WorldModel model, TilePos pos, int lod) {
        return image(model, pos, lod, List.of());
    }

    BufferedImage image(WorldModel model, TilePos pos, int lod, List<Layer> overlay) {
        return image(model, pos, lod, overlay, null);
    }

    BufferedImage image(WorldModel model, TilePos pos, int lod, List<Layer> overlay, SurfacePreview.Surface surface) {
        Key key = new Key(pos, lod, List.copyOf(overlay), surface);
        BufferedImage cached = images.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = surface == null ? TileRenderer.render(model, pos, lod, key.overlay())
                : PreviewRenderer.render(surface, model.tile(pos).orElseThrow(
                        () -> new IllegalArgumentException("no tile at " + pos)), lod, key.overlay());
        images.put(key, image);
        bytes += sizeOf(image);
        evictExcept(key);
        return image;
    }

    void invalidate(Iterable<TilePos> positions) {
        Set<TilePos> stale = new HashSet<>();
        positions.forEach(stale::add);
        Iterator<Map.Entry<Key, BufferedImage>> entries = images.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Key, BufferedImage> entry = entries.next();
            if (stale.contains(entry.getKey().pos())) {
                bytes -= sizeOf(entry.getValue());
                entries.remove();
            }
        }
    }

    void clear() {
        images.clear();
        bytes = 0;
    }

    long bytes() {
        return bytes;
    }

    int size() {
        return images.size();
    }

    private void evictExcept(Key keep) {
        Iterator<Map.Entry<Key, BufferedImage>> entries = images.entrySet().iterator();
        while (bytes > maxBytes && entries.hasNext()) {
            Map.Entry<Key, BufferedImage> eldest = entries.next();
            if (eldest.getKey().equals(keep)) {
                continue;
            }
            bytes -= sizeOf(eldest.getValue());
            entries.remove();
        }
    }

    private static long sizeOf(BufferedImage image) {
        return (long) image.getWidth() * image.getHeight() * Integer.BYTES;
    }
}
