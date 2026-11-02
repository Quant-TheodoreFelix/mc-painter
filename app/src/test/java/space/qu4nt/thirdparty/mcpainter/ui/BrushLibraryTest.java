package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class BrushLibraryTest {

    @TempDir
    Path dir;

    private static BrushShape.Mask mask() {
        byte[] data = new byte[9];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 30);
        }
        return new BrushShape.Mask(3, data);
    }

    private static BrushPreset preset(BrushShape shape) {
        return new BrushPreset("산 만들기", shape, "바위", Tool.FLATTEN, 24, 40, Brush.Falloff.LINEAR,
                TerrainType.STONE, "stony_peaks", 70, true);
    }

    @Test
    void savesAndLoadsShapesAndPresets() throws IOException {
        BrushLibrary library = new BrushLibrary(dir.resolve("brushes"));
        BrushLibrary.Stored<NamedShape> shape = library.saveShape(new NamedShape(" 바위 ", mask()));
        BrushLibrary.Stored<BrushPreset> withMask = library.savePreset(preset(mask()));
        BrushLibrary.Stored<BrushPreset> square = library.savePreset(preset(BrushShape.SQUARE));
        assertEquals("바위", shape.value().name());
        assertTrue(Files.isRegularFile(dir.resolve("brushes").resolve("shapes").resolve(shape.file())));

        BrushLibrary.Contents contents = new BrushLibrary(dir.resolve("brushes")).load();
        assertEquals(0, contents.failures());
        assertEquals(List.of(shape), contents.shapes());
        assertEquals(2, contents.presets().size());
        assertTrue(contents.presets().contains(withMask));
        assertTrue(contents.presets().contains(square));
        BrushPreset loaded = withMask.value();
        assertEquals("minecraft:stony_peaks", loaded.biome());
        assertEquals("", square.value().shapeName());

        library.deletePreset(square.file());
        assertEquals(1, library.load().presets().size());
    }

    @Test
    void countsUnreadableFilesInsteadOfFailing() throws IOException {
        BrushLibrary library = new BrushLibrary(dir);
        library.savePreset(preset(BrushShape.CIRCLE));
        Files.writeString(dir.resolve("presets").resolve("00000000-0000-0000-0000-000000000000.json"), "{oops");
        Files.createDirectories(dir.resolve("shapes"));
        Files.writeString(dir.resolve("shapes").resolve("00000000-0000-0000-0000-000000000001.json"),
                BrushLibrary.toJson(preset(BrushShape.CIRCLE)));
        BrushLibrary.Contents contents = library.load();
        assertEquals(1, contents.presets().size());
        assertEquals(0, contents.shapes().size());
        assertEquals(2, contents.failures());
    }

    @Test
    void parsesCanonicalJsonAndRejectsEveryDeviation() throws IOException {
        String good = BrushLibrary.toJson(preset(mask()));
        assertEquals(preset(mask()), BrushLibrary.parse(good));
        assertEquals(new NamedShape("바위", mask()), BrushLibrary.parse(BrushLibrary.toJson(new NamedShape("바위", mask()))));
        String[][] mutations = {
            {"\"version\": 1", "\"version\": 2"},
            {"\"format\": \"mc-painter-preset\"", "\"format\": \"other\""},
            {"\"name\": \"산 만들기\"", "\"name\": \"\""},
            {"\"name\": \"산 만들기\"", "\"name\": \"" + "x".repeat(65) + "\""},
            {"\"name\": \"산 만들기\"", "\"name\": \"a\\u202eb\""},
            {"\"name\": \"산 만들기\"", "\"name\": \"<html>\""},
            {"\"name\": \"산 만들기\"", "\"name\": \"a\\u0000\""},
            {"\"tool\": \"flatten\"", "\"tool\": \"explode\""},
            {"\"radius\": 24", "\"radius\": 257"},
            {"\"radius\": 24", "\"radius\": -1"},
            {"\"radius\": 24", "\"radius\": \"24\""},
            {"\"strength\": 40", "\"strength\": 0"},
            {"\"falloff\": \"linear\"", "\"falloff\": \"LINEAR\""},
            {"\"terrain\": \"stone\"", "\"terrain\": \"lava\""},
            {"\"biome\": \"minecraft:stony_peaks\"", "\"biome\": \"minecraft:../x\""},
            {"\"waterLevel\": 70", "\"waterLevel\": 5000"},
            {"\"autoAddTiles\": true", "\"autoAddTiles\": 1"},
            {"\"autoAddTiles\": true", "\"autoAddTiles\": true, \"extra\": 1"},
            {"\"type\": \"mask\"", "\"type\": \"star\""},
            {"\"size\": 3", "\"size\": 4"},
            {"\"size\": 3", "\"size\": 0"},
            {"\"size\": 3", "\"size\": 257"},
        };
        for (String[] mutation : mutations) {
            assertTrue(good.contains(mutation[0]), mutation[0]);
            String bad = good.replace(mutation[0], mutation[1]);
            assertThrows(Exception.class, () -> BrushLibrary.parse(bad), () -> "accepted " + mutation[1]);
        }
        String data = Base64Holder.data(good);
        assertThrows(IOException.class, () -> BrushLibrary.parse(good.replace(data, "!!!!")));
        assertThrows(IOException.class, () -> BrushLibrary.parse(good.replace(data, data + "AAAA")));
        assertThrows(IOException.class, () -> BrushLibrary.parse(good.replace(data, data.substring(4))));
        String circle = BrushLibrary.toJson(preset(BrushShape.CIRCLE));
        assertThrows(IOException.class,
                () -> BrushLibrary.parse(circle.replace("\"type\": \"circle\"", "\"type\": \"circle\", \"size\": 3")));
        assertThrows(IOException.class, () -> BrushLibrary.parse("[]"));
        assertThrows(IOException.class, () -> BrushLibrary.parse("{\"format\": \"mc-painter-shape\"}"));
    }

    @Test
    void settingsApplyPresetsOnceAndClampWater() {
        BrushSettings settings = new BrushSettings();
        AtomicInteger events = new AtomicInteger();
        settings.addListener(events::incrementAndGet);
        settings.apply(new BrushPreset("p", mask(), "m", Tool.WATER, 7, 33, Brush.Falloff.CONSTANT,
                TerrainType.SAND, "desert", 4000, true), -64, 320);
        assertEquals(1, events.get());
        assertEquals(Tool.WATER, settings.tool());
        assertEquals(320, settings.waterLevel());
        assertEquals(mask(), settings.shape());
        assertEquals(new Brush(7, Brush.Falloff.CONSTANT, mask()), settings.brush());
        assertTrue(settings.autoAddTiles());
        BrushPreset back = BrushPreset.of("again", settings, "m");
        assertEquals("minecraft:desert", back.biome());
        assertEquals(320, back.waterLevel());
    }

    @Test
    void catalogKeepsSessionShapesUntilStored() {
        BrushCatalog catalog = new BrushCatalog(Messages.load(Language.KOREAN));
        AtomicInteger events = new AtomicInteger();
        catalog.addListener(events::incrementAndGet);
        assertEquals(2, catalog.shapes().size());
        assertSame(catalog.shapes().get(1), catalog.entryFor(BrushShape.SQUARE, "x"));
        assertEquals(0, events.get());
        ShapeEntry session = catalog.entryFor(mask(), "바위");
        assertEquals(ShapeEntry.Kind.SESSION, session.kind());
        assertSame(session, catalog.entryFor(mask(), "다른 이름"));
        assertEquals(3, catalog.shapes().size());
        catalog.addShape(ShapeEntry.stored("f.json", new NamedShape("바위", mask())));
        assertEquals(3, catalog.shapes().size());
        assertEquals(ShapeEntry.Kind.STORED, catalog.entryFor(mask(), "x").kind());
        assertEquals(16, session.icon().getIconWidth());
        catalog.load(new BrushLibrary.Contents(List.of(), List.of(), 0));
        assertEquals(2, catalog.shapes().size());
    }

    @Test
    void squareBrushesAutoAddCornerTiles() {
        WorldModel circleModel = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        BrushSettings circle = new BrushSettings();
        circle.setRadius(10);
        circle.setAutoAddTiles(true);
        BrushStroke.begin(circleModel, circle, Tool.TERRAIN, "t", 120, 120, noop()).finish();
        assertEquals(3, circleModel.tileCount());

        WorldModel squareModel = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        BrushSettings square = new BrushSettings();
        square.setRadius(10);
        square.setAutoAddTiles(true);
        square.setShape(BrushShape.SQUARE);
        BrushStroke.begin(squareModel, square, Tool.TERRAIN, "t", 120, 120, noop()).finish();
        assertEquals(4, squareModel.tileCount());
        assertTrue(squareModel.contains(129, 129));
    }

    @Test
    void derivesShapeNamesFromFiles() {
        assertEquals("rock", BrushActions.baseName(Path.of("dir", "rock.png")));
        assertEquals(".hidden", BrushActions.baseName(Path.of(".hidden")));
        assertEquals(64, BrushActions.baseName(Path.of("x".repeat(80) + ".png")).length());
    }

    private static BrushStroke.Listener noop() {
        return new BrushStroke.Listener() {
            @Override
            public void changed(java.util.SequencedSet<space.qu4nt.thirdparty.mcpainter.world.TilePos> tiles) {
            }

            @Override
            public void tileLimitReached(int limit) {
            }
        };
    }

    private static final class Base64Holder {

        static String data(String json) {
            int key = json.indexOf("\"data\": \"") + 9;
            return json.substring(key, json.indexOf('"', key));
        }
    }
}
