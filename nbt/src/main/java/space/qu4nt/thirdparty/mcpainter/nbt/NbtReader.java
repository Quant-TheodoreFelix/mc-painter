package space.qu4nt.thirdparty.mcpainter.nbt;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NbtReader {

    private static final long TAG_COST = 16;
    private static final long ENTRY_COST = 32;
    private static final long REFERENCE_COST = 8;
    private static final int INITIAL_LIST_CAPACITY = 1024;

    private final DataInputStream in;
    private final NbtLimits limits;
    private long remaining;

    private NbtReader(InputStream in, NbtLimits limits) {
        this.in = in instanceof DataInputStream data ? data : new DataInputStream(in);
        this.limits = limits;
        this.remaining = limits.maxBytes();
    }

    public static RootTag read(InputStream in) throws IOException {
        return read(in, NbtLimits.DEFAULT);
    }

    public static RootTag read(InputStream in, NbtLimits limits) throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(limits, "limits");
        return new NbtReader(in, limits).readRoot();
    }

    private RootTag readRoot() throws IOException {
        try {
            int typeId = in.readUnsignedByte();
            if (typeId != TagType.COMPOUND.id()) {
                throw new NbtFormatException("root tag must be a compound, got type id " + typeId);
            }
            String name = readString();
            CompoundTag tag = readCompound(1);
            return new RootTag(name, tag);
        } catch (EOFException e) {
            throw new NbtFormatException("unexpected end of NBT data", e);
        }
    }

    private Tag readPayload(TagType type, int depth) throws IOException {
        return switch (type) {
            case END -> throw new NbtFormatException("unexpected END tag");
            case BYTE -> {
                charge(TAG_COST + Byte.BYTES);
                yield new ByteTag(in.readByte());
            }
            case SHORT -> {
                charge(TAG_COST + Short.BYTES);
                yield new ShortTag(in.readShort());
            }
            case INT -> {
                charge(TAG_COST + Integer.BYTES);
                yield new IntTag(in.readInt());
            }
            case LONG -> {
                charge(TAG_COST + Long.BYTES);
                yield new LongTag(in.readLong());
            }
            case FLOAT -> {
                charge(TAG_COST + Float.BYTES);
                yield new FloatTag(in.readFloat());
            }
            case DOUBLE -> {
                charge(TAG_COST + Double.BYTES);
                yield new DoubleTag(in.readDouble());
            }
            case STRING -> new StringTag(readString());
            case BYTE_ARRAY -> readByteArray();
            case INT_ARRAY -> readIntArray();
            case LONG_ARRAY -> readLongArray();
            case LIST -> readList(depth + 1);
            case COMPOUND -> readCompound(depth + 1);
        };
    }

    private CompoundTag readCompound(int depth) throws IOException {
        checkDepth(depth);
        charge(TAG_COST);
        CompoundTag.Builder builder = CompoundTag.builder();
        while (true) {
            int typeId = in.readUnsignedByte();
            if (typeId == TagType.END.id()) {
                return builder.build();
            }
            TagType type = TagType.fromId(typeId);
            String key = readString();
            charge(ENTRY_COST);
            Tag value = readPayload(type, depth);
            if (!builder.putIfAbsent(key, value)) {
                throw new NbtFormatException("duplicate compound key \"" + key + "\"");
            }
        }
    }

    private ListTag readList(int depth) throws IOException {
        checkDepth(depth);
        TagType elementType = TagType.fromId(in.readUnsignedByte());
        int count = readLength("list");
        charge(TAG_COST + REFERENCE_COST * count);
        if (elementType == TagType.END) {
            if (count != 0) {
                throw new NbtFormatException("list of END must be empty, got " + count + " elements");
            }
            return ListTag.empty();
        }
        List<Tag> elements = new ArrayList<>(Math.min(count, INITIAL_LIST_CAPACITY));
        for (int i = 0; i < count; i++) {
            elements.add(readPayload(elementType, depth));
        }
        return ListTag.of(elementType, elements);
    }

    private ByteArrayTag readByteArray() throws IOException {
        int length = readLength("byte array");
        charge(TAG_COST + (long) Byte.BYTES * length);
        byte[] values = new byte[length];
        in.readFully(values);
        return ByteArrayTag.wrap(values);
    }

    private IntArrayTag readIntArray() throws IOException {
        int length = readLength("int array");
        charge(TAG_COST + (long) Integer.BYTES * length);
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = in.readInt();
        }
        return IntArrayTag.wrap(values);
    }

    private LongArrayTag readLongArray() throws IOException {
        int length = readLength("long array");
        charge(TAG_COST + (long) Long.BYTES * length);
        long[] values = new long[length];
        for (int i = 0; i < length; i++) {
            values[i] = in.readLong();
        }
        return LongArrayTag.wrap(values);
    }

    private String readString() throws IOException {
        int length = in.readUnsignedShort();
        charge(TAG_COST + 2L * length);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return Mutf8.decodeStrict(bytes);
    }

    private int readLength(String what) throws IOException {
        int length = in.readInt();
        if (length < 0) {
            throw new NbtFormatException("negative " + what + " length " + length);
        }
        return length;
    }

    private void checkDepth(int depth) throws NbtLimitException {
        if (depth > limits.maxDepth()) {
            throw new NbtLimitException("NBT nesting exceeds max depth " + limits.maxDepth());
        }
    }

    private void charge(long bytes) throws NbtLimitException {
        remaining -= bytes;
        if (remaining < 0) {
            throw new NbtLimitException("NBT data exceeds budget of " + limits.maxBytes() + " bytes");
        }
    }
}
