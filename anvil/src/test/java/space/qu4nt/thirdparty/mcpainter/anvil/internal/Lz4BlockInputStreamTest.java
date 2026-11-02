package space.qu4nt.thirdparty.mcpainter.anvil.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.region.ChunkCompression;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RawChunk;
import space.qu4nt.thirdparty.mcpainter.anvil.region.RegionFormatException;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtLimits;
import space.qu4nt.thirdparty.mcpainter.nbt.NbtReader;

class Lz4BlockInputStreamTest {

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = Lz4BlockInputStreamTest.class.getResourceAsStream("/lz4/" + name)) {
            if (in == null) {
                throw new IOException("missing test resource " + name);
            }
            return in.readAllBytes();
        }
    }

    private static byte[] text(int size) {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        while (builder.length() < size) {
            builder.append("minecraft:stone_").append(i % 37).append(';');
            i++;
        }
        return builder.substring(0, size).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] noise(int size) {
        byte[] bytes = new byte[size];
        new Random(1234).nextBytes(bytes);
        return bytes;
    }

    private static byte[] decode(byte[] encoded) throws IOException {
        try (InputStream in = new Lz4BlockInputStream(encoded, 0, encoded.length)) {
            return in.readAllBytes();
        }
    }

    @Test
    void decodesVectorsProducedByLz4Java() throws IOException {
        assertArrayEquals(text(150000), decode(resource("text-150000.lz4")));
        assertArrayEquals(noise(5000), decode(resource("noise-5000.lz4")));
        assertArrayEquals(new byte[0], decode(resource("empty.lz4")));
    }

    @Test
    void decodesLz4ChunkPayload() throws IOException {
        RawChunk chunk = RawChunk.of(ChunkCompression.LZ4.id(), resource("nbt.lz4"), 0);
        assertEquals(NbtReader.read(new ByteArrayInputStream(resource("nbt.bin"))), chunk.decode(NbtLimits.DEFAULT));
    }

    @Test
    void xxHash32MatchesReferenceValues() {
        byte[] data = noise(1000);
        int[] lengths = {0, 1, 3, 4, 15, 16, 17, 100, 1000};
        int[] seed0 = {0x02cc5d05, 0xad531457, 0x878ff66b, 0x3d8afdfe, 0x4740317a,
            0xb479bc2a, 0x97219be1, 0x5aa92f4d, 0x41326a55};
        int[] seedLz4 = {0x8d3b42d8, 0xcfd103df, 0x663004f1, 0x61f47f1e, 0x0a59262d,
            0x119b0925, 0xaea2bf56, 0x1e79871b, 0x118fb35d};
        for (int i = 0; i < lengths.length; i++) {
            assertEquals(seed0[i], XxHash32.hash(data, 0, lengths[i], 0), "seed 0 length " + lengths[i]);
            assertEquals(seedLz4[i], XxHash32.hash(data, 0, lengths[i], 0x9747B28C), "lz4 seed length " + lengths[i]);
        }
    }

    @Test
    void rejectsCorruptStreams() throws IOException {
        byte[] good = resource("text-150000.lz4");

        byte[] badMagic = good.clone();
        badMagic[0] = 'X';
        assertThrows(Lz4FormatException.class, () -> decode(badMagic));

        byte[] badChecksum = good.clone();
        badChecksum[17] ^= 1;
        assertThrows(Lz4FormatException.class, () -> decode(badChecksum));

        byte[] badPayload = good.clone();
        badPayload[30] ^= 0x55;
        assertThrows(Lz4FormatException.class, () -> decode(badPayload));

        byte[] truncated = Arrays.copyOf(good, good.length - 21);
        assertThrows(Lz4FormatException.class, () -> decode(truncated));

        byte[] badMethod = good.clone();
        badMethod[8] = (byte) 0x30;
        assertThrows(Lz4FormatException.class, () -> decode(badMethod));

        byte[] oversized = good.clone();
        oversized[8] = (byte) 0x20;
        assertThrows(Lz4FormatException.class, () -> decode(oversized));

        byte[] endMarkWithChecksum = resource("empty.lz4");
        endMarkWithChecksum[17] = 1;
        assertThrows(Lz4FormatException.class, () -> decode(endMarkWithChecksum));

        RawChunk corrupt = RawChunk.of(ChunkCompression.LZ4.id(), badChecksum, 0);
        assertThrows(RegionFormatException.class, () -> corrupt.decode(NbtLimits.DEFAULT));
    }

    @Test
    void rejectsMaliciousSequences() {
        byte[] out = new byte[16];
        assertThrows(Lz4FormatException.class,
                () -> Lz4BlockInputStream.decompress(new byte[] {0x10, 'a', 0x00, 0x00}, 0, 4, out, 16));
        assertThrows(Lz4FormatException.class,
                () -> Lz4BlockInputStream.decompress(new byte[] {0x10, 'a', 0x02, 0x00}, 0, 4, out, 16));
        assertThrows(Lz4FormatException.class,
                () -> Lz4BlockInputStream.decompress(new byte[] {(byte) 0xF0, (byte) 0xFF, (byte) 0xFF}, 0, 3, out, 16));
        assertThrows(Lz4FormatException.class,
                () -> Lz4BlockInputStream.decompress(new byte[] {0x1F, 'a', 0x01, 0x00, (byte) 0xFF, 0x00}, 0, 6, out, 16));
        assertThrows(Lz4FormatException.class,
                () -> Lz4BlockInputStream.decompress(new byte[] {0x20, 'a', 'b'}, 0, 3, out, 16));
    }

    @Test
    void decodesOverlappingMatches() throws Lz4FormatException {
        byte[] out = new byte[9];
        Lz4BlockInputStream.decompress(new byte[] {0x13, 'a', 0x01, 0x00, 0x10, 'z'}, 0, 6, out, 9);
        assertArrayEquals("aaaaaaaaz".getBytes(StandardCharsets.US_ASCII), out);
    }
}
