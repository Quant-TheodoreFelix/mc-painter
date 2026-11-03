package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import space.qu4nt.thirdparty.mcpainter.anvil.world.ResourceLocation;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class BrushSettings {

    static final int MAX_RADIUS = 512;
    static final int DEFAULT_RADIUS = 12;
    static final int MIN_STRENGTH_PERCENT = 1;
    static final int DEFAULT_STRENGTH_PERCENT = 50;

    private Tool tool = Tool.RAISE;
    private int radius = DEFAULT_RADIUS;
    private int strengthPercent = DEFAULT_STRENGTH_PERCENT;
    private Brush.Falloff falloff = Brush.Falloff.SMOOTH;
    private TerrainType terrain = TerrainType.GRASS;
    private int waterLevel = 63;
    private String biome = WorldModel.DEFAULT_BIOME;
    private boolean autoAddTiles;
    private BrushShape shape = BrushShape.CIRCLE;
    private LayerGroup layerGroup = LayerGroup.OAK_TREES;
    private List<Layer> flowers = LayerGroup.DEFAULT_FLOWERS;
    private final List<Runnable> listeners = new ArrayList<>();

    void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    Tool tool() {
        return tool;
    }

    void setTool(Tool value) {
        Objects.requireNonNull(value, "tool");
        if (value != tool) {
            tool = value;
            fire();
        }
    }

    int radius() {
        return radius;
    }

    void setRadius(int value) {
        int next = Math.clamp(value, 0, MAX_RADIUS);
        if (next != radius) {
            radius = next;
            fire();
        }
    }

    int strengthPercent() {
        return strengthPercent;
    }

    float strength() {
        return strengthPercent / 100f;
    }

    void setStrengthPercent(int value) {
        int next = Math.clamp(value, MIN_STRENGTH_PERCENT, 100);
        if (next != strengthPercent) {
            strengthPercent = next;
            fire();
        }
    }

    Brush.Falloff falloff() {
        return falloff;
    }

    void setFalloff(Brush.Falloff value) {
        Objects.requireNonNull(value, "falloff");
        if (value != falloff) {
            falloff = value;
            fire();
        }
    }

    TerrainType terrain() {
        return terrain;
    }

    void setTerrain(TerrainType value) {
        Objects.requireNonNull(value, "terrain");
        if (value != terrain) {
            terrain = value;
            fire();
        }
    }

    int waterLevel() {
        return waterLevel;
    }

    void setWaterLevel(int value) {
        if (value != waterLevel) {
            waterLevel = value;
            fire();
        }
    }

    String biome() {
        return biome;
    }

    void setBiome(String value) {
        String normalized = normalizeBiome(value);
        if (!normalized.equals(biome)) {
            biome = normalized;
            fire();
        }
    }

    static String normalizeBiome(String value) {
        Objects.requireNonNull(value, "biome");
        return ResourceLocation.parse(value.strip()).toString();
    }

    boolean autoAddTiles() {
        return autoAddTiles;
    }

    void setAutoAddTiles(boolean value) {
        if (value != autoAddTiles) {
            autoAddTiles = value;
            fire();
        }
    }

    BrushShape shape() {
        return shape;
    }

    void setShape(BrushShape value) {
        Objects.requireNonNull(value, "shape");
        if (!value.equals(shape)) {
            shape = value;
            fire();
        }
    }

    LayerGroup layerGroup() {
        return layerGroup;
    }

    void setLayerGroup(LayerGroup value) {
        Objects.requireNonNull(value, "layer group");
        if (value != layerGroup) {
            layerGroup = value;
            fire();
        }
    }

    List<Layer> flowers() {
        return flowers;
    }

    void setFlowers(Collection<Layer> value) {
        List<Layer> next = LayerGroup.requireFlowers(value);
        if (!next.equals(flowers)) {
            flowers = next;
            fire();
        }
    }

    List<Layer> paintLayers() {
        return layerGroup.layers(flowers);
    }

    void apply(BrushPreset preset, int minWaterLevel, int maxWaterLevel) {
        layerGroup = preset.layer();
        flowers = preset.flowers();
        tool = preset.tool();
        radius = preset.radius();
        strengthPercent = preset.strengthPercent();
        falloff = preset.falloff();
        shape = preset.shape();
        terrain = preset.terrain();
        biome = preset.biome();
        waterLevel = Math.clamp(preset.waterLevel(), minWaterLevel, maxWaterLevel);
        autoAddTiles = preset.autoAddTiles();
        fire();
    }

    Brush brush() {
        return new Brush(radius, falloff, shape);
    }

    private void fire() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
