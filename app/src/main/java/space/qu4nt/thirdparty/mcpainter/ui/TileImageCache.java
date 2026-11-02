package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class TileImageCache {

    static final long DEFAULT_MAX_BYTES = 128L * 1024 * 1024;

    private record Key(TilePos pos, int lod) {
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
        Key key = new Key(pos, lod);
        BufferedImage cached = images.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = TileRenderer.render(model, pos, lod);
        images.put(key, image);
        bytes += sizeOf(image);
        evictExcept(key);
        return image;
    }

    void invalidate(Iterable<TilePos> positions) {
        for (TilePos pos : positions) {
            for (int lod = 0; lod <= TileRenderer.MAX_LOD; lod++) {
                BufferedImage removed = images.remove(new Key(pos, lod));
                if (removed != null) {
                    bytes -= sizeOf(removed);
                }
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
