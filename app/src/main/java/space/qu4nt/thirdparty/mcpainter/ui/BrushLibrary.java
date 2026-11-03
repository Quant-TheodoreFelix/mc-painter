package space.qu4nt.thirdparty.mcpainter.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import space.qu4nt.thirdparty.mcpainter.io.AppDirectories;
import space.qu4nt.thirdparty.mcpainter.io.Json;
import space.qu4nt.thirdparty.mcpainter.io.JsonFileStore;
import space.qu4nt.thirdparty.mcpainter.io.JsonFormatException;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;

final class BrushLibrary {

    static final int FORMAT_VERSION = 1;
    static final String SHAPE_FORMAT = "mc-painter-shape";
    static final String PRESET_FORMAT = "mc-painter-preset";
    static final int MAX_NAME_LENGTH = 64;
    static final String FOLDER = "brushes";

    private static final Set<String> SHAPE_KEYS = Set.of("format", "version", "name", "size", "data");
    private static final Set<String> PRESET_KEYS = Set.of("format", "version", "name", "shape", "tool", "radius",
            "strength", "falloff", "terrain", "biome", "waterLevel", "autoAddTiles");
    private static final Set<String> PRESET_KEYS_WITH_LAYER = union(PRESET_KEYS, "layer");
    private static final Set<String> PRESET_KEYS_WITH_FLOWERS = union(PRESET_KEYS_WITH_LAYER, "flowers");

    record Stored<T>(String file, T value) {
    }

    record Contents(List<Stored<NamedShape>> shapes, List<Stored<BrushPreset>> presets, int failures) {
    }

    private final Path root;
    private final JsonFileStore shapes;
    private final JsonFileStore presets;

    BrushLibrary(Path root) {
        this.root = root;
        this.shapes = new JsonFileStore(root.resolve("shapes"));
        this.presets = new JsonFileStore(root.resolve("presets"));
    }

    static BrushLibrary user() {
        return new BrushLibrary(AppDirectories.config().resolve(FOLDER));
    }

    Path root() {
        return root;
    }

    Contents load() throws IOException {
        int failures = 0;
        List<Stored<NamedShape>> loadedShapes = new ArrayList<>();
        for (String file : shapes.list()) {
            try {
                loadedShapes.add(new Stored<>(file, parseShape(asObject(Json.parse(shapes.read(file))))));
            } catch (IOException | IllegalArgumentException e) {
                failures++;
            }
        }
        List<Stored<BrushPreset>> loadedPresets = new ArrayList<>();
        for (String file : presets.list()) {
            try {
                loadedPresets.add(new Stored<>(file, parsePreset(asObject(Json.parse(presets.read(file))))));
            } catch (IOException | IllegalArgumentException e) {
                failures++;
            }
        }
        return new Contents(List.copyOf(loadedShapes), List.copyOf(loadedPresets), failures);
    }

    Stored<NamedShape> saveShape(NamedShape shape) throws IOException {
        return new Stored<>(shapes.write(toJson(shape)), shape);
    }

    Stored<BrushPreset> savePreset(BrushPreset preset) throws IOException {
        return new Stored<>(presets.write(toJson(preset)), preset);
    }

    void deleteShape(String file) throws IOException {
        shapes.delete(file);
    }

    void deletePreset(String file) throws IOException {
        presets.delete(file);
    }

    static Object parse(String json) throws IOException {
        Map<String, Object> object = asObject(Json.parse(json));
        Object format = object.get("format");
        if (SHAPE_FORMAT.equals(format)) {
            return parseShape(object);
        }
        if (PRESET_FORMAT.equals(format)) {
            return parsePreset(object);
        }
        throw new JsonFormatException("not an mc-painter brush file");
    }

    static String toJson(NamedShape shape) {
        Map<String, Object> out = header(SHAPE_FORMAT, shape.name());
        out.put("size", shape.mask().size());
        out.put("data", Base64.getEncoder().encodeToString(shape.mask().data()));
        return Json.write(out);
    }

    static String toJson(BrushPreset preset) {
        Map<String, Object> out = header(PRESET_FORMAT, preset.name());
        Map<String, Object> shape = new LinkedHashMap<>();
        switch (preset.shape()) {
            case BrushShape.Circle _ -> shape.put("type", "circle");
            case BrushShape.Square _ -> shape.put("type", "square");
            case BrushShape.Mask mask -> {
                shape.put("type", "mask");
                shape.put("name", preset.shapeName());
                shape.put("size", mask.size());
                shape.put("data", Base64.getEncoder().encodeToString(mask.data()));
            }
        }
        out.put("shape", shape);
        out.put("tool", preset.tool().id());
        out.put("radius", preset.radius());
        out.put("strength", preset.strengthPercent());
        out.put("falloff", preset.falloff().name().toLowerCase(Locale.ROOT));
        out.put("terrain", preset.terrain().id());
        out.put("biome", preset.biome());
        out.put("waterLevel", preset.waterLevel());
        out.put("autoAddTiles", preset.autoAddTiles());
        out.put("layer", preset.layer().id());
        if (preset.layer() == LayerGroup.FLOWERS) {
            out.put("flowers", preset.flowers().stream().map(Layer::id).toList());
        }
        return Json.write(out);
    }

    static String requireName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        String stripped = name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isISOControl(c) || !SafeText.neutralize(String.valueOf(c)).equals(String.valueOf(c))
                    || c == '<') {
                throw new IllegalArgumentException("name contains a forbidden character");
            }
        }
        return stripped;
    }

    private static Map<String, Object> header(String format, String name) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", format);
        out.put("version", FORMAT_VERSION);
        out.put("name", name);
        return out;
    }

    private static NamedShape parseShape(Map<String, Object> object) throws JsonFormatException {
        requireHeader(object, SHAPE_FORMAT, SHAPE_KEYS);
        return new NamedShape(string(object, "name"), mask(object));
    }

    private static BrushPreset parsePreset(Map<String, Object> object) throws JsonFormatException {
        requireHeader(object, PRESET_FORMAT, object.containsKey("flowers") ? PRESET_KEYS_WITH_FLOWERS
                : object.containsKey("layer") ? PRESET_KEYS_WITH_LAYER : PRESET_KEYS);
        Map<String, Object> shapeObject = asObject(object.get("shape"));
        String type = string(shapeObject, "type");
        BrushShape shape;
        String shapeName = "";
        switch (type) {
            case "circle" -> {
                requireKeys(shapeObject, Set.of("type"));
                shape = BrushShape.CIRCLE;
            }
            case "square" -> {
                requireKeys(shapeObject, Set.of("type"));
                shape = BrushShape.SQUARE;
            }
            case "mask" -> {
                requireKeys(shapeObject, Set.of("type", "name", "size", "data"));
                shape = mask(shapeObject);
                shapeName = string(shapeObject, "name");
            }
            default -> throw new JsonFormatException("unknown shape type");
        }
        Tool tool = Tool.fromId(string(object, "tool")).filter(Tool::visible)
                .orElseThrow(() -> new JsonFormatException("unknown tool"));
        LayerGroup layer = object.containsKey("layer") ? LayerGroup.fromId(string(object, "layer"))
                .orElseThrow(() -> new JsonFormatException("unknown layer")) : BrushPreset.DEFAULT_LAYER;
        List<Layer> flowers = object.containsKey("flowers") ? flowers(object.get("flowers"))
                : LayerGroup.DEFAULT_FLOWERS;
        if (object.containsKey("flowers") && layer != LayerGroup.FLOWERS) {
            throw new JsonFormatException("flowers only belong to the flower layer");
        }
        Brush.Falloff falloff = falloff(string(object, "falloff"));
        TerrainType terrain = TerrainType.fromId(string(object, "terrain"))
                .orElseThrow(() -> new JsonFormatException("unknown terrain"));
        return new BrushPreset(string(object, "name"), shape, shapeName, tool,
                integer(object, "radius", 0, BrushSettings.MAX_RADIUS),
                integer(object, "strength", BrushSettings.MIN_STRENGTH_PERCENT, 100), falloff, terrain,
                string(object, "biome"),
                integer(object, "waterLevel", BrushPreset.MIN_WATER_LEVEL, BrushPreset.MAX_WATER_LEVEL),
                bool(object, "autoAddTiles"), layer, flowers);
    }

    private static List<Layer> flowers(Object value) throws JsonFormatException {
        if (!(value instanceof List<?> items) || items.isEmpty() || items.size() > Layer.flowers().size()) {
            throw new JsonFormatException("flowers must be a non-empty list");
        }
        List<Layer> flowers = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof String id)) {
                throw new JsonFormatException("flowers must hold strings");
            }
            Layer flower = Layer.fromId(id).filter(Layer::flower)
                    .orElseThrow(() -> new JsonFormatException("unknown flower"));
            if (flowers.contains(flower)) {
                throw new JsonFormatException("duplicate flower");
            }
            flowers.add(flower);
        }
        return flowers;
    }

    private static BrushShape.Mask mask(Map<String, Object> object) throws JsonFormatException {
        int size = integer(object, "size", 1, BrushShape.Mask.MAX_SIZE);
        String data = string(object, "data");
        if (data.length() > 4 * ((size * size + 2) / 3)) {
            throw new JsonFormatException("mask data too long");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new JsonFormatException("mask data is not valid base64");
        }
        if (bytes.length != size * size) {
            throw new JsonFormatException("mask data has " + bytes.length + " values, expected " + size * size);
        }
        return new BrushShape.Mask(size, bytes);
    }

    private static Brush.Falloff falloff(String id) throws JsonFormatException {
        for (Brush.Falloff falloff : Brush.Falloff.values()) {
            if (falloff.name().toLowerCase(Locale.ROOT).equals(id)) {
                return falloff;
            }
        }
        throw new JsonFormatException("unknown falloff");
    }

    private static void requireHeader(Map<String, Object> object, String format, Set<String> keys)
            throws JsonFormatException {
        requireKeys(object, keys);
        if (!format.equals(object.get("format"))) {
            throw new JsonFormatException("expected format " + format);
        }
        if (!Long.valueOf(FORMAT_VERSION).equals(object.get("version"))) {
            throw new JsonFormatException("unsupported brush file version");
        }
    }

    private static Set<String> union(Set<String> keys, String extra) {
        Set<String> all = new HashSet<>(keys);
        all.add(extra);
        return Set.copyOf(all);
    }

    private static void requireKeys(Map<String, Object> object, Set<String> keys) throws JsonFormatException {
        if (!object.keySet().equals(keys)) {
            throw new JsonFormatException("unexpected or missing members");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) throws JsonFormatException {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new JsonFormatException("expected a JSON object");
    }

    private static String string(Map<String, Object> object, String key) throws JsonFormatException {
        if (object.get(key) instanceof String value) {
            return value;
        }
        throw new JsonFormatException(key + " must be a string");
    }

    private static int integer(Map<String, Object> object, String key, int min, int max) throws JsonFormatException {
        if (object.get(key) instanceof Long value && value >= min && value <= max) {
            return value.intValue();
        }
        throw new JsonFormatException(key + " must be an integer in [" + min + ", " + max + "]");
    }

    private static boolean bool(Map<String, Object> object, String key) throws JsonFormatException {
        if (object.get(key) instanceof Boolean value) {
            return value;
        }
        throw new JsonFormatException(key + " must be true or false");
    }
}
