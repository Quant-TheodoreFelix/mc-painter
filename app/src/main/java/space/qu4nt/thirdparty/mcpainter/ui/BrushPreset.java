package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;

record BrushPreset(String name, BrushShape shape, String shapeName, Tool tool, int radius, int strengthPercent,
        Brush.Falloff falloff, TerrainType terrain, String biome, int waterLevel, boolean autoAddTiles) {

    static final int MIN_WATER_LEVEL = -4096;
    static final int MAX_WATER_LEVEL = 4096;

    BrushPreset {
        name = BrushLibrary.requireName(name);
        Objects.requireNonNull(shape, "shape");
        shapeName = shape instanceof BrushShape.Mask ? BrushLibrary.requireName(shapeName) : "";
        Objects.requireNonNull(tool, "tool");
        if (radius < 0 || radius > BrushSettings.MAX_RADIUS) {
            throw new IllegalArgumentException("radius out of range");
        }
        if (strengthPercent < BrushSettings.MIN_STRENGTH_PERCENT || strengthPercent > 100) {
            throw new IllegalArgumentException("strength out of range");
        }
        Objects.requireNonNull(falloff, "falloff");
        Objects.requireNonNull(terrain, "terrain");
        biome = BrushSettings.normalizeBiome(biome);
        if (waterLevel < MIN_WATER_LEVEL || waterLevel > MAX_WATER_LEVEL) {
            throw new IllegalArgumentException("water level out of range");
        }
    }

    static BrushPreset of(String name, BrushSettings settings, String shapeName) {
        return new BrushPreset(name, settings.shape(), shapeName, settings.tool(), settings.radius(),
                settings.strengthPercent(), settings.falloff(), settings.terrain(), settings.biome(),
                settings.waterLevel(), settings.autoAddTiles());
    }
}
