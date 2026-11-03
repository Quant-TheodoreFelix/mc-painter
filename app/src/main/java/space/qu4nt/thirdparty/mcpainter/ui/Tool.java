package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Optional;

enum Tool {
    RAISE("raise", true),
    LOWER("lower", true),
    SMOOTH("smooth", true),
    FLATTEN("flatten", true),
    TERRAIN("terrain", false),
    WATER("water", false),
    BIOME("biome", false),
    ADD_TILES("addTiles", false),
    DELETE_TILES("deleteTiles", false);

    private final String key;
    private final boolean shapesHeight;

    Tool(String key, boolean shapesHeight) {
        this.key = key;
        this.shapesHeight = shapesHeight;
    }

    String messageKey() {
        return "tool." + key;
    }

    String id() {
        return key;
    }

    static Optional<Tool> fromId(String id) {
        for (Tool tool : values()) {
            if (tool.key.equals(id)) {
                return Optional.of(tool);
            }
        }
        return Optional.empty();
    }

    boolean continuous() {
        return shapesHeight;
    }

    boolean usesStrength() {
        return shapesHeight;
    }

    Optional<Tool> inverse() {
        return switch (this) {
            case RAISE -> Optional.of(LOWER);
            case LOWER -> Optional.of(RAISE);
            case ADD_TILES -> Optional.of(DELETE_TILES);
            case DELETE_TILES -> Optional.of(ADD_TILES);
            default -> Optional.empty();
        };
    }

    boolean editsTiles() {
        return this == ADD_TILES || this == DELETE_TILES;
    }

    int shortcut() {
        return ordinal() + 1;
    }
}
