package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public final class AreaLoader {

    public static final int RING = 1;

    private AreaLoader() {
    }

    public static TileRect loadedRect(Workspace workspace, TileRect area) {
        TileRect bounds = workspace.spec().bounds();
        return area.expand(RING).intersect(bounds).orElse(area);
    }

    public static long tilesIn(Workspace workspace, TileRect rect) {
        if (workspace.spec().base().filled()) {
            return rect.intersect(workspace.spec().bounds()).map(TileRect::count).orElse(0L);
        }
        return workspace.storedIn(rect).size();
    }

    public static List<TileRect> regionsToWrite(Workspace workspace, TileRect area) {
        if (workspace.spec().base().filled()) {
            return WorldExporter.regionRects(area);
        }
        java.util.Set<TileRect> regions = new java.util.TreeSet<>((a, b) -> a.minZ() != b.minZ()
                ? Integer.compare(a.minZ(), b.minZ()) : Integer.compare(a.minX(), b.minX()));
        for (TilePos pos : workspace.storedIn(area)) {
            regionOf(pos).intersect(area).ifPresent(regions::add);
        }
        return new ArrayList<>(regions);
    }

    public static WorldExporter.Result exportWorkspace(Workspace workspace, TileRect area, java.nio.file.Path world,
            int threads, Progress progress) throws IOException {
        TileRect target = area.intersect(workspace.spec().bounds())
                .orElseThrow(() -> new IllegalArgumentException("area outside bounds"));
        return WorldExporter.exportAll(workspace.spec(), regionsToWrite(workspace, target), rect -> {
            TileRect loaded = loadedRect(workspace, rect);
            return build(workspace, loaded, rect, Math.toIntExact(loaded.count()), 1, Progress.NONE);
        }, world, threads, progress);
    }

    static TileRect regionOf(TilePos pos) {
        int per = WorldExporter.TILES_PER_REGION;
        int rx = Math.floorDiv(pos.x(), per) * per;
        int rz = Math.floorDiv(pos.z(), per) * per;
        return new TileRect(rx, rz, rx + per - 1, rz + per - 1);
    }

    public static WorldModel load(Workspace workspace, TileRect area, int maxTiles, int threads, Progress progress)
            throws IOException {
        TileRect bounds = workspace.spec().bounds();
        TileRect editable = area.intersect(bounds).orElseThrow(() -> new IllegalArgumentException("area outside bounds"));
        TileRect loaded = loadedRect(workspace, editable);
        return build(workspace, loaded, editable, maxTiles, threads, progress);
    }

    static WorldModel build(Workspace workspace, TileRect loaded, TileRect editable, int maxTiles, int threads,
            Progress progress) throws IOException {
        boolean filled = workspace.spec().base().filled();
        List<TilePos> stored = workspace.storedIn(loaded);
        long total = filled ? loaded.count() : stored.size();
        if (total > maxTiles) {
            throw new IllegalArgumentException("area needs " + total + " tiles, limit " + maxTiles);
        }
        WorldModel.Builder builder = workspace.spec().builder().editable(editable).maxTiles(maxTiles);
        List<TileCodec.Decoded> decoded = decodeAll(workspace, stored, threads, progress);
        java.util.Set<TilePos> present = new java.util.HashSet<>();
        for (TileCodec.Decoded tile : decoded) {
            TileCodec.apply(builder, tile);
            present.add(tile.pos());
        }
        if (filled) {
            for (int z = loaded.minZ(); z <= loaded.maxZ(); z++) {
                for (int x = loaded.minX(); x <= loaded.maxX(); x++) {
                    TilePos pos = new TilePos(x, z);
                    if (!present.contains(pos)) {
                        builder.baseTile(pos);
                    }
                }
            }
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new ProjectFormatException("invalid workspace tiles: " + e.getMessage(), e);
        }
    }

    private static List<TileCodec.Decoded> decodeAll(Workspace workspace, List<TilePos> positions, int threads,
            Progress progress) throws IOException {
        List<TileCodec.Decoded> result = new ArrayList<>(positions.size());
        if (positions.isEmpty()) {
            return result;
        }
        int workers = Math.clamp(threads, 1, Math.max(1, positions.size()));
        if (workers == 1) {
            long done = 0;
            for (TilePos pos : positions) {
                result.add(workspace.decode(pos));
                if (!progress.report(++done, positions.size())) {
                    throw new CancellationException("loading cancelled");
                }
            }
            return result;
        }
        ExecutorService pool = Executors.newFixedThreadPool(workers, Workers.daemonThreads("mc-painter-load"));
        try {
            List<Future<TileCodec.Decoded>> futures = new ArrayList<>(positions.size());
            for (TilePos pos : positions) {
                futures.add(pool.submit(() -> workspace.decode(pos)));
            }
            long done = 0;
            for (Future<TileCodec.Decoded> future : futures) {
                result.add(Workers.await(future));
                if (!progress.report(++done, positions.size())) {
                    throw new CancellationException("loading cancelled");
                }
            }
            return result;
        } finally {
            pool.shutdownNow();
        }
    }
}
