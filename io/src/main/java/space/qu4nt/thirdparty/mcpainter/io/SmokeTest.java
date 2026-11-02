package space.qu4nt.thirdparty.mcpainter.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

public final class SmokeTest {

    public static final int OVERWRITE_MIN_CHUNK = 0;
    public static final int OVERWRITE_MAX_CHUNK = 3;
    public static final int CREATE_MIN_CHUNK_X = 40;
    public static final int CREATE_MAX_CHUNK_X = 41;
    public static final int CREATE_MIN_CHUNK_Z = 0;
    public static final int CREATE_MAX_CHUNK_Z = 1;
    public static final int GROUND_Y = 60;

    static final BlockStateEntry AIR = plain("air");
    static final BlockStateEntry BEDROCK = plain("bedrock");
    static final BlockStateEntry STONE = plain("stone");
    static final BlockStateEntry GRASS = BlockStateEntry.of(ResourceLocation.minecraft("grass_block"),
            Map.of("snowy", "false"));
    static final BlockStateEntry GOLD = plain("gold_block");
    static final BlockStateEntry GLASS = plain("glass");
    static final BlockStateEntry DIAMOND = plain("diamond_block");

    private SmokeTest() {
    }

    static BlockTraits traits(BlockStateEntry block) {
        return block.equals(AIR) ? BlockTraits.AIR : BlockTraits.SOLID;
    }

    private static BlockStateEntry plain(String path) {
        return BlockStateEntry.withoutProperties(ResourceLocation.minecraft(path));
    }

    static BlockStateEntry blockAt(int x, int y, int z, int minY) {
        if (y == minY) {
            return BEDROCK;
        }
        if (y < GROUND_Y) {
            return STONE;
        }
        if (y == GROUND_Y) {
            return GRASS;
        }
        int overwriteMax = (OVERWRITE_MAX_CHUNK + 1) * 16 - 1;
        if (x >= 0 && x <= overwriteMax && z >= 0 && z <= overwriteMax) {
            boolean border = x == 0 || z == 0 || x == overwriteMax || z == overwriteMax;
            if (border && y == GROUND_Y + 1) {
                return GOLD;
            }
            int center = overwriteMax / 2;
            if ((x == center || x == center + 1) && (z == center || z == center + 1) && y <= GROUND_Y + 20) {
                return GLASS;
            }
            return AIR;
        }
        int pillarX = CREATE_MIN_CHUNK_X * 16 + 15;
        int pillarZ = CREATE_MIN_CHUNK_Z * 16 + 15;
        if ((x == pillarX || x == pillarX + 1) && (z == pillarZ || z == pillarZ + 1) && y <= GROUND_Y + 20) {
            return DIAMOND;
        }
        return AIR;
    }

    public static List<String> run(Path worldDirectory) throws IOException {
        MinecraftWorld world = MinecraftWorld.open(worldDirectory);
        WorldLock lock = world.lock();
        try {
            return apply(world);
        } finally {
            lock.close();
        }
    }

    private static List<String> apply(MinecraftWorld world) throws IOException {
        Dimension dimension = Dimension.OVERWORLD;
        List<String> report = new ArrayList<>();
        report.add("world " + world.root() + " dataVersion=" + world.level().dataVersion()
                + " version=" + world.level().versionName());

        Map<RegionPos, List<int[]>> targets = new LinkedHashMap<>();
        for (int cz = OVERWRITE_MIN_CHUNK; cz <= OVERWRITE_MAX_CHUNK; cz++) {
            for (int cx = OVERWRITE_MIN_CHUNK; cx <= OVERWRITE_MAX_CHUNK; cx++) {
                targets.computeIfAbsent(RegionPos.ofChunk(cx, cz), k -> new ArrayList<>()).add(new int[] {cx, cz});
            }
        }
        for (int cz = CREATE_MIN_CHUNK_Z; cz <= CREATE_MAX_CHUNK_Z; cz++) {
            for (int cx = CREATE_MIN_CHUNK_X; cx <= CREATE_MAX_CHUNK_X; cx++) {
                targets.computeIfAbsent(RegionPos.ofChunk(cx, cz), k -> new ArrayList<>()).add(new int[] {cx, cz});
            }
        }

        ChunkData prototype = Prototypes.find(world, dimension)
                .orElseThrow(() -> new IOException("no full chunk at the world's data version to use as a prototype"));
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        int overwritten = 0;
        int created = 0;

        for (Map.Entry<RegionPos, List<int[]>> entry : targets.entrySet()) {
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
                    ChunkData base = own.orElse(prototype);
                    int minY = Prototypes.minY(base);
                    RootTag rebuilt = ChunkRebuilder.rebuild(base, chunk[0], chunk[1],
                            (x, y, z) -> blockAt(x, y, z, minY), SmokeTest::traits);
                    writer.put(localX, localZ, RawChunk.encode(rebuilt, ChunkCompression.ZLIB, timestamp));
                    if (own.isPresent()) {
                        overwritten++;
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
            removeEntityChunks(world, dimension, pos, entry.getValue(), report);
        }

        report.add("overwrote " + overwritten + " chunks, created " + created + " chunks");
        int overwriteCenter = (OVERWRITE_MAX_CHUNK + 1) * 8;
        report.add("look at: /tp @s " + overwriteCenter + " " + (GROUND_Y + 2) + " " + (overwriteCenter + 6)
                + "  (grass square with gold border and glass pillar)");
        report.add("look at: /tp @s " + (CREATE_MIN_CHUNK_X * 16 + 16) + " " + (GROUND_Y + 2) + " "
                + (CREATE_MIN_CHUNK_Z * 16 + 22) + "  (new chunks with diamond pillar)");
        return report;
    }

    private static void removeEntityChunks(MinecraftWorld world, Dimension dimension, RegionPos pos,
            List<int[]> chunks, List<String> report) throws IOException {
        Path path = world.entitiesDirectory(dimension).resolve(pos.fileName());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        RegionWriter writer;
        int removed = 0;
        try (RegionFile entities = RegionFile.open(path)) {
            writer = RegionWriter.copyOf(entities);
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
            report.add("removed entities from " + removed + " chunks in " + pos.fileName());
        }
    }
}
