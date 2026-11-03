package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Objects;
import java.util.Optional;

public record BaseTerrain(Kind kind, float height, TerrainType terrain) {

    public static final BaseTerrain VOID = new BaseTerrain(Kind.VOID, 0, TerrainType.GRASS);

    public enum Kind {
        VOID("void"),
        FLAT("flat");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<Kind> fromId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public BaseTerrain {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(terrain, "terrain");
        if (!Float.isFinite(height)) {
            throw new IllegalArgumentException("base height must be finite");
        }
        if (kind == Kind.VOID) {
            height = 0;
            terrain = TerrainType.GRASS;
        }
    }

    public static BaseTerrain flat(float height, TerrainType terrain) {
        return new BaseTerrain(Kind.FLAT, height, terrain);
    }

    public boolean filled() {
        return kind == Kind.FLAT;
    }
}
