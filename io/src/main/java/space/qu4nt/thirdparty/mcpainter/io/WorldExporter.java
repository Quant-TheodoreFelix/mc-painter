package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public final class WorldExporter {

    private static final int CHUNKS_PER_TILE = Tile.SIZE / 16;
    private static final int SUBSURFACE_DEPTH = 3;
    static final int DEEPSLATE_TOP = 0;
    static final int DEEPSLATE_BLEND = 8;

    public record Result(int regionsWritten, int chunksReplaced, int chunksCreated, int entityChunksRemoved,
            int poiChunksRemoved) {
    }

    private WorldExporter() {
    }

    public static Result export(WorldModel model, Path worldDirectory) throws IOException {
        MinecraftWorld world = MinecraftWorld.open(worldDirectory);
        WorldLock lock = world.lock();
        try {
            return export(model, world);
        } finally {
            lock.close();
        }
    }

    private static Result export(WorldModel model, MinecraftWorld world) throws IOException {
        Dimension dimension = Dimension.OVERWORLD;
        ChunkData prototype = Prototypes.find(world, dimension)
                .orElseThrow(() -> new IOException("template world has no full chunk at its data version"));
        int minY = Prototypes.minY(prototype);
        int maxY = Prototypes.maxY(prototype);
        if (model.minY() < minY || model.maxY() > maxY) {
            throw new IOException("model height range " + model.minY() + ".." + model.maxY()
                    + " does not fit template range " + minY + ".." + maxY);
        }
        BlockCatalog catalog = new BlockCatalog(world.level().dataVersion());
        List<ResourceLocation> biomes = new ArrayList<>();
        for (String id : model.biomes()) {
            biomes.add(ResourceLocation.parse(id));
        }

        Map<RegionPos, List<int[]>> regions = new TreeMap<>(
                (a, b) -> a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x()));
        for (TilePos tile : model.tilePositions()) {
            for (int cz = 0; cz < CHUNKS_PER_TILE; cz++) {
                for (int cx = 0; cx < CHUNKS_PER_TILE; cx++) {
                    int chunkX = tile.x() * CHUNKS_PER_TILE + cx;
                    int chunkZ = tile.z() * CHUNKS_PER_TILE + cz;
                    regions.computeIfAbsent(RegionPos.ofChunk(chunkX, chunkZ), k -> new ArrayList<>())
                            .add(new int[] {chunkX, chunkZ});
                }
            }
        }

        int timestamp = (int) (System.currentTimeMillis() / 1000);
        int replaced = 0;
        int created = 0;
        int entitiesRemoved = 0;
        int poiRemoved = 0;
        for (Map.Entry<RegionPos, List<int[]>> entry : regions.entrySet()) {
            RegionPos pos = entry.getKey();
            Path path = world.regionPath(dimension, pos);
            Files.createDirectories(path.getParent());
            RegionWriter writer;
            RegionFile existing = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? RegionFile.open(path) : null;
            try {
                writer = existing == null ? new RegionWriter() : RegionWriter.copyOf(existing);
                for (int[] chunk : entry.getValue()) {
                    int localX = chunk[0] - pos.x() * RegionPos.CHUNKS_PER_SIDE;
                    int localZ = chunk[1] - pos.z() * RegionPos.CHUNKS_PER_SIDE;
                    Optional<ChunkData> own = existing == null ? Optional.empty()
                            : Prototypes.fullChunk(existing, localX, localZ, world.level().dataVersion());
                    ChunkColumns columns = new ChunkColumns(model, catalog, chunk[0], chunk[1], minY, maxY);
                    ChunkFeatures features = ChunkFeatures.generate(model, catalog, columns::blockAt, chunk[0],
                            chunk[1], minY, maxY);
                    RootTag rebuilt = ChunkRebuilder.rebuild(own.orElse(prototype), chunk[0], chunk[1],
                            (x, y, z) -> {
                                BlockStateEntry feature = features.at(x, y, z);
                                return feature != null ? feature : columns.blockAt(x, y, z);
                            }, catalog::traits,
                            (x, y, z) -> biomes.get(columns.biomeIndex(x, z)));
                    writer.put(localX, localZ, RawChunk.encode(rebuilt, ChunkCompression.ZLIB, timestamp));
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
            entitiesRemoved += removeChunks(world.entitiesDirectory(dimension).resolve(pos.fileName()), pos,
                    entry.getValue());
            poiRemoved += removeChunks(world.poiDirectory(dimension).resolve(pos.fileName()), pos, entry.getValue());
        }
        return new Result(regions.size(), replaced, created, entitiesRemoved, poiRemoved);
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
