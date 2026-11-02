package space.qu4nt.thirdparty.mcpainter.nbt;

import java.util.Arrays;

public final class ByteArrayTag implements Tag {

    private final byte[] values;

    private ByteArrayTag(byte[] values) {
        this.values = values;
    }

    public static ByteArrayTag of(byte... values) {
        return new ByteArrayTag(values.clone());
    }

    static ByteArrayTag wrap(byte[] values) {
        return new ByteArrayTag(values);
    }

    public int length() {
        return values.length;
    }

    public byte get(int index) {
        return values[index];
    }

    public byte[] toArray() {
        return values.clone();
    }

    byte[] raw() {
        return values;
    }

    @Override
    public TagType type() {
        return TagType.BYTE_ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteArrayTag other && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "ByteArrayTag[length=" + values.length + "]";
    }
}
