package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.ListTag;
import space.qu4nt.thirdparty.mcpainter.nbt.LongTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtCompression;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtIo;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;
import space.qu4nt.thirdparty.mcpainter.nbt.StringTag;
import space.qu4nt.thirdparty.mcpainter.nbt.TagType;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class ProjectFileTest {

    @TempDir
    Path dir;

    private static WorldModel sample() {
        WorldModel model = WorldModel.builder(-64, 320, 63).maxTiles(10)
                .generation(new GenerationSettings(-1234567890123L)).build();
        try (Edit edit = model.beginEdit("setup")) {
            edit.addTile(new TilePos(-1, 0), 70, TerrainType.GRASS);
            edit.addTile(new TilePos(3, -2), 40, TerrainType.SAND);
            edit.setHeight(-5, 7, 123.25f);
            edit.setTerrain(-5, 7, TerrainType.SNOW);
            edit.setWaterLevel(-5, 7, -20);
            edit.setBiome(-5, 7, "minecraft:snowy_slopes");
            edit.setBiome(400, -200, "mymod:glow/caves");
        }
        return model;
    }

    @Test
    void roundTripsEveryColumnAttribute() throws IOException {
        WorldModel original = sample();
        Path file = dir.resolve("world" + ProjectFile.EXTENSION);
        ProjectFile.save(original, file);
        WorldModel loaded = ProjectFile.load(file);

        assertEquals(original.minY(), loaded.minY());
        assertEquals(original.maxY(), loaded.maxY());
        assertEquals(original.seaLevel(), loaded.seaLevel());
        assertEquals(original.maxTiles(), loaded.maxTiles());
        assertEquals(-1234567890123L, loaded.generation().seed());
        assertEquals(original.biomes(), loaded.biomes());
        assertEquals(original.tilePositions(), loaded.tilePositions());
        for (TilePos pos : original.tilePositions()) {
            Tile a = original.tile(pos).orElseThrow();
            Tile b = loaded.tile(pos).orElseThrow();
            assertArrayEquals(a.heights(), b.heights());
            assertArrayEquals(a.terrainOrdinals(), b.terrainOrdinals());
            assertArrayEquals(a.waterLevels(), b.waterLevels());
            assertArrayEquals(a.biomeIndices(), b.biomeIndices());
        }
        assertEquals(123.25f, loaded.height(-5, 7));
        assertEquals(TerrainType.SNOW, loaded.terrain(-5, 7));
        assertEquals(-20, loaded.waterLevel(-5, 7));
        assertEquals("mymod:glow/caves", loaded.biome(400, -200));
        assertEquals(false, loaded.canUndo());
    }

    @Test
    void rejectsBadContainers() throws IOException {
        Path file = dir.resolve("bad" + ProjectFile.EXTENSION);
        ProjectFile.save(sample(), file);
        byte[] good = Files.readAllBytes(file);

        assertRejected(Arrays.copyOf(good, 5));
        byte[] magic = good.clone();
        magic[0] = 'X';
        assertRejected(magic);
        byte[] version = good.clone();
        version[9] = 9;
        assertRejected(version);
        assertRejected(Arrays.copyOf(good, good.length - 5));
        byte[] hugeLength = good.clone();
        hugeLength[11] = 0x7F;
        assertRejected(hugeLength);
        for (int offset = 15; offset < 15 + 40; offset++) {
            if (offset >= 19 && offset <= 24) {
                continue;
            }
            byte[] corrupt = good.clone();
            corrupt[offset] ^= 0x5A;
            int at = offset;
            Path probe = Files.write(dir.resolve("probe" + ProjectFile.EXTENSION), corrupt);
            assertThrows(ProjectFormatException.class, () -> ProjectFile.load(probe), () -> "offset " + at);
        }
        int lastPayloadByte = good.length - 6;
        byte[] badCrc = good.clone();
        badCrc[lastPayloadByte - 5] ^= 0x01;
        assertRejected(badCrc);
    }

    @Test
    void rejectsStructurallyInvalidRecords() throws IOException {
        assertRejected(container(record(ProjectFile.RECORD_TILE, tile(0, 0, 0, 0)), end()));
        assertRejected(container(record(ProjectFile.RECORD_HEADER, header(1)), record(ProjectFile.RECORD_HEADER, header(1)), end()));
        assertRejected(container(record(ProjectFile.RECORD_HEADER, header(1)),
                record(ProjectFile.RECORD_TILE, tile(0, 0, 0, 0)), record(ProjectFile.RECORD_TILE, tile(1, 0, 0, 0)), end()));
        assertRejected(container(record(ProjectFile.RECORD_HEADER, header(5)),
                record(ProjectFile.RECORD_TILE, tile(0, 0, 0, 0)), record(ProjectFile.RECORD_TILE, tile(0, 0, 0, 0)), end()));
        assertRejected(container(record(ProjectFile.RECORD_HEADER, header(5)),
                record(ProjectFile.RECORD_TILE, tile(0, 0, 7, 0)), end()));
        assertRejected(container(record(ProjectFile.RECORD_HEADER, header(5)),
                record(ProjectFile.RECORD_TILE, tile(0, 0, 0, Float.floatToIntBits(Float.NaN))), end()));
        assertRejected(container(record(ProjectFile.RECORD_HEADER, header(5)), record(9, header(5)), end()));
        assertRejected(container(record(ProjectFile.RECORD_HEADER, header(ProjectFile.HARD_MAX_TILES + 1)), end()));
        assertRejected(container(record(ProjectFile.RECORD_HEADER, header(5))));
        assertRejected(container(ProjectFile.FORMAT_VERSION, record(ProjectFile.RECORD_HEADER, header(5, null)),
                record(ProjectFile.RECORD_TILE, tile(0, 0, 0, Float.floatToIntBits(70))), end()));
        byte[] valid = container(record(ProjectFile.RECORD_HEADER, header(5)),
                record(ProjectFile.RECORD_TILE, tile(0, 0, 0, Float.floatToIntBits(70))), end());
        Path file = Files.write(dir.resolve("ok" + ProjectFile.EXTENSION), valid);
        assertEquals(70f, ProjectFile.load(file).height(5, 5));
        assertEquals(99, ProjectFile.load(file).generation().seed());
    }

    @Test
    void readsVersionOneFiles() throws IOException {
        byte[] v1 = container(1, record(ProjectFile.RECORD_HEADER, header(5, null)),
                record(ProjectFile.RECORD_TILE, tile(0, 0, 0, Float.floatToIntBits(70))), end());
        WorldModel loaded = ProjectFile.load(Files.write(dir.resolve("v1" + ProjectFile.EXTENSION), v1));
        assertEquals(70f, loaded.height(5, 5));
        assertEquals(GenerationSettings.DEFAULT, loaded.generation());
        assertRejected(container(0, record(ProjectFile.RECORD_HEADER, header(5)), end()));
        assertRejected(container(ProjectFile.FORMAT_VERSION + 1, record(ProjectFile.RECORD_HEADER, header(5)), end()));
    }

    @Test
    void refusesSymlinks() throws IOException {
        Path real = dir.resolve("real" + ProjectFile.EXTENSION);
        ProjectFile.save(sample(), real);
        Path link = Files.createSymbolicLink(dir.resolve("link" + ProjectFile.EXTENSION), real);
        assertThrows(IOException.class, () -> ProjectFile.load(link));
        assertThrows(IOException.class, () -> ProjectFile.save(sample(), link));
    }

    private void assertRejected(byte[] bytes) throws IOException {
        Path file = Files.write(dir.resolve("case" + ProjectFile.EXTENSION), bytes);
        assertThrows(ProjectFormatException.class, () -> ProjectFile.load(file));
    }

    private static CompoundTag header(int maxTiles) {
        return header(maxTiles, 99L);
    }

    private static CompoundTag header(int maxTiles, Long seed) {
        CompoundTag.Builder builder = CompoundTag.builder()
                .put("minY", new IntTag(-64))
                .put("maxY", new IntTag(320))
                .put("seaLevel", new IntTag(63))
                .put("maxTiles", new IntTag(maxTiles))
                .put("terrainTypes", ListTag.of(TagType.STRING, new StringTag("grass")))
                .put("biomes", ListTag.of(TagType.STRING, new StringTag("minecraft:plains")));
        if (seed != null) {
            builder.put("seed", new LongTag(seed));
        }
        return builder.build();
    }

    private static CompoundTag tile(int x, int z, int terrainIndex, int heightBits) {
        int[] heights = new int[Tile.AREA];
        Arrays.fill(heights, heightBits);
        byte[] terrain = new byte[Tile.AREA];
        Arrays.fill(terrain, (byte) terrainIndex);
        int[] water = new int[Tile.AREA];
        Arrays.fill(water, 63);
        return CompoundTag.builder()
                .put("x", new IntTag(x))
                .put("z", new IntTag(z))
                .put("heights", IntArrayTag.of(heights))
                .put("terrain", ByteArrayTag.of(terrain))
                .put("water", IntArrayTag.of(water))
                .put("biomes", IntArrayTag.of(new int[Tile.AREA]))
                .build();
    }

    private static byte[] record(int type, CompoundTag payload) throws IOException {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        NbtIo.write(encoded, RootTag.of(payload), NbtCompression.GZIP);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(type);
        out.writeInt(encoded.size());
        encoded.writeTo(out);
        return bytes.toByteArray();
    }

    private static byte[] end() {
        return new byte[] {0, 0, 0, 0, 0};
    }

    private static byte[] container(byte[]... records) throws IOException {
        return container(ProjectFile.FORMAT_VERSION, records);
    }

    private static byte[] container(int version, byte[]... records) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(ProjectFile.MAGIC);
        out.writeShort(version);
        for (byte[] record : records) {
            out.write(record);
        }
        return bytes.toByteArray();
    }
}
