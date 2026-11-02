package space.qu4nt.thirdparty.mcpainter.nbt;

import java.util.Arrays;

public final class LongArrayTag implements Tag {

    private final long[] values;

    private LongArrayTag(long[] values) {
        this.values = values;
    }

    public static LongArrayTag of(long... values) {
        return new LongArrayTag(values.clone());
    }

    static LongArrayTag wrap(long[] values) {
        return new LongArrayTag(values);
    }

    public int length() {
        return values.length;
    }

    public long get(int index) {
        return values[index];
    }

    public long[] toArray() {
        return values.clone();
    }

    long[] raw() {
        return values;
    }

    @Override
    public TagType type() {
        return TagType.LONG_ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LongArrayTag other && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "LongArrayTag[length=" + values.length + "]";
    }
}
