package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapeBatchTest {

    @TempDir
    Path dir;

    private Path image(String name, int rgb) throws IOException {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < 20; z++) {
            for (int x = 0; x < 40; x++) {
                image.setRGB(x, z, x < 20 ? 0 : rgb);
            }
        }
        Path file = dir.resolve(name);
        assertTrue(ImageIO.write(image, "png", file.toFile()));
        return file;
    }

    @Test
    void readsPreviewsAndListsFilesThatAreNotImages() throws IOException {
        Path ridge = image("ridge.png", 0xFFFFFF);
        Path text = Files.writeString(dir.resolve("notes.png"), "not an image");
        ShapeBatch.Previews previews = ShapeBatch.previews(List.of(ridge, text), 3);
        assertEquals(1, previews.items().size());
        assertEquals("ridge", previews.items().getFirst().name());
        assertTrue(previews.items().getFirst().preview().size() <= ShapeBatch.PREVIEW_SIZE);
        assertEquals(List.of("notes.png"), previews.unreadable());
        assertEquals(3, previews.omitted());
    }

    @Test
    void readsAtMostTheBatchLimit() throws IOException {
        Path ridge = image("ridge.png", 0xFFFFFF);
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < ShapeBatch.MAX_FILES + 5; i++) {
            files.add(ridge);
        }
        ShapeBatch.Previews previews = ShapeBatch.previews(files, 0);
        assertEquals(ShapeBatch.MAX_FILES, previews.items().size());
        assertEquals(5, previews.omitted());
    }

    @Test
    void savesFullSizeMasksAndSkipsEmptyOnes() throws IOException {
        Path bright = image("bright.png", 0xFFFFFF);
        Path dark = image("dark.png", 0);
        ShapeBatch.Previews previews = ShapeBatch.previews(List.of(bright, dark), 0);
        assertFalse(ShapeBatch.empty(previews.items().get(0), false, false));
        assertTrue(ShapeBatch.empty(previews.items().get(1), false, false));
        assertFalse(ShapeBatch.empty(previews.items().get(1), false, true));

        List<NamedShape> saved = new ArrayList<>();
        ShapeBatch.Saved result = ShapeBatch.save(previews.items(), false, false, shape -> {
            saved.add(shape);
            return new BrushLibrary.Stored<>("f" + saved.size(), shape);
        });
        assertNull(result.saveError());
        assertEquals(List.of("dark"), result.skipped());
        assertEquals(1, result.stored().size());
        assertEquals("bright", saved.getFirst().name());
        assertEquals(40, saved.getFirst().mask().size());
        assertEquals(255, saved.getFirst().mask().value(30, 20));
    }

    @Test
    void stopsAtTheFirstSaveFailure() throws IOException {
        Path one = image("one.png", 0xFFFFFF);
        Path two = image("two.png", 0xFFFFFF);
        ShapeBatch.Previews previews = ShapeBatch.previews(List.of(one, two, one), 0);
        IOException full = new IOException("disk full");
        List<String> attempts = new ArrayList<>();
        ShapeBatch.Saved result = ShapeBatch.save(previews.items(), true, false, shape -> {
            attempts.add(shape.name());
            if (attempts.size() == 2) {
                throw full;
            }
            return new BrushLibrary.Stored<>("f", shape);
        });
        assertEquals(List.of("one", "two"), attempts);
        assertEquals(1, result.stored().size());
        assertSame(full, result.saveError());
    }

    @Test
    void namesComeFromFileNamesWithoutForbiddenCharacters() {
        assertEquals("cliff edge", ShapeBatch.nameFor(Path.of("cliff edge.png")));
        assertEquals("_b>", ShapeBatch.nameFor(Path.of("<b>.png")));
        assertEquals("a_b", ShapeBatch.nameFor(Path.of("a‮b.png")));
        assertEquals(".png", ShapeBatch.nameFor(Path.of(".png")));
        assertEquals(BrushLibrary.MAX_NAME_LENGTH, ShapeBatch.nameFor(Path.of("x".repeat(100) + ".png")).length());
        for (String name : List.of("cliff edge", "_b>", "a_b")) {
            assertEquals(name, BrushLibrary.requireName(name));
        }
    }

    @Test
    void longNameListsAreShortened() {
        Messages ko = Messages.load(Language.KOREAN);
        assertEquals("a, b", ShapeBatch.list(ko, List.of("a", "b")));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < ShapeBatch.LISTED_NAMES + 3; i++) {
            many.add("n" + i);
        }
        assertEquals("n0, n1, n2, n3, n4, n5, n6, n7 외 3개", ShapeBatch.list(ko, many));
    }
}
