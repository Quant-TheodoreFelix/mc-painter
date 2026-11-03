package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Optional;

enum Tool {
    RAISE("raise", true, true),
    LOWER("lower", true, true),
    SMOOTH("smooth", true, true),
    FLATTEN("flatten", true, true),
    TERRAIN("terrain", false, true),
    WATER("water", false, true),
    BIOME("biome", false, true),
    ADD_TILES("addTiles", false, true),
    DELETE_TILES("deleteTiles", false, true),
    LAYER("layer", true, true),
    LAYER_ERASE("layerErase", true, false);

    static final int NO_SHORTCUT = -1;

    private final String key;
    private final boolean continuous;
    private final boolean visible;

    Tool(String key, boolean continuous, boolean visible) {
        this.key = key;
        this.continuous = continuous;
        this.visible = visible;
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
        return continuous;
    }

    boolean usesStrength() {
        return continuous;
    }

    boolean visible() {
        return visible;
    }

    boolean paintsLayer() {
        return this == LAYER || this == LAYER_ERASE;
    }

    Optional<Tool> inverse() {
        return switch (this) {
            case RAISE -> Optional.of(LOWER);
            case LOWER -> Optional.of(RAISE);
            case ADD_TILES -> Optional.of(DELETE_TILES);
            case DELETE_TILES -> Optional.of(ADD_TILES);
            case LAYER -> Optional.of(LAYER_ERASE);
            case LAYER_ERASE -> Optional.of(LAYER);
            default -> Optional.empty();
        };
    }

    boolean editsTiles() {
        return this == ADD_TILES || this == DELETE_TILES;
    }

    int shortcut() {
        if (!visible) {
            return NO_SHORTCUT;
        }
        int number = ordinal() + 1;
        return number < 10 ? number : number == 10 ? 0 : NO_SHORTCUT;
    }
}
