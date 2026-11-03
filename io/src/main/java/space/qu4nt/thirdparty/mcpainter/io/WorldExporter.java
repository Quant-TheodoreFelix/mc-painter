package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.BlockStateEntry;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkData;
import space.qu4nt.thirdparty.mcpainter.anvil.region.ChunkCompression;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RawChunk;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionFile;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionPos;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionWriter;
import space.qu4nt.thirdparty.mcpainter.anvil.world.Dimension;
import space.qu4nt.thirdparty.mcpainter.anvil.world.MinecraftWorld;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;
import space.qu4nt.thirdparty.mcpainter.anvil.world.WorldLock;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public final class WorldExporter {

    private static final int CHUNKS_PER_TILE = Tile.SIZE / 16;
    private static final int SUBSURFACE_DEPTH = 3;
    static final int DEEPSLATE_TOP = 0;
    static final int DEEPSLATE_BLEND = 8;

    public static final int TILES_PER_REGION = RegionPos.CHUNKS_PER_SIDE / CHUNKS_PER_TILE;
    public static final long BYTES_PER_CHUNK = 4096;
    public static final long REGION_HEADER_BYTES = 8192;
    public static final double SECONDS_PER_TILE = 0.17;

    public record Result(int regionsWritten, int chunksReplaced, int chunksCreated, int entityChunksRemoved,
            int poiChunksRemoved, boolean cancelled) {

        public Result(int regionsWritten, int chunksReplaced, int chunksCreated, int entityChunksRemoved,
                int poiChunksRemoved) {
            this(regionsWritten, chunksReplaced, chunksCreated, entityChunksRemoved, poiChunksRemoved, false);
        }

        Result plus(Result other) {
            return new Result(regionsWritten + other.regionsWritten, chunksReplaced + other.chunksReplaced,
                    chunksCreated + other.chunksCreated, entityChunksRemoved + other.entityChunksRemoved,
                    poiChunksRemoved + other.poiChunksRemoved, cancelled || other.cancelled);
        }
    }

    public record Estimate(long tiles, long regions, long chunks, long bytes, long seconds) {
    }

    @FunctionalInterface
    public interface TileSource {

        WorldModel load(TileRect region) throws IOException;
    }

    private record Context(MinecraftWorld world, Dimension dimension, ChunkData prototype, BlockCatalog catalog,
            List<ResourceLocation> biomes, int minY, int maxY, int timestamp) {
    }

    private WorldExporter() {
    }

    public static Estimate estimate(long tiles, long regions, int threads) {
        long chunks = tiles * CHUNKS_PER_TILE * CHUNKS_PER_TILE;
        long bytes = chunks * BYTES_PER_CHUNK + regions * REGION_HEADER_BYTES;
        long seconds = (long) Math.ceil(tiles * SECONDS_PER_TILE / Math.max(1, threads));
        return new Estimate(tiles, regions, chunks, bytes, seconds);
    }

    public static long regionCount(TileRect rect) {
        int per = TILES_PER_REGION;
        long width = Math.floorDiv(rect.maxX(), per) - Math.floorDiv(rect.minX(), per) + 1L;
        long depth = Math.floorDiv(rect.maxZ(), per) - Math.floorDiv(rect.minZ(), per) + 1L;
        return width * depth;
    }

    public static long usableSpace(Path worldDirectory) throws IOException {
        return Files.getFileStore(worldDirectory).getUsableSpace();
    }

    public static Result export(WorldModel model, Path worldDirectory) throws IOException {
        return export(model, worldDirectory, model.tilePositions(), 1, Progress.NONE);
    }

    public static Result export(WorldModel model, Path worldDirectory, Collection<TilePos> tiles, int threads,
            Progress progress) throws IOException {
        MinecraftWorld world = MinecraftWorld.open(worldDirectory);
        WorldLock lock = world.lock();
        try {
            Context context = context(world, model.minY(), model.maxY(), model.biomes());
            Map<RegionPos, List<int[]>> regions = new TreeMap<>(REGION_ORDER);
            for (TilePos tile : tiles) {
                if (model.tile(tile).isEmpty()) {
                    continue;
                }
                addChunks(regions, tile);
            }
            List<RegionJob> jobs = new ArrayList<>();
            for (Map.Entry<RegionPos, List<int[]>> entry : regions.entrySet()) {
                jobs.add(new RegionJob(entry.getKey(), () -> model, entry.getValue()));
            }
            return run(context, jobs, threads, progress);
        } finally {
            lock.close();
        }
    }

    public static Result exportAll(WorldSpec spec, List<TileRect> regionsToWrite, TileSource source,
            Path worldDirectory, int threads, Progress progress) throws IOException {
        MinecraftWorld world = MinecraftWorld.open(worldDirectory);
        WorldLock lock = world.lock();
        try {
            Context context = context(world, spec.minY(), spec.maxY(), spec.biomes());
            List<RegionJob> jobs = new ArrayList<>();
            for (TileRect rect : regionsToWrite) {
                RegionPos pos = RegionPos.ofChunk(rect.minX() * CHUNKS_PER_TILE, rect.minZ() * CHUNKS_PER_TILE);
                jobs.add(new RegionJob(pos, () -> source.load(rect), null, rect));
            }
            return run(context, jobs, threads, progress);
        } finally {
            lock.close();
        }
    }

    public static List<TileRect> regionRects(TileRect area) {
        int per = TILES_PER_REGION;
        List<TileRect> rects = new ArrayList<>();
        for (int rz = Math.floorDiv(area.minZ(), per); rz <= Math.floorDiv(area.maxZ(), per); rz++) {
            for (int rx = Math.floorDiv(area.minX(), per); rx <= Math.floorDiv(area.maxX(), per); rx++) {
                TileRect region = new TileRect(rx * per, rz * per, rx * per + per - 1, rz * per + per - 1);
                region.intersect(area).ifPresent(rects::add);
            }
        }
        return rects;
    }

    private static final java.util.Comparator<RegionPos> REGION_ORDER =
            (a, b) -> a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x());

    @FunctionalInterface
    private interface ModelSupplier {

        WorldModel get() throws IOException;
    }

    private record RegionJob(RegionPos pos, ModelSupplier model, List<int[]> chunks, TileRect tiles) {

        RegionJob(RegionPos pos, ModelSupplier model, List<int[]> chunks) {
            this(pos, model, chunks, null);
        }
    }

    private static Context context(MinecraftWorld world, int modelMinY, int modelMaxY, List<String> biomeIds)
            throws IOException {
        Dimension dimension = Dimension.OVERWORLD;
        ChunkData prototype = Prototypes.find(world, dimension)
                .orElseThrow(() -> new IOException("template world has no full chunk at its data version"));
        int minY = Prototypes.minY(prototype);
        int maxY = Prototypes.maxY(prototype);
        if (modelMinY < minY || modelMaxY > maxY) {
            throw new IOException("model height range " + modelMinY + ".." + modelMaxY
                    + " does not fit template range " + minY + ".." + maxY);
        }
        BlockCatalog catalog = new BlockCatalog(world.level().dataVersion());
        List<ResourceLocation> biomes = new ArrayList<>();
        for (String id : biomeIds) {
            biomes.add(ResourceLocation.parse(id));
        }
        return new Context(world, dimension, prototype, catalog, List.copyOf(biomes), minY, maxY,
                (int) (System.currentTimeMillis() / 1000));
    }

    private static void addChunks(Map<RegionPos, List<int[]>> regions, TilePos tile) {
        for (int cz = 0; cz < CHUNKS_PER_TILE; cz++) {
            for (int cx = 0; cx < CHUNKS_PER_TILE; cx++) {
                int chunkX = tile.x() * CHUNKS_PER_TILE + cx;
                int chunkZ = tile.z() * CHUNKS_PER_TILE + cz;
                regions.computeIfAbsent(RegionPos.ofChunk(chunkX, chunkZ), k -> new ArrayList<>())
                        .add(new int[] {chunkX, chunkZ});
            }
        }
    }

    private static Result run(Context context, List<RegionJob> jobs, int threads, Progress progress)
            throws IOException {
        int workers = Math.clamp(threads, 1, Math.max(1, jobs.size()));
        AtomicBoolean stop = new AtomicBoolean();
        Result total = new Result(0, 0, 0, 0, 0);
        if (!progress.report(0, jobs.size())) {
            return new Result(0, 0, 0, 0, 0, true);
        }
        if (workers == 1) {
            long done = 0;
            for (RegionJob job : jobs) {
                total = total.plus(region(context, job));
                if (!progress.report(++done, jobs.size())) {
                    return total.plus(new Result(0, 0, 0, 0, 0, done < jobs.size()));
                }
            }
            return total;
        }
        ExecutorService pool = Executors.newFixedThreadPool(workers, Workers.daemonThreads("mc-painter-export"));
        try {
            CompletionService<Result> completion = new ExecutorCompletionService<>(pool);
            for (RegionJob job : jobs) {
                completion.submit(() -> stop.get() ? new Result(0, 0, 0, 0, 0, true) : region(context, job));
            }
            IOException failure = null;
            long done = 0;
            for (int i = 0; i < jobs.size(); i++) {
                try {
                    total = total.plus(Workers.await(completion.take()));
                } catch (IOException | RuntimeException e) {
                    stop.set(true);
                    if (failure == null) {
                        failure = e instanceof IOException io ? io : new IOException(e);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    stop.set(true);
                    throw new CancellationException("export interrupted");
                }
                if (!progress.report(++done, jobs.size())) {
                    stop.set(true);
                }
            }
            if (failure != null) {
                throw failure;
            }
            return stop.get() ? total.plus(new Result(0, 0, 0, 0, 0, true)) : total;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Result region(Context context, RegionJob job) throws IOException {
        WorldModel model = job.model().get();
        List<int[]> chunks = job.chunks();
        if (chunks == null) {
            Map<RegionPos, List<int[]>> regions = new TreeMap<>(REGION_ORDER);
            TileRect rect = job.tiles();
            for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                for (int x = rect.minX(); x <= rect.maxX(); x++) {
                    TilePos tile = new TilePos(x, z);
                    if (model.tile(tile).isPresent()) {
                        addChunks(regions, tile);
                    }
                }
            }
            chunks = regions.getOrDefault(job.pos(), List.of());
            if (chunks.isEmpty()) {
                return new Result(0, 0, 0, 0, 0);
            }
        }
        RegionPos pos = job.pos();
        MinecraftWorld world = context.world();
        Path path = world.regionPath(context.dimension(), pos);
        Files.createDirectories(path.getParent());
        RegionWriter writer;
        int replaced = 0;
        int created = 0;
        RegionFile existing = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? RegionFile.open(path) : null;
        try {
            writer = existing == null ? new RegionWriter() : RegionWriter.copyOf(existing);
            for (int[] chunk : chunks) {
                int localX = chunk[0] - pos.x() * RegionPos.CHUNKS_PER_SIDE;
                int localZ = chunk[1] - pos.z() * RegionPos.CHUNKS_PER_SIDE;
                Optional<ChunkData> own = existing == null ? Optional.empty()
                        : Prototypes.fullChunk(existing, localX, localZ, world.level().dataVersion());
                ChunkColumns columns = new ChunkColumns(model, context.catalog(), chunk[0], chunk[1], context.minY(),
                        context.maxY());
                ChunkFeatures features = ChunkFeatures.generate(model, context.catalog(), columns::blockAt, chunk[0],
                        chunk[1], context.minY(), context.maxY());
                RootTag rebuilt = ChunkRebuilder.rebuild(own.orElse(context.prototype()), chunk[0], chunk[1],
                        (x, y, z) -> {
                            BlockStateEntry feature = features.at(x, y, z);
                            return feature != null ? feature : columns.blockAt(x, y, z);
                        }, context.catalog()::traits,
                        (x, y, z) -> context.biomes().get(columns.biomeIndex(x, z)));
                writer.put(localX, localZ, RawChunk.encode(rebuilt, ChunkCompression.ZLIB, context.timestamp()));
                if (own.isPresent()) {
                    replaced++;
                } else {
                    created++;
                }
            }
        } finally {
            if (existing != null) {
                existing.close();
            }
        }
        writer.writeTo(path);
        int entitiesRemoved = removeChunks(world.entitiesDirectory(context.dimension()).resolve(pos.fileName()), pos,
                chunks);
        int poiRemoved = removeChunks(world.poiDirectory(context.dimension()).resolve(pos.fileName()), pos, chunks);
        return new Result(1, replaced, created, entitiesRemoved, poiRemoved);
    }

    private static int removeChunks(Path path, RegionPos pos, List<int[]> chunks) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        RegionWriter writer;
        int removed = 0;
        try (RegionFile region = RegionFile.open(path)) {
            writer = RegionWriter.copyOf(region);
            for (int[] chunk : chunks) {
                int localX = chunk[0] - pos.x() * RegionPos.CHUNKS_PER_SIDE;
                int localZ = chunk[1] - pos.z() * RegionPos.CHUNKS_PER_SIDE;
                if (writer.get(localX, localZ).isPresent()) {
                    writer.remove(localX, localZ);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            writer.writeTo(path);
        }
        return removed;
    }

    static final class ChunkColumns {

        private final int originX;
        private final int originZ;
        private final int minY;
        private final long seed;
        private final int[] surface = new int[256];
        private final int[] water = new int[256];
        private final TerrainType[] terrain = new TerrainType[256];
        private final int[] biome = new int[256];
        private final BlockStateEntry air;
        private final BlockStateEntry bedrock;
        private final BlockStateEntry stone;
        private final BlockStateEntry deepslate;
        private final BlockStateEntry dirt;
        private final BlockStateEntry grass;
        private final BlockStateEntry sand;
        private final BlockStateEntry gravel;
        private final BlockStateEntry snow;
        private final BlockStateEntry waterBlock;

        ChunkColumns(WorldModel model, BlockCatalog catalog, int chunkX, int chunkZ, int minY, int maxY) {
            this.originX = chunkX * 16;
            this.originZ = chunkZ * 16;
            this.minY = minY;
            this.seed = model.generation().seed();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int i = z * 16 + x;
                    int wx = originX + x;
                    int wz = originZ + z;
                    surface[i] = Math.clamp((int) Math.floor(model.height(wx, wz)), minY + 1, maxY - 1);
                    water[i] = Math.clamp(model.waterLevel(wx, wz), minY, maxY);
                    terrain[i] = model.terrain(wx, wz);
                    biome[i] = model.biomeIndex(wx, wz);
                }
            }
            air = catalog.state(BlockCatalog.Block.AIR);
            bedrock = catalog.state(BlockCatalog.Block.BEDROCK);
            stone = catalog.state(BlockCatalog.Block.STONE);
            deepslate = catalog.state(BlockCatalog.Block.DEEPSLATE);
            dirt = catalog.state(BlockCatalog.Block.DIRT);
            grass = catalog.state(BlockCatalog.Block.GRASS_BLOCK);
            sand = catalog.state(BlockCatalog.Block.SAND);
            gravel = catalog.state(BlockCatalog.Block.GRAVEL);
            snow = catalog.state(BlockCatalog.Block.SNOW_BLOCK);
            waterBlock = catalog.state(BlockCatalog.Block.WATER);
        }

        int biomeIndex(int x, int z) {
            return biome[(z - originZ) * 16 + (x - originX)];
        }

        int surface(int column) {
            return surface[column];
        }

        int water(int column) {
            return water[column];
        }

        BlockStateEntry blockAt(int x, int y, int z) {
            int i = (z - originZ) * 16 + (x - originX);
            if (y == minY) {
                return bedrock;
            }
            int top = surface[i];
            if (y > top) {
                return y < water[i] ? waterBlock : air;
            }
            int depth = top - y;
            boolean submerged = top + 1 < water[i];
            BlockStateEntry block = switch (terrain[i]) {
                case GRASS -> depth == 0 ? (submerged ? dirt : grass) : depth <= SUBSURFACE_DEPTH ? dirt : stone;
                case DIRT -> depth <= SUBSURFACE_DEPTH ? dirt : stone;
                case STONE -> stone;
                case SAND -> depth <= SUBSURFACE_DEPTH ? sand : stone;
                case GRAVEL -> depth <= SUBSURFACE_DEPTH ? gravel : stone;
                case SNOW -> depth == 0 ? snow : depth <= SUBSURFACE_DEPTH ? dirt : stone;
            };
            return block == stone && deepslateAt(x, y, z) ? deepslate : block;
        }

        boolean deepslateAt(int x, int y, int z) {
            if (y < DEEPSLATE_TOP) {
                return true;
            }
            int above = y - DEEPSLATE_TOP;
            return above < DEEPSLATE_BLEND
                    && GenHash.unit(GenHash.hash(seed, GenHash.DEEPSLATE, x, y, z)) * DEEPSLATE_BLEND < DEEPSLATE_BLEND - above;
        }
    }
}
