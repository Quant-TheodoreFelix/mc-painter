package space.qu4nt.thirdparty.mcpainter.nbt;

public enum TagType {
    END(0),
    BYTE(1),
    SHORT(2),
    INT(3),
    LONG(4),
    FLOAT(5),
    DOUBLE(6),
    BYTE_ARRAY(7),
    STRING(8),
    LIST(9),
    COMPOUND(10),
    INT_ARRAY(11),
    LONG_ARRAY(12);

    private static final TagType[] BY_ID = values();

    private final int id;

    TagType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static TagType fromId(int id) throws NbtFormatException {
        if (id < 0 || id >= BY_ID.length) {
            throw new NbtFormatException("unknown tag type id " + id);
        }
        return BY_ID[id];
    }
}
