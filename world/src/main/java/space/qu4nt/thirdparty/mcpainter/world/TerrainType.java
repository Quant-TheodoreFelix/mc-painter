package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Optional;

public enum TerrainType {
    GRASS("grass"),
    DIRT("dirt"),
    STONE("stone"),
    SAND("sand"),
    GRAVEL("gravel"),
    SNOW("snow");

    private static final TerrainType[] BY_ORDINAL = values();

    private final String id;

    TerrainType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<TerrainType> fromId(String id) {
        for (TerrainType type : BY_ORDINAL) {
            if (type.id.equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    static TerrainType fromOrdinal(int ordinal) {
        return BY_ORDINAL[ordinal];
    }
}
