package space.qu4nt.thirdparty.mcpainter.nbt;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;

public final class NbtWriter {

    private final DataOutputStream out;
    private final int maxDepth;

    private NbtWriter(OutputStream out, int maxDepth) {
        this.out = out instanceof DataOutputStream data ? data : new DataOutputStream(out);
        this.maxDepth = maxDepth;
    }

    public static void write(OutputStream out, RootTag root) throws IOException {
        write(out, root, NbtLimits.DEFAULT);
    }

    public static void write(OutputStream out, RootTag root, NbtLimits limits) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(limits, "limits");
        NbtWriter writer = new NbtWriter(out, limits.maxDepth());
        writer.out.writeByte(TagType.COMPOUND.id());
        writer.out.writeUTF(root.name());
        writer.writeCompound(root.tag(), 1);
        writer.out.flush();
    }

    private void writePayload(Tag tag, int depth) throws IOException {
        switch (tag) {
            case ByteTag t -> out.writeByte(t.value());
            case ShortTag t -> out.writeShort(t.value());
            case IntTag t -> out.writeInt(t.value());
            case LongTag t -> out.writeLong(t.value());
            case FloatTag t -> out.writeFloat(t.value());
            case DoubleTag t -> out.writeDouble(t.value());
            case StringTag t -> out.writeUTF(t.value());
            case ByteArrayTag t -> {
                byte[] values = t.raw();
                out.writeInt(values.length);
                out.write(values);
            }
            case IntArrayTag t -> {
                int[] values = t.raw();
                out.writeInt(values.length);
                for (int value : values) {
                    out.writeInt(value);
                }
            }
            case LongArrayTag t -> {
                long[] values = t.raw();
                out.writeInt(values.length);
                for (long value : values) {
                    out.writeLong(value);
                }
            }
            case ListTag t -> writeList(t, depth + 1);
            case CompoundTag t -> writeCompound(t, depth + 1);
        }
    }

    private void writeCompound(CompoundTag tag, int depth) throws IOException {
        checkDepth(depth);
        for (Map.Entry<String, Tag> entry : tag.entries().entrySet()) {
            out.writeByte(entry.getValue().type().id());
            out.writeUTF(entry.getKey());
            writePayload(entry.getValue(), depth);
        }
        out.writeByte(TagType.END.id());
    }

    private void writeList(ListTag tag, int depth) throws IOException {
        checkDepth(depth);
        out.writeByte(tag.elementType().id());
        out.writeInt(tag.size());
        for (Tag element : tag) {
            writePayload(element, depth);
        }
    }

    private void checkDepth(int depth) throws NbtLimitException {
        if (depth > maxDepth) {
            throw new NbtLimitException("NBT nesting exceeds max depth " + maxDepth);
        }
    }
}
