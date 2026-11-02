package space.qu4nt.thirdparty.mcpainter.nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.SequencedSet;

public final class CompoundTag implements Tag {

    private static final CompoundTag EMPTY = new CompoundTag(new LinkedHashMap<>());

    private final SequencedMap<String, Tag> entries;

    private CompoundTag(LinkedHashMap<String, Tag> entries) {
        this.entries = Collections.unmodifiableSequencedMap(entries);
    }

    public static CompoundTag empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder(new LinkedHashMap<>());
    }

    public Builder toBuilder() {
        return new Builder(new LinkedHashMap<>(entries));
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    public SequencedSet<String> keys() {
        return entries.sequencedKeySet();
    }

    public SequencedMap<String, Tag> entries() {
        return entries;
    }

    public Optional<Tag> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    public <T extends Tag> Optional<T> get(String key, Class<T> type) {
        Tag tag = entries.get(key);
        return type.isInstance(tag) ? Optional.of(type.cast(tag)) : Optional.empty();
    }

    @Override
    public TagType type() {
        return TagType.COMPOUND;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CompoundTag other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "CompoundTag" + entries.sequencedKeySet();
    }

    public static final class Builder {

        private LinkedHashMap<String, Tag> entries;
        private boolean shared;

        private Builder(LinkedHashMap<String, Tag> entries) {
            this.entries = entries;
        }

        public Builder put(String key, Tag value) {
            Mutf8.requireEncodable(key, "compound key");
            Objects.requireNonNull(value, "value");
            unshare();
            entries.put(key, value);
            return this;
        }

        public Builder remove(String key) {
            unshare();
            entries.remove(key);
            return this;
        }

        boolean putIfAbsent(String key, Tag value) {
            unshare();
            return entries.putIfAbsent(key, value) == null;
        }

        public CompoundTag build() {
            shared = true;
            return new CompoundTag(entries);
        }

        private void unshare() {
            if (shared) {
                entries = new LinkedHashMap<>(entries);
                shared = false;
            }
        }
    }
}
