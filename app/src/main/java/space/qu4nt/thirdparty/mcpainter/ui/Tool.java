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
    ADD_TILES("addTiles", false);

    private final String key;
    private final boolean shapesHeight;

    Tool(String key, boolean shapesHeight) {
        this.key = key;
        this.shapesHeight = shapesHeight;
    }

    String messageKey() {
        return "tool." + key;
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
            default -> Optional.empty();
        };
    }

    int shortcut() {
        return ordinal() + 1;
    }
}
