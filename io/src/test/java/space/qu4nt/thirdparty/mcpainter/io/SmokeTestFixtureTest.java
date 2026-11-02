package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkData;
import space.qu4nt.thirdparty.mcpainter.anvil.chunk.ChunkSection;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionFile;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionPos;
import space.qu4nt.thirdparty.mcpainter.anvil.world.Dimension;
import space.qu4nt.thirdparty.mcpainter.anvil.world.MinecraftWorld;

class SmokeTestFixtureTest {

    @TempDir
    Path scratch;

    static Stream<Path> worlds() throws IOException {
        String root = System.getProperty("mcpainter.fixtures");
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

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("worlds")
    void smokeTestRewritesCopyOfRealWorld(Path fixture) throws IOException {
        assumeTrue(!fixture.toString().isEmpty(), "set MCPAINTER_FIXTURES to run fixture tests");
        Path world = scratch.resolve(fixture.getFileName().toString());
        copyTree(fixture, world);

        List<String> report = SmokeTest.run(world);
        report.forEach(System.out::println);

        MinecraftWorld reopened = MinecraftWorld.open(world);
        try (RegionFile region = RegionFile.open(reopened.regionPath(Dimension.OVERWORLD, new RegionPos(0, 0)))) {
            ChunkData origin = ChunkData.parse(region.readChunk(0, 0).orElseThrow());
            ChunkSection ground = origin.sections().stream().filter(s -> s.y() == 3).findFirst().orElseThrow();
            assertEquals(SmokeTest.GOLD.id(), ground.blocks().orElseThrow().get(ChunkSection.blockIndex(0, 13, 0)).id());
            assertEquals(SmokeTest.GRASS, ground.blocks().orElseThrow().get(ChunkSection.blockIndex(5, 12, 5)));
            assertEquals(reopened.level().dataVersion(), origin.dataVersion());
        }
        try (RegionFile created = RegionFile.open(reopened.regionPath(Dimension.OVERWORLD, new RegionPos(1, 0)))) {
            ChunkData chunk = ChunkData.parse(created.readChunk(8, 0).orElseThrow());
            assertEquals(SmokeTest.CREATE_MIN_CHUNK_X, chunk.x());
            assertEquals(reopened.level().dataVersion(), chunk.dataVersion());
        }
        Path entities = reopened.entitiesDirectory(Dimension.OVERWORLD).resolve("r.0.0.mca");
        if (Files.exists(entities)) {
            try (RegionFile region = RegionFile.open(entities)) {
                assertFalse(region.readRaw(0, 0).isPresent());
            }
        }
    }
}
