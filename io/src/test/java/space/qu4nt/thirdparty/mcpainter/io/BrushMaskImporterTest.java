package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

class BrushMaskImporterTest {

    @TempDir
    Path dir;

    private Path write(BufferedImage image, String format, String name) throws IOException {
        Path file = dir.resolve(name);
        assertTrue(ImageIO.write(image, format, file.toFile()), format);
        return file;
    }

    private static BufferedImage rgb(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, z, rgb);
            }
        }
        return image;
    }

    @Test
    void multipliesBrightnessByAlpha() throws IOException {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 4; x++) {
            image.setRGB(x, 0, x < 2 ? 0x00FFFFFF : 0xFFFFFFFF);
            image.setRGB(x, 1, x < 2 ? 0xFF000000 : 0x80FFFFFF);
        }
        BrushMaskImporter.Source source = BrushMaskImporter.read(write(image, "png", "alpha.png"));
        assertEquals("PNG", source.format());
        for (boolean levels : new boolean[] {false, true}) {
            BrushShape.Mask mask = source.toMask(levels, false);
            assertEquals(4, mask.size());
            assertEquals(0, mask.value(0, 1));
            assertEquals(0, mask.value(0, 2));
            assertEquals(255, mask.value(3, 1));
            assertEquals(128, mask.value(3, 2));
            assertEquals(0, mask.value(3, 3));
        }
        BrushShape.Mask inverted = source.toMask(false, true);
        assertEquals(0, inverted.value(0, 1));
        assertEquals(255, inverted.value(0, 2));
        assertEquals(0, inverted.value(3, 1));
        assertEquals(0, inverted.value(3, 0));
    }

    @Test
    void detectsFormatsByContentNotExtension() throws IOException {
        BufferedImage image = rgb(16, 16, 0xFFFFFF);
        image.setRGB(0, 0, 0);
        Set<String> expected = Set.of("PNG", "JPEG", "BMP", "GIF", "TIF", "TIFF");
        for (String format : new String[] {"png", "jpeg", "bmp", "gif", "tiff"}) {
            Path file = write(image, format, "shape-" + format + ".dat");
            BrushMaskImporter.Source source = BrushMaskImporter.read(file);
            assertTrue(expected.contains(source.format()), source.format());
            assertEquals(16, source.size());
            assertTrue(source.toMask(false, false).value(8, 8) > 240, format);
        }
        Path text = Files.writeString(dir.resolve("fake.png"), "not an image at all");
        IOException error = assertThrows(IOException.class, () -> BrushMaskImporter.read(text));
        assertTrue(error.getMessage().contains("not a supported image"), error.getMessage());
        BufferedImage bits = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_BINARY);
        Path wbmp = write(bits, "wbmp", "bits.wbmp");
        assertThrows(IOException.class, () -> BrushMaskImporter.read(wbmp));
    }

    @Test
    void subsamplesLargeImagesIntoTheMaskLimit() throws IOException {
        BufferedImage wide = new BufferedImage(3000, 1000, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = wide.getRaster();
        for (int z = 0; z < 1000; z++) {
            for (int x = 0; x < 3000; x++) {
                raster.setSample(x, z, 0, 255);
            }
        }
        BrushMaskImporter.Source source = BrushMaskImporter.read(write(wide, "png", "wide.png"));
        assertEquals(BrushShape.Mask.MAX_SIZE, source.size());
        assertEquals(3000, source.sourceWidth());
        assertEquals(1000, source.sourceHeight());
        BrushShape.Mask mask = source.toMask(false, false);
        assertEquals(0, mask.value(256, 20));
        assertEquals(255, mask.value(256, 256));
        assertEquals(0, mask.value(256, 500));
    }

    @Test
    void autoLevelsStretchHeightmapRanges() {
        BufferedImage gray16 = new BufferedImage(4, 4, BufferedImage.TYPE_USHORT_GRAY);
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                gray16.getRaster().setSample(x, z, 0, 20_000 + x * 5_000);
            }
        }
        BrushMaskImporter.Source source = BrushMaskImporter.source(gray16, "PNG", 4, 4);
        BrushShape.Mask raw = source.toMask(false, false);
        assertEquals(Math.round(20_000 / 65535.0 * 255), raw.value(0, 0));
        BrushShape.Mask stretched = source.toMask(true, false);
        assertEquals(0, stretched.value(0, 0));
        assertEquals(255, stretched.value(3, 0));
        assertEquals(85, stretched.value(1, 2));
        assertEquals(255, source.toMask(true, true).value(0, 3));

        BufferedImage flat = rgb(5, 5, 0x404040);
        assertEquals(255, BrushMaskImporter.source(flat, "PNG", 5, 5).toMask(true, false).value(2, 2));
        BufferedImage black = rgb(5, 5, 0);
        assertThrows(IllegalArgumentException.class,
                () -> BrushMaskImporter.source(black, "PNG", 5, 5).toMask(true, false));
        assertEquals(255, BrushMaskImporter.source(black, "PNG", 5, 5).toMask(false, true).value(0, 0));
    }

    @Test
    void readsFloatingPointHeightmaps() {
        ComponentColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), false, false,
                Transparency.OPAQUE, DataBuffer.TYPE_FLOAT);
        WritableRaster raster = model.createCompatibleWritableRaster(3, 1);
        raster.setSample(0, 0, 0, -12.5f);
        raster.setSample(1, 0, 0, 87.5f);
        raster.setSample(2, 0, 0, Float.NaN);
        BufferedImage image = new BufferedImage(model, raster, false, null);
        BrushShape.Mask mask = BrushMaskImporter.source(image, "TIFF", 3, 1).toMask(true, false);
        assertEquals(3, mask.size());
        assertEquals(0, mask.value(0, 1));
        assertEquals(255, mask.value(1, 1));
        assertEquals(32, mask.value(2, 1));
    }

    @Test
    void rejectsLinksAndOversizedImagesBeforeDecoding() throws IOException {
        Path real = write(rgb(2, 2, 0xFFFFFF), "png", "real.png");
        Path link = Files.createSymbolicLink(dir.resolve("link.png"), real);
        assertThrows(IOException.class, () -> BrushMaskImporter.read(link));
        Path huge = dir.resolve("huge.png");
        Files.write(huge, pngHeaderOnly(BrushMaskImporter.MAX_DIMENSION + 1, 10));
        IOException error = assertThrows(IOException.class, () -> BrushMaskImporter.read(huge));
        assertTrue(error.getMessage().contains((BrushMaskImporter.MAX_DIMENSION + 1) + "x10"), error.getMessage());
    }

    private static byte[] pngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(ihdr);
        data.writeBytes("IHDR");
        data.writeInt(width);
        data.writeInt(height);
        data.write(new byte[] {8, 0, 0, 0, 0});
        byte[] chunk = ihdr.toByteArray();
        out.writeInt(chunk.length - 4);
        out.write(chunk);
        CRC32 crc = new CRC32();
        crc.update(chunk);
        out.writeInt((int) crc.getValue());
        return bytes.toByteArray();
    }
}
