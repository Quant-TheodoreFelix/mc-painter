package space.qu4nt.thirdparty.mcpainter.anvil.region;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.nbt.ByteArrayTag;
import space.qu4nt.thirdparty.mcpainter.nbt.CompoundTag;
import space.qu4nt.thirdparty.mcpainter.nbt.IntTag;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;
import space.qu4nt.thirdparty.mcpainter.nbt.RootTag;

class RegionFileTest {

    @TempDir
    Path dir;

    private static RootTag chunkTag(int x, int z) {
        return RootTag.of(CompoundTag.builder()
                .put("DataVersion", new IntTag(4786))
                .put("xPos", new IntTag(x))
                .put("zPos", new IntTag(z))
                .build());
    }

    @Test
    void writesAndReadsChunksWithEachCodec() throws IOException {
        Path file = dir.resolve("r.-1.2.mca");
        new RegionWriter()
                .put(0, 0, RawChunk.encode(chunkTag(-32, 64), ChunkCompression.ZLIB, 100))
                .put(31, 0, RawChunk.encode(chunkTag(-1, 64), ChunkCompression.GZIP, 200))
                .put(5, 31, RawChunk.encode(chunkTag(-27, 95), ChunkCompression.NONE, 300))
                .writeTo(file);

        assertEquals(0, Files.size(file) % RegionFile.SECTOR_BYTES);
        try (RegionFile region = RegionFile.open(file)) {
            assertEquals(new RegionPos(-1, 2), region.pos());
            assertEquals(RegionFile.ChunkState.PRESENT, region.state(0, 0));
            assertEquals(RegionFile.ChunkState.ABSENT, region.state(1, 0));
            assertEquals(chunkTag(-32, 64), region.readChunk(0, 0).orElseThrow());
            assertEquals(chunkTag(-1, 64), region.readChunk(31, 0).orElseThrow());
            assertEquals(chunkTag(-27, 95), region.readChunk(5, 31).orElseThrow());
            assertEquals(200, region.timestamp(31, 0));
            assertEquals(Optional.empty(), region.readRaw(1, 0));
        }
    }

    @Test
    void rewritingRawChunksIsByteIdentical() throws IOException {
        Path first = dir.resolve("r.0.0.mca");
        new RegionWriter()
                .put(3, 4, RawChunk.encode(chunkTag(3, 4), ChunkCompression.ZLIB, 7))
                .put(9, 9, RawChunk.of(ChunkCompression.CUSTOM.id(), new byte[] {0, 3, 'a', ':', 'b', 1, 2}, 8))
                .put(10, 9, RawChunk.of(ChunkCompression.LZ4.id(), new byte[] {9, 9, 9}, 9))
                .writeTo(first);

        Path secondDir = Files.createDirectory(dir.resolve("copy"));
        Path second = secondDir.resolve("r.0.0.mca");
        RegionWriter copy = new RegionWriter();
        try (RegionFile region = RegionFile.open(first)) {
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    int localX = x;
                    int localZ = z;
                    region.readRaw(x, z).ifPresent(raw -> copy.put(localX, localZ, raw));
                }
            }
            assertThrows(UnsupportedCompressionException.class, () -> region.readChunk(9, 9));
            assertThrows(UnsupportedCompressionException.class, () -> region.readChunk(10, 9));
        }
        copy.writeTo(second);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void oversizedChunksAreStoredExternallyAndCleanedUp() throws IOException {
        byte[] noise = new byte[RegionFile.MAX_INLINE_SECTORS * RegionFile.SECTOR_BYTES];
        new Random(42).nextBytes(noise);
        RootTag big = RootTag.of(CompoundTag.builder().put("noise", ByteArrayTag.of(noise)).build());
        Path file = dir.resolve("r.1.1.mca");
        Path external = dir.resolve("c.34.33.mcc");

        new RegionWriter().put(2, 1, RawChunk.encode(big, ChunkCompression.NONE, 1)).writeTo(file);
        assertTrue(Files.isRegularFile(external));
        assertEquals(3 * RegionFile.SECTOR_BYTES, Files.size(file));
        try (RegionFile region = RegionFile.open(file)) {
            assertEquals(big, region.readChunk(2, 1).orElseThrow());
        }

        new RegionWriter().put(2, 1, RawChunk.encode(chunkTag(34, 33), ChunkCompression.ZLIB, 2)).writeTo(file);
        assertFalse(Files.exists(external));
        try (RegionFile region = RegionFile.open(file)) {
            assertEquals(chunkTag(34, 33), region.readChunk(2, 1).orElseThrow());
        }
    }

    @Test
    void emptyFileHasNoChunks() throws IOException {
        Path file = Files.createFile(dir.resolve("r.0.0.mca"));
        try (RegionFile region = RegionFile.open(file)) {
            assertEquals(RegionFile.ChunkState.ABSENT, region.state(0, 0));
        }
    }

    @Test
    void truncatedHeaderIsRejected() throws IOException {
        Path file = Files.write(dir.resolve("r.0.0.mca"), new byte[100]);
        assertThrows(RegionFormatException.class, () -> RegionFile.open(file));
    }

    @Test
    void invalidFileNameIsRejected() throws IOException {
        Path file = Files.write(dir.resolve("region.dat"), new byte[0]);
        assertThrows(RegionFormatException.class, () -> RegionFile.open(file));
    }

    @Test
    void invalidLocationsAreMarkedCorrupt() throws IOException {
        ByteBuffer data = ByteBuffer.allocate(6 * RegionFile.SECTOR_BYTES);
        location(data, 0, 1, 1);
        location(data, 1, 2, 0);
        location(data, 2, 100, 1);
        location(data, 3, 2, 2);
        location(data, 4, 3, 1);
        location(data, 5, 5, 1);
        Path file = Files.write(dir.resolve("r.0.0.mca"), data.array());

        try (RegionFile region = RegionFile.open(file)) {
            assertEquals(RegionFile.ChunkState.CORRUPT, region.state(0, 0));
            assertEquals(RegionFile.ChunkState.CORRUPT, region.state(1, 0));
            assertEquals(RegionFile.ChunkState.CORRUPT, region.state(2, 0));
            assertEquals(RegionFile.ChunkState.CORRUPT, region.state(3, 0));
            assertEquals(RegionFile.ChunkState.CORRUPT, region.state(4, 0));
            assertEquals(RegionFile.ChunkState.PRESENT, region.state(5, 0));
            assertTrue(region.problem(3, 0).orElseThrow().contains("overlap"));
            assertThrows(RegionFormatException.class, () -> region.readRaw(0, 0));
        }
    }

    @Test
    void invalidPayloadHeadersAreRejectedOnRead() throws IOException {
        ByteBuffer data = ByteBuffer.allocate(6 * RegionFile.SECTOR_BYTES);
        location(data, 0, 2, 1);
        data.putInt(2 * RegionFile.SECTOR_BYTES, -5);
        location(data, 1, 3, 1);
        data.putInt(3 * RegionFile.SECTOR_BYTES, RegionFile.SECTOR_BYTES);
        location(data, 2, 4, 1);
        data.putInt(4 * RegionFile.SECTOR_BYTES, 1);
        data.put(4 * RegionFile.SECTOR_BYTES + 4, (byte) 0);
        location(data, 3, 5, 1);
        data.putInt(5 * RegionFile.SECTOR_BYTES, 1);
        data.put(5 * RegionFile.SECTOR_BYTES + 4, (byte) (ChunkCompression.ZLIB.id() | ChunkCompression.EXTERNAL_FLAG));
        Path file = Files.write(dir.resolve("r.0.0.mca"), data.array());

        try (RegionFile region = RegionFile.open(file)) {
            for (int x = 0; x < 4; x++) {
                int localX = x;
                assertEquals(RegionFile.ChunkState.PRESENT, region.state(x, 0));
                assertThrows(RegionFormatException.class, () -> region.readRaw(localX, 0));
            }
        }
    }

    @Test
    void oversizedExternalChunkIsRejected() throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        byte[] payload = new byte[RegionFile.MAX_INLINE_SECTORS * RegionFile.SECTOR_BYTES];
        new RegionWriter().put(0, 0, RawChunk.of(ChunkCompression.NONE.id(), payload, 0)).writeTo(file);
        RegionLimits tight = new RegionLimits(1024, NbtLimits.DEFAULT);
        try (RegionFile region = RegionFile.open(file, tight)) {
            assertThrows(RegionFormatException.class, () -> region.readRaw(0, 0));
        }
    }

    @Test
    void symbolicLinksAreRefused() throws IOException {
        Path real = dir.resolve("real.bin");
        new RegionWriter().put(0, 0, RawChunk.encode(chunkTag(0, 0), ChunkCompression.ZLIB, 0))
                .writeTo(dir.resolve("r.0.0.mca"));
        Files.copy(dir.resolve("r.0.0.mca"), real);
        Path link = Files.createSymbolicLink(dir.resolve("r.5.5.mca"), real);

        assertThrows(IOException.class, () -> RegionFile.open(link));
        assertThrows(IOException.class, () -> new RegionWriter().writeTo(link));
        assertArrayEquals(Files.readAllBytes(dir.resolve("r.0.0.mca")), Files.readAllBytes(real));
    }

    @Test
    void regionFileNamesAreParsedStrictly() {
        assertEquals(Optional.of(new RegionPos(-3, 12)), RegionPos.parseFileName("r.-3.12.mca"));
        assertEquals(Optional.empty(), RegionPos.parseFileName("r.1.2.mcr"));
        assertEquals(Optional.empty(), RegionPos.parseFileName("r.+1.2.mca"));
        assertEquals(Optional.empty(), RegionPos.parseFileName("r.1.2.mca.bak"));
        assertEquals(Optional.empty(), RegionPos.parseFileName("../r.0.0.mca"));
        assertEquals(Optional.empty(), RegionPos.parseFileName("r.999999999.0.mca"));
        assertEquals(new RegionPos(-1, 0), RegionPos.ofChunk(-1, 31));
        assertThrows(IndexOutOfBoundsException.class, () -> new RegionPos(0, 0).chunkX(32));
    }

    private static void location(ByteBuffer data, int index, int offset, int sectors) {
        data.putInt(index * Integer.BYTES, (offset << 8) | sectors);
    }
}
