package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import space.qu4nt.thirdparty.mcpainter.io.SurfacePreview;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class PreviewCache {

    static final long DEFAULT_MAX_BYTES = 96L * 1024 * 1024;

    interface Renderer {
        SurfacePreview.Surface render(WorldModel snapshot, TilePos pos);
    }

    private record Job(TilePos pos, int generation, long version) {
    }

    private final Supplier<WorldModel> model;
    private final Executor workers;
    private final Executor edt;
    private final Renderer renderer;
    private final Consumer<TilePos> ready;
    private int maxInFlight;
    private final int maxEntries;
    private final LinkedHashMap<TilePos, SurfacePreview.Surface> surfaces = new LinkedHashMap<>(256, 0.75f, true);
    private final Map<TilePos, Long> versions = new HashMap<>();
    private final Map<TilePos, Long> computed = new HashMap<>();
    private final Set<TilePos> failed = new HashSet<>();
    private final LinkedHashSet<TilePos> pending = new LinkedHashSet<>();
    private final Set<TilePos> inFlight = new HashSet<>();
    private int generation;
    private long clock;

    PreviewCache(Supplier<WorldModel> model, Executor workers, Executor edt, Renderer renderer,
            Consumer<TilePos> ready, int maxInFlight, long maxBytes) {
        this.model = Objects.requireNonNull(model, "model");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.ready = Objects.requireNonNull(ready, "ready");
        if (maxInFlight < 1 || maxBytes < SurfacePreview.Surface.BYTES) {
            throw new IllegalArgumentException("cache limits too small");
        }
        this.maxInFlight = maxInFlight;
        this.maxEntries = (int) Math.min(Integer.MAX_VALUE, maxBytes / SurfacePreview.Surface.BYTES);
    }

    static ExecutorService workerPool() {
        return workerPool(Math.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 4));
    }

    static ExecutorService workerPool(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be positive");
        }
        AtomicInteger count = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "mc-painter-preview-" + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        return new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), factory);
    }

    static void resize(ExecutorService pool, int threads) {
        if (threads < 1 || !(pool instanceof ThreadPoolExecutor executor)) {
            return;
        }
        if (threads > executor.getMaximumPoolSize()) {
            executor.setMaximumPoolSize(threads);
            executor.setCorePoolSize(threads);
        } else {
            executor.setCorePoolSize(threads);
            executor.setMaximumPoolSize(threads);
        }
    }

    void setMaxInFlight(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("maxInFlight must be positive");
        }
        maxInFlight = value;
        pump();
    }

    static int threads(ExecutorService pool) {
        return pool instanceof ThreadPoolExecutor executor ? executor.getMaximumPoolSize() : 1;
    }

    Optional<SurfacePreview.Surface> surface(TilePos pos) {
        SurfacePreview.Surface surface = surfaces.get(pos);
        if ((surface == null || computed.getOrDefault(pos, -1L) != version(pos)) && !failed.contains(pos)) {
            request(pos);
        }
        return Optional.ofNullable(surface);
    }

    boolean current(TilePos pos) {
        return surfaces.containsKey(pos) && computed.getOrDefault(pos, -1L) == version(pos);
    }

    void keepOnly(Set<TilePos> wanted) {
        pending.retainAll(wanted);
    }

    void invalidate(Iterable<TilePos> tiles) {
        Set<TilePos> touched = new LinkedHashSet<>();
        for (TilePos pos : tiles) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    touched.add(new TilePos(pos.x() + dx, pos.z() + dz));
                }
            }
        }
        for (TilePos pos : touched) {
            versions.put(pos, ++clock);
            failed.remove(pos);
        }
    }

    void invalidateAll() {
        for (TilePos pos : List.copyOf(surfaces.keySet())) {
            versions.put(pos, ++clock);
        }
        failed.clear();
    }

    void clear() {
        generation++;
        surfaces.clear();
        versions.clear();
        computed.clear();
        failed.clear();
        pending.clear();
        inFlight.clear();
    }

    int size() {
        return surfaces.size();
    }

    int pendingCount() {
        return pending.size() + inFlight.size();
    }

    private long version(TilePos pos) {
        return versions.getOrDefault(pos, 0L);
    }

    private void request(TilePos pos) {
        if (!inFlight.contains(pos)) {
            pending.add(pos);
        }
        pump();
    }

    private void pump() {
        Iterator<TilePos> next = pending.iterator();
        while (inFlight.size() < maxInFlight && next.hasNext()) {
            TilePos pos = next.next();
            next.remove();
            WorldModel current = model.get();
            if (current.tile(pos).isEmpty()) {
                surfaces.remove(pos);
                continue;
            }
            List<TilePos> around = new ArrayList<>(9);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    around.add(new TilePos(pos.x() + dx, pos.z() + dz));
                }
            }
            WorldModel snapshot = current.snapshot(around);
            Job job = new Job(pos, generation, version(pos));
            inFlight.add(pos);
            workers.execute(() -> {
                SurfacePreview.Surface result;
                try {
                    result = renderer.render(snapshot, pos);
                } catch (RuntimeException | LinkageError e) {
                    result = null;
                }
                SurfacePreview.Surface done = result;
                edt.execute(() -> finished(job, done));
            });
            next = pending.iterator();
        }
    }

    private void finished(Job job, SurfacePreview.Surface result) {
        if (job.generation() != generation) {
            return;
        }
        inFlight.remove(job.pos());
        if (result == null) {
            if (job.version() == version(job.pos())) {
                failed.add(job.pos());
            }
        } else {
            surfaces.put(job.pos(), result);
            computed.put(job.pos(), job.version());
            evict();
            ready.accept(job.pos());
        }
        pump();
    }

    private void evict() {
        Iterator<Map.Entry<TilePos, SurfacePreview.Surface>> oldest = surfaces.entrySet().iterator();
        while (surfaces.size() > maxEntries && oldest.hasNext()) {
            TilePos pos = oldest.next().getKey();
            oldest.remove();
            computed.remove(pos);
        }
    }
}
