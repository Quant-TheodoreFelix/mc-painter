package space.qu4nt.thirdparty.mcpainter.nbt;

import java.util.Arrays;

public final class IntArrayTag implements Tag {

    private final int[] values;

    private IntArrayTag(int[] values) {
        this.values = values;
    }

    public static IntArrayTag of(int... values) {
        return new IntArrayTag(values.clone());
    }

    static IntArrayTag wrap(int[] values) {
        return new IntArrayTag(values);
    }

    public int length() {
        return values.length;
    }

    public int get(int index) {
        return values[index];
    }

    public int[] toArray() {
        return values.clone();
    }

    int[] raw() {
        return values;
    }

    @Override
    public TagType type() {
        return TagType.INT_ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IntArrayTag other && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "IntArrayTag[length=" + values.length + "]";
    }
}
