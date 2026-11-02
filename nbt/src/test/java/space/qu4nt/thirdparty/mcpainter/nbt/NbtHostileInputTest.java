package space.qu4nt.thirdparty.mcpainter.nbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

class NbtHostileInputTest {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);

    private NbtHostileInputTest rootCompound() {
        return u8(TagType.COMPOUND.id()).utf("");
    }

    private NbtHostileInputTest entry(TagType type, String key) {
        return u8(type.id()).utf(key);
    }

    private NbtHostileInputTest u8(int value) {
        return io(() -> out.writeByte(value));
    }

    private NbtHostileInputTest i32(int value) {
        return io(() -> out.writeInt(value));
    }

    private NbtHostileInputTest utf(String value) {
        return io(() -> out.writeUTF(value));
    }

    private NbtHostileInputTest raw(int... values) {
        for (int value : values) {
            u8(value);
        }
        return this;
    }

    private NbtHostileInputTest io(IoAction action) {
        try {
            action.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    private RootTag read() throws IOException {
        return read(NbtLimits.DEFAULT);
    }

    private RootTag read(NbtLimits limits) throws IOException {
        return NbtReader.read(new ByteArrayInputStream(bytes.toByteArray()), limits);
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    @Test
    void rootMustBeCompound() {
        u8(TagType.STRING.id()).utf("").utf("x");
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void unknownTypeIdIsRejected() {
        rootCompound().u8(13).utf("a");
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void unknownListElementTypeIsRejected() {
        rootCompound().entry(TagType.LIST, "a").u8(99).i32(0);
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void negativeArrayLengthIsRejected() {
        rootCompound().entry(TagType.LONG_ARRAY, "a").i32(-1);
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void hugeArrayIsRejectedBeforeAllocation() {
        rootCompound().entry(TagType.LONG_ARRAY, "a").i32(Integer.MAX_VALUE);
        assertThrows(NbtLimitException.class, this::read);
    }

    @Test
    void hugeListCountIsRejectedBeforeReading() {
        rootCompound().entry(TagType.LIST, "a").u8(TagType.COMPOUND.id()).i32(Integer.MAX_VALUE);
        assertThrows(NbtLimitException.class, this::read);
    }

    @Test
    void endListWithElementsIsRejected() {
        rootCompound().entry(TagType.LIST, "a").u8(TagType.END.id()).i32(1);
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void duplicateKeysAreRejected() {
        rootCompound()
                .entry(TagType.BYTE, "a").u8(1)
                .entry(TagType.BYTE, "a").u8(2)
                .u8(TagType.END.id());
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void truncatedInputIsFormatError() {
        rootCompound().entry(TagType.INT, "a").raw(0, 0);
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void depthLimitIsEnforced() throws IOException {
        int maxDepth = NbtLimits.DEFAULT_MAX_DEPTH;
        nestedCompounds(maxDepth);
        assertEquals(1, read().tag().size());

        bytes.reset();
        nestedCompounds(maxDepth + 1);
        assertThrows(NbtLimitException.class, this::read);
    }

    private void nestedCompounds(int depth) {
        rootCompound();
        for (int i = 1; i < depth; i++) {
            entry(TagType.COMPOUND, "c");
        }
        for (int i = 0; i < depth; i++) {
            u8(TagType.END.id());
        }
    }

    @Test
    void budgetIsEnforcedForManySmallTags() {
        rootCompound().entry(TagType.LIST, "a").u8(TagType.BYTE.id()).i32(100);
        for (int i = 0; i < 100; i++) {
            u8(0);
        }
        u8(TagType.END.id());
        assertThrows(NbtLimitException.class, () -> read(new NbtLimits(512, 1000)));
    }

    @Test
    void rawNulByteInStringIsRejected() {
        rootCompound().entry(TagType.STRING, "a").raw(0, 1, 0x00);
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void overlongEncodingIsRejected() {
        rootCompound().entry(TagType.STRING, "a").raw(0, 2, 0xC1, 0x81);
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void fourByteSequenceIsRejected() {
        rootCompound().entry(TagType.STRING, "a").raw(0, 4, 0xF0, 0x9F, 0x98, 0x80);
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void truncatedMultiByteSequenceIsRejected() {
        rootCompound().entry(TagType.STRING, "a").raw(0, 2, 0xE0, 0xA0);
        assertThrows(NbtFormatException.class, this::read);
    }

    @Test
    void canonicalNulEncodingIsAccepted() throws IOException {
        rootCompound().entry(TagType.STRING, "a").raw(0, 2, 0xC0, 0x80).u8(TagType.END.id());
        assertEquals(new StringTag("\u0000"), read().tag().get("a").orElseThrow());
    }
}
