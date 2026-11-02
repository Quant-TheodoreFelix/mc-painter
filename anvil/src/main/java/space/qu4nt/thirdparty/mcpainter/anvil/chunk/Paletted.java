package space.qu4nt.thirdparty.mcpainter.anvil.chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Paletted<T> {

    private final List<T> palette;
    private final int[] indices;

    private Paletted(List<T> palette, int[] indices) {
        this.palette = palette;
        this.indices = indices;
    }

    public static <T> Paletted<T> of(List<T> palette, int[] indices) {
        List<T> copy = List.copyOf(palette);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("palette must not be empty");
        }
        int[] values = indices.clone();
        for (int value : values) {
            if (value < 0 || value >= copy.size()) {
                throw new IllegalArgumentException("index " + value + " outside palette of " + copy.size());
            }
        }
        return new Paletted<>(copy, values);
    }

    public static <T> Paletted<T> filled(T value, int size) {
        return new Paletted<>(List.of(Objects.requireNonNull(value, "value")), new int[size]);
    }

    static <T> Paletted<T> trusted(List<T> palette, int[] indices) {
        return new Paletted<>(List.copyOf(palette), indices);
    }

    public int size() {
        return indices.length;
    }

    public T get(int index) {
        return palette.get(indices[index]);
    }

    public List<T> palette() {
        return palette;
    }

    public int paletteIndex(int index) {
        return indices[index];
    }

    public Paletted<T> with(int index, T value) {
        Objects.requireNonNull(value, "value");
        Objects.checkIndex(index, indices.length);
        int existing = palette.indexOf(value);
        int[] updated = indices.clone();
        if (existing >= 0) {
            updated[index] = existing;
            return new Paletted<>(palette, updated);
        }
        List<T> extended = new ArrayList<>(palette);
        extended.add(value);
        updated[index] = extended.size() - 1;
        return new Paletted<>(List.copyOf(extended), updated);
    }

    public Paletted<T> compact() {
        boolean[] used = new boolean[palette.size()];
        for (int index : indices) {
            used[index] = true;
        }
        int[] remap = new int[palette.size()];
        List<T> kept = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            if (used[i]) {
                remap[i] = kept.size();
                kept.add(palette.get(i));
            }
        }
        if (kept.size() == palette.size()) {
            return this;
        }
        int[] updated = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            updated[i] = remap[indices[i]];
        }
        return new Paletted<>(List.copyOf(kept), updated);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Paletted<?> other
                && palette.equals(other.palette)
                && Arrays.equals(indices, other.indices);
    }

    @Override
    public int hashCode() {
        return 31 * palette.hashCode() + Arrays.hashCode(indices);
    }

    @Override
    public String toString() {
        return "Paletted[palette=" + palette.size() + ", size=" + indices.length + "]";
    }
}
