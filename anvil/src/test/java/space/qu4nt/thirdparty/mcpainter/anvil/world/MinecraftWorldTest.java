package space.qu4nt.thirdparty.mcpainter.anvil.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionPos;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtCompression;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtIo;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;

class MinecraftWorldTest {

    @TempDir
    Path root;

    private void writeLevel(int dataVersion) throws IOException {
        CompoundTag data = CompoundTag.builder()
                .put("DataVersion", new IntTag(dataVersion))
                .put("LevelName", new StringTag("테스트 월드"))
                .put("Version", CompoundTag.builder().put("Name", new StringTag("test")).build())
                .build();
        try (OutputStream out = Files.newOutputStream(root.resolve(LevelData.FILE_NAME))) {
            NbtIo.write(out, RootTag.of(CompoundTag.builder().put("Data", data).build()), NbtCompression.GZIP);
        }
    }

    private void touchRegion(String directory, String name) throws IOException {
        Path dir = Files.createDirectories(root.resolve(directory));
        Files.createFile(dir.resolve(name));
    }

    @Test
    void readsLevelData() throws IOException {
        writeLevel(3953);
        MinecraftWorld world = MinecraftWorld.open(root);
        assertEquals(3953, world.level().dataVersion());
        assertEquals("테스트 월드", world.level().levelName());
        assertEquals("test", world.level().versionName());
    }

    @Test
    void resolvesLegacyLayout() throws IOException {
        writeLevel(3953);
        touchRegion("region", "r.0.0.mca");
        touchRegion("DIM-1/region", "r.-1.0.mca");
        touchRegion("DIM1/region", "r.0.0.mca");
        touchRegion("dimensions/mymod/sky/high/region", "r.0.0.mca");
        MinecraftWorld world = MinecraftWorld.open(root);

        assertFalse(world.usesDimensionsFolder());
        assertEquals(world.root().resolve("region"), world.regionDirectory(Dimension.OVERWORLD));
        assertEquals(world.root().resolve("DIM-1/region"), world.regionDirectory(Dimension.THE_NETHER));
        assertEquals(world.root().resolve("DIM1/entities"), world.entitiesDirectory(Dimension.THE_END));
        assertEquals(
                List.of("minecraft:overworld", "minecraft:the_end", "minecraft:the_nether", "mymod:sky/high"),
                world.listDimensions().stream().map(Dimension::toString).toList());
    }

    @Test
    void resolvesDimensionsFolderLayout() throws IOException {
        writeLevel(DataVersions.DIMENSIONS_FOLDER);
        touchRegion("dimensions/minecraft/overworld/region", "r.0.0.mca");
        touchRegion("dimensions/minecraft/the_nether/region", "r.0.0.mca");
        MinecraftWorld world = MinecraftWorld.open(root);

        assertTrue(world.usesDimensionsFolder());
        assertEquals(world.root().resolve("dimensions/minecraft/overworld/region"),
                world.regionDirectory(Dimension.OVERWORLD));
        assertEquals(List.of(Dimension.OVERWORLD, Dimension.THE_NETHER), world.listDimensions());
    }

    @Test
    void rejectsUnsupportedVersions() throws IOException {
        writeLevel(DataVersions.MIN_SUPPORTED - 1);
        assertThrows(UnsupportedVersionException.class, () -> MinecraftWorld.open(root));
        writeLevel(DataVersions.MAX_SUPPORTED + 1);
        assertThrows(UnsupportedVersionException.class, () -> MinecraftWorld.open(root));
    }

    @Test
    void rejectsMissingOrSymlinkedLevelData() throws IOException {
        assertThrows(WorldFormatException.class, () -> MinecraftWorld.open(root));
        Path elsewhere = Files.createTempFile(root.getParent(), "level", ".dat");
        Files.createSymbolicLink(root.resolve(LevelData.FILE_NAME), elsewhere);
        assertThrows(IOException.class, () -> MinecraftWorld.open(root));
        Files.delete(elsewhere);
    }

    @Test
    void rejectsLevelDataWithoutDataVersion() throws IOException {
        try (OutputStream out = Files.newOutputStream(root.resolve(LevelData.FILE_NAME))) {
            NbtIo.write(out, RootTag.of(CompoundTag.builder().put("Data", CompoundTag.empty()).build()),
                    NbtCompression.GZIP);
        }
        assertThrows(WorldFormatException.class, () -> MinecraftWorld.open(root));
    }

    @Test
    void listsOnlyRealRegionFiles() throws IOException {
        writeLevel(3953);
        touchRegion("region", "r.1.0.mca");
        touchRegion("region", "r.-2.5.mca");
        touchRegion("region", "r.0.0.mcr");
        touchRegion("region", "notes.txt");
        Path outside = Files.createTempFile(root.getParent(), "outside", ".mca");
        Files.createSymbolicLink(root.resolve("region/r.9.9.mca"), outside);
        MinecraftWorld world = MinecraftWorld.open(root);

        assertEquals(List.of(new RegionPos(-2, 5), new RegionPos(1, 0)), world.listRegions(Dimension.OVERWORLD));
        assertEquals(List.of(), world.listRegions(Dimension.THE_END));
        Files.delete(outside);
    }

    @Test
    void refusesSymlinkedRegionDirectory() throws IOException {
        writeLevel(3953);
        Path outside = Files.createTempDirectory(root.getParent(), "outside");
        Files.createSymbolicLink(root.resolve("region"), outside);
        MinecraftWorld world = MinecraftWorld.open(root);
        assertThrows(IOException.class, () -> world.listRegions(Dimension.OVERWORLD));
        Files.delete(outside);
    }

    @Test
    void sessionLockIsExclusive() throws IOException {
        writeLevel(3953);
        MinecraftWorld world = MinecraftWorld.open(root);
        assertFalse(world.isInUse());
        WorldLock lock = world.lock();
        try {
            assertTrue(world.isInUse());
            assertThrows(WorldInUseException.class, world::lock);
        } finally {
            lock.close();
        }
        assertFalse(world.isInUse());
    }

    @Test
    void resourceLocationsAreValidatedStrictly() {
        assertEquals(new ResourceLocation("minecraft", "stone"), ResourceLocation.parse("stone"));
        assertEquals(new ResourceLocation("my_mod", "a/b.c"), ResourceLocation.parse("my_mod:a/b.c"));
        for (String invalid : List.of("", ":", "a:", ":b", "A:b", "a:B", "a:b/", "a:/b", "a:b//c",
                "a:..", "a:b/../c", "..:b", "a:b:c", "a:b\\c", "a:b c")) {
            assertThrows(IllegalArgumentException.class, () -> ResourceLocation.parse(invalid), invalid);
        }
    }
}
