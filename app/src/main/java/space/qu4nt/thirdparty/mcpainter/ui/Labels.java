package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Locale;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;

final class Labels {

    private Labels() {
    }

    static String terrainKey(TerrainType type) {
        return "terrain." + type.id();
    }

    static String falloffKey(Brush.Falloff falloff) {
        return "falloff." + falloff.name().toLowerCase(Locale.ROOT);
    }
}
