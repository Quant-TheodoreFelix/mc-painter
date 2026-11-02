package space.qu4nt.thirdparty.mcpainter.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkData;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RawChunk;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionFile;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionPos;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionWriter;
import space.qu4nt.thirdparty.mcpainter.anvil.region.UnsupportedCompressionException;
import space.qu4nt.thirdparty.mcpainter.anvil.world.Dimension;
import space.qu4nt.thirdparty.mcpainter.anvil.world.MinecraftWorld;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtWriter;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;

class FixtureWorldsTest {

    private static final String PROPERTY = "mcpainter.fixtures";

    @TempDir
    Path scratch;

    static Stream<Path> worlds() throws IOException {
        String root = System.getProperty(PROPERTY);
        if (root == null || root.isBlank()) {
            return Stream.of(Path.of(""));
        }
        List<Path> worlds = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(Path.of(root))) {
            for (Path entry : entries) {
                if (Files.isRegularFile(entry.resolve("level.dat"))) {
                    worlds.add(entry);
                }
            }
        }
        worlds.sort(null);
        return worlds.stream();
    }

    private static byte[] encode(RootTag root) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtWriter.write(out, root);
        return out.toByteArray();
    }

    @ParameterizedTest
    @MethodSource("worlds")
    void readsEveryChunkAndRewritesRegionsLosslessly(Path worldDir) throws IOException {
        assumeTrue(!worldDir.toString().isEmpty(), "set -D" + PROPERTY + " to run fixture tests");
        MinecraftWorld world = MinecraftWorld.open(worldDir);
        Map<String, Integer> stats = new TreeMap<>();
        List<String> failures = new ArrayList<>();

        for (Dimension dimension : world.listDimensions()) {
            for (RegionPos pos : world.listRegions(dimension)) {
                Path file = world.regionPath(dimension, pos);
                RegionWriter copy = new RegionWriter();
                try (RegionFile region = RegionFile.open(file)) {
                    for (int z = 0; z < RegionPos.CHUNKS_PER_SIDE; z++) {
                        for (int x = 0; x < RegionPos.CHUNKS_PER_SIDE; x++) {
                            String where = dimension + " " + pos.fileName() + " (" + x + ", " + z + ")";
                            switch (region.state(x, z)) {
                                case ABSENT -> {
                                    continue;
                                }
                                case CORRUPT -> {
                                    failures.add(where + " corrupt: " + region.problem(x, z).orElse(""));
                                    continue;
                                }
                                case PRESENT -> {
                                }
                            }
                            RawChunk raw = region.readRaw(x, z).orElseThrow();
                            copy.put(x, z, raw);
                            stats.merge("compression " + raw.compressionId(), 1, Integer::sum);
                            RootTag root;
                            try {
                                root = raw.decode(NbtLimits.DEFAULT);
                            } catch (UnsupportedCompressionException e) {
                                stats.merge("undecodable", 1, Integer::sum);
                                continue;
                            }
                            try {
                                ChunkData data = ChunkData.parse(root);
                                stats.merge("dataVersion " + data.dataVersion(), 1, Integer::sum);
                                stats.merge("status " + data.status(), 1, Integer::sum);
                                if (!Arrays.equals(encode(data.toNbt()), encode(root))) {
                                    failures.add(where + " re-encoded chunk bytes differ from source");
                                }
                            } catch (IOException | RuntimeException e) {
                                failures.add(where + " " + e);
                            }
                        }
                    }
                }
                Path rewritten = Files.createDirectories(scratch.resolve(dimension.id().namespace())
                        .resolve(dimension.id().path())).resolve(pos.fileName());
                copy.writeTo(rewritten);
                try (RegionFile original = RegionFile.open(file); RegionFile again = RegionFile.open(rewritten)) {
                    for (int z = 0; z < RegionPos.CHUNKS_PER_SIDE; z++) {
                        for (int x = 0; x < RegionPos.CHUNKS_PER_SIDE; x++) {
                            if (original.state(x, z) == RegionFile.ChunkState.PRESENT) {
                                assertEquals(original.readRaw(x, z), again.readRaw(x, z));
                            }
                        }
                    }
                }
            }
        }
        System.out.println(worldDir.getFileName() + " dataVersion=" + world.level().dataVersion()
                + " version=" + world.level().versionName()
                + " dimensions=" + world.listDimensions() + " " + stats);
        failures.stream().limit(20).forEach(f -> System.out.println("  FAIL " + f));
        assertFalse(stats.isEmpty(), "no chunks found");
        assertEquals(List.of(), failures.stream().limit(20).toList());
    }
}
