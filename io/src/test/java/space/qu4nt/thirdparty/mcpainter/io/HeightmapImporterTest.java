package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class HeightmapImporterTest {

    @TempDir
    Path dir;

    private Path writeImage(BufferedImage image, String name) throws IOException {
        Path file = dir.resolve(name);
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("no png writer");
        }
        return file;
    }

    @Test
    void imports16BitGrayscaleWithEdgeClamping() throws IOException {
        BufferedImage image = new BufferedImage(200, 150, BufferedImage.TYPE_USHORT_GRAY);
        WritableRaster raster = image.getRaster();
        for (int z = 0; z < 150; z++) {
            for (int x = 0; x < 200; x++) {
                raster.setSample(x, z, 0, x == 199 ? 65535 : 0);
            }
        }
        WorldModel model = HeightmapImporter.importPng(writeImage(image, "h16.png"), HeightmapImporter.Options.defaults());

        assertEquals(4, model.tileCount());
        assertEquals(new TilePos(-1, -1), model.tilePositions().getFirst());
        assertEquals(40f, model.height(-128, -128), 1e-3);
        assertEquals(160f, model.height(-128 + 199, -128), 1e-3);
        assertEquals(160f, model.height(-128 + 255, -128 + 255), 1e-3);
        assertEquals(TerrainType.GRAVEL, model.terrain(-128, -128));
        assertEquals(TerrainType.SNOW, model.terrain(-128 + 199, -128));
        assertEquals(63, model.waterLevel(0, 0));
    }

    @Test
    void imports8BitAndIndexedImages() throws IOException {
        BufferedImage gray = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        gray.getRaster().setSample(0, 0, 0, 255);
        WorldModel grayModel = HeightmapImporter.importPng(writeImage(gray, "g8.png"), HeightmapImporter.Options.defaults());
        assertEquals(160f, grayModel.height(-0, -0), 1e-3);
        assertEquals(1, grayModel.tileCount());

        BufferedImage indexed = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_INDEXED);
        indexed.setRGB(0, 0, 0xFFFFFF);
        WorldModel indexedModel = HeightmapImporter.importPng(writeImage(indexed, "idx.png"),
                HeightmapImporter.Options.defaults());
        assertEquals(160f, indexedModel.height(0, 0), 1e-3);
        assertEquals(40f, indexedModel.height(1, 0), 1e-3);
    }

    @Test
    void rejectsNonPngAndSymlinks() throws IOException {
        Path text = Files.writeString(dir.resolve("fake.png"), "not an image");
        assertThrows(IOException.class, () -> HeightmapImporter.importPng(text, HeightmapImporter.Options.defaults()));
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_GRAY);
        Path real = writeImage(image, "real.png");
        Path link = Files.createSymbolicLink(dir.resolve("link.png"), real);
        assertThrows(IOException.class, () -> HeightmapImporter.importPng(link, HeightmapImporter.Options.defaults()));
    }

    @Test
    void rejectsHugeDimensionsBeforeDecoding() throws IOException {
        Path file = dir.resolve("huge.png");
        Files.write(file, pngHeaderOnly(100_000, 100_000));
        IOException error = assertThrows(IOException.class,
                () -> HeightmapImporter.importPng(file, HeightmapImporter.Options.defaults()));
        assertEquals(true, error.getMessage().contains("100000x100000"));
    }

    @Test
    void rejectsImagesNeedingTooManyTiles() throws IOException {
        BufferedImage image = new BufferedImage(200, 10, BufferedImage.TYPE_BYTE_GRAY);
        Path file = writeImage(image, "wide.png");
        HeightmapImporter.Options defaults = HeightmapImporter.Options.defaults();
        HeightmapImporter.Options tight = new HeightmapImporter.Options(defaults.minY(), defaults.maxY(),
                defaults.seaLevel(), defaults.low(), defaults.high(), 1);
        assertThrows(IOException.class, () -> HeightmapImporter.importPng(file, tight));
        assertThrows(IllegalArgumentException.class, () -> new HeightmapImporter.Options(-64, 320, 63, 100, 50, 10));
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
        byte[] iend = {'I', 'E', 'N', 'D'};
        out.writeInt(0);
        out.write(iend);
        CRC32 endCrc = new CRC32();
        endCrc.update(iend);
        out.writeInt((int) endCrc.getValue());
        return bytes.toByteArray();
    }
}
