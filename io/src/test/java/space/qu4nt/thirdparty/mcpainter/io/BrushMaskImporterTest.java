package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

class BrushMaskImporterTest {

    @TempDir
    Path dir;

    @Test
    void multipliesBrightnessByAlpha() throws IOException {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 4; x++) {
            image.setRGB(x, 0, x < 2 ? 0x00FFFFFF : 0xFFFFFFFF);
            image.setRGB(x, 1, x < 2 ? 0xFF000000 : 0x80FFFFFF);
        }
        Path file = dir.resolve("alpha.png");
        ImageIO.write(image, "png", file.toFile());
        BrushShape.Mask mask = BrushMaskImporter.importPng(file);
        assertEquals(4, mask.size());
        assertEquals(0, mask.value(0, 0));
        assertEquals(0, mask.value(0, 1));
        assertEquals(0, mask.value(0, 2));
        assertEquals(255, mask.value(3, 1));
        assertEquals(128, mask.value(3, 2));
        assertEquals(0, mask.value(3, 3));
    }

    @Test
    void padsToASquareAndDownsamples() {
        BufferedImage wide = new BufferedImage(300, 100, BufferedImage.TYPE_BYTE_GRAY);
        for (int z = 0; z < 100; z++) {
            for (int x = 0; x < 300; x++) {
                wide.getRaster().setSample(x, z, 0, 255);
            }
        }
        BrushShape.Mask mask = BrushMaskImporter.toMask(wide);
        assertEquals(BrushShape.Mask.MAX_SIZE, mask.size());
        assertEquals(0, mask.value(128, 10));
        assertEquals(255, mask.value(128, 128));
        assertEquals(255, mask.value(0, 128));
        assertEquals(0, mask.value(128, 250));

        BufferedImage gray16 = new BufferedImage(3, 3, BufferedImage.TYPE_USHORT_GRAY);
        gray16.getRaster().setSample(1, 1, 0, 65535);
        gray16.getRaster().setSample(0, 0, 0, 32768);
        BrushShape.Mask small = BrushMaskImporter.toMask(gray16);
        assertEquals(3, small.size());
        assertEquals(255, small.value(1, 1));
        assertEquals(128, small.value(0, 0));
        assertEquals(0, small.value(2, 2));
    }

    @Test
    void rejectsNonPngAndOversizedImages() throws IOException {
        Path text = Files.writeString(dir.resolve("fake.png"), "not an image");
        assertThrows(IOException.class, () -> BrushMaskImporter.importPng(text));
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_BYTE_GRAY);
        Path real = dir.resolve("real.png");
        ImageIO.write(image, "png", real.toFile());
        Path link = Files.createSymbolicLink(dir.resolve("link.png"), real);
        assertThrows(IOException.class, () -> BrushMaskImporter.importPng(link));
        BufferedImage huge = new BufferedImage(BrushMaskImporter.MAX_DIMENSION + 1, 1, BufferedImage.TYPE_BYTE_GRAY);
        Path big = dir.resolve("big.png");
        ImageIO.write(huge, "png", big.toFile());
        IOException error = assertThrows(IOException.class, () -> BrushMaskImporter.importPng(big));
        assertTrue(error.getMessage().contains("4097x1"));
    }
}
