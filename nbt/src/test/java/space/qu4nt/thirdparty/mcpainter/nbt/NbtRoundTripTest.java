package space.qu4nt.thirdparty.mcpainter.nbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NbtRoundTripTest {

    static RootTag sample() {
        CompoundTag nested = CompoundTag.builder()
                .put("Name", new StringTag("minecraft:stone"))
                .put("Properties", CompoundTag.builder().put("axis", new StringTag("y")).build())
                .build();
        CompoundTag root = CompoundTag.builder()
                .put("DataVersion", new IntTag(4786))
                .put("byte", new ByteTag((byte) -7))
                .put("short", new ShortTag((short) 31000))
                .put("long", new LongTag(Long.MIN_VALUE))
                .put("float", new FloatTag(Float.NaN))
                .put("double", new DoubleTag(-0.0))
                .put("unicode", new StringTag("\u0000한글😀"))
                .put("bytes", ByteArrayTag.of((byte) 1, (byte) 2, (byte) 3))
                .put("ints", IntArrayTag.of(1, -1, Integer.MAX_VALUE))
                .put("longs", LongArrayTag.of(0L, -1L, Long.MAX_VALUE))
                .put("palette", ListTag.of(TagType.COMPOUND, nested, nested))
                .put("emptyEnd", ListTag.empty())
                .put("emptyTyped", ListTag.of(TagType.STRING, List.of()))
                .put("nestedLists", ListTag.of(TagType.LIST, ListTag.of(TagType.INT, new IntTag(5))))
                .build();
        return new RootTag("root", root);
    }

    static byte[] encode(RootTag root) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtWriter.write(out, root);
        return out.toByteArray();
    }

    @Test
    void roundTripPreservesValuesAndBytes() throws IOException {
        RootTag original = sample();
        byte[] encoded = encode(original);
        RootTag decoded = NbtReader.read(new ByteArrayInputStream(encoded));
        assertEquals(original, decoded);
        assertEquals(List.copyOf(original.tag().keys()), List.copyOf(decoded.tag().keys()));
        assertArrayEquals(encoded, encode(decoded));
    }

    @ParameterizedTest
    @EnumSource(NbtCompression.class)
    void compressedRoundTrip(NbtCompression compression) throws IOException {
        RootTag original = sample();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.write(out, original, compression);
        RootTag decoded = NbtIo.read(new ByteArrayInputStream(out.toByteArray()), compression);
        assertEquals(original, decoded);
    }

    @Test
    void corruptGzipIsFormatError() {
        byte[] garbage = {1, 2, 3, 4, 5};
        assertThrows(NbtFormatException.class,
                () -> NbtIo.read(new ByteArrayInputStream(garbage), NbtCompression.GZIP));
    }

    @Test
    void arraysAreDefensivelyCopied() {
        long[] source = {1L, 2L};
        LongArrayTag tag = LongArrayTag.of(source);
        source[0] = 99L;
        long[] view = tag.toArray();
        view[1] = 99L;
        assertEquals(1L, tag.get(0));
        assertEquals(2L, tag.get(1));
    }

    @Test
    void builtCompoundIsUnaffectedByLaterBuilderChanges() {
        CompoundTag.Builder builder = CompoundTag.builder().put("a", new IntTag(1));
        CompoundTag first = builder.build();
        builder.put("b", new IntTag(2)).remove("a");
        assertEquals(List.of("a"), List.copyOf(first.keys()));
        assertEquals(List.of("b"), List.copyOf(builder.build().keys()));
    }

    @Test
    void listRejectsMixedTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> ListTag.of(TagType.INT, new IntTag(1), new LongTag(2)));
        assertThrows(IllegalArgumentException.class,
                () -> ListTag.of(TagType.END, new IntTag(1)));
    }

    @Test
    void stringsBeyondModifiedUtf8LimitAreRejected() {
        String tooLong = "ࠀ".repeat(21846);
        assertThrows(IllegalArgumentException.class, () -> new StringTag(tooLong));
        assertThrows(IllegalArgumentException.class, () -> CompoundTag.builder().put(tooLong, new IntTag(1)));
        new StringTag("ࠀ".repeat(21845));
    }

    @Test
    void writerEnforcesDepthLimit() {
        Tag tag = new IntTag(0);
        for (int i = 0; i < 4; i++) {
            tag = ListTag.of(tag.type(), tag);
        }
        RootTag root = RootTag.of(CompoundTag.builder().put("deep", tag).build());
        assertThrows(NbtLimitException.class,
                () -> NbtWriter.write(new ByteArrayOutputStream(), root, new NbtLimits(4, 1024)));
    }
}
