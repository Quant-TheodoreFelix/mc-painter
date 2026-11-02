package space.qu4nt.thirdparty.mcpainter.nbt;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class ListTag implements Tag, Iterable<Tag> {

    private static final ListTag EMPTY = new ListTag(TagType.END, List.of());

    private final TagType elementType;
    private final List<Tag> elements;

    private ListTag(TagType elementType, List<Tag> elements) {
        this.elementType = elementType;
        this.elements = elements;
    }

    public static ListTag empty() {
        return EMPTY;
    }

    public static ListTag of(TagType elementType, List<? extends Tag> elements) {
        Objects.requireNonNull(elementType, "elementType");
        List<Tag> copy = List.copyOf(elements);
        if (elementType == TagType.END && !copy.isEmpty()) {
            throw new IllegalArgumentException("END element type requires an empty list");
        }
        for (Tag element : copy) {
            if (element.type() != elementType) {
                throw new IllegalArgumentException(
                        "list of " + elementType + " cannot hold " + element.type());
            }
        }
        return new ListTag(elementType, copy);
    }

    public static ListTag of(TagType elementType, Tag... elements) {
        return of(elementType, List.of(elements));
    }

    public TagType elementType() {
        return elementType;
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public Tag get(int index) {
        return elements.get(index);
    }

    public <T extends Tag> T get(int index, Class<T> type) {
        return type.cast(elements.get(index));
    }

    public List<Tag> elements() {
        return elements;
    }

    @Override
    public Iterator<Tag> iterator() {
        return elements.iterator();
    }

    @Override
    public TagType type() {
        return TagType.LIST;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ListTag other
                && elementType == other.elementType
                && elements.equals(other.elements);
    }

    @Override
    public int hashCode() {
        return 31 * elementType.hashCode() + elements.hashCode();
    }

    @Override
    public String toString() {
        return "ListTag[" + elementType + ", size=" + elements.size() + "]";
    }
}
