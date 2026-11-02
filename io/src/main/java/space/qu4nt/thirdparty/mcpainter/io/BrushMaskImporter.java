package space.qu4nt.thirdparty.mcpainter.io;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Path;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

public final class BrushMaskImporter {

    public static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    public static final int MAX_DIMENSION = 4096;

    private BrushMaskImporter() {
    }

    public static BrushShape.Mask importPng(Path file) throws IOException {
        return toMask(SafePng.read(file, "brush image", MAX_FILE_BYTES, MAX_DIMENSION, (width, height) -> {
        }));
    }

    static BrushShape.Mask toMask(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int side = Math.max(width, height);
        int size = Math.min(side, BrushShape.Mask.MAX_SIZE);
        int offsetX = (side - width) / 2;
        int offsetZ = (side - height) / 2;
        int[] cellsX = new int[size];
        int[] cellsZ = new int[size];
        for (int i = 0; i < side; i++) {
            cellsX[cell(i, side, size)]++;
            cellsZ[cell(i, side, size)]++;
        }
        double[] sums = new double[size * size];
        Sampler sampler = new Sampler(image);
        for (int z = 0; z < height; z++) {
            int cz = cell(z + offsetZ, side, size);
            for (int x = 0; x < width; x++) {
                sums[cz * size + cell(x + offsetX, side, size)] += sampler.weight(x, z);
            }
        }
        byte[] data = new byte[size * size];
        for (int cz = 0; cz < size; cz++) {
            for (int cx = 0; cx < size; cx++) {
                double average = sums[cz * size + cx] / ((double) cellsX[cx] * cellsZ[cz]);
                data[cz * size + cx] = (byte) Math.clamp(Math.round(average * 255), 0, 255);
            }
        }
        return new BrushShape.Mask(size, data);
    }

    private static int cell(int position, int side, int size) {
        return (int) ((long) position * size / side);
    }

    private static final class Sampler {

        private final BufferedImage image;
        private final Raster raster;
        private final boolean gray;
        private final boolean alpha;
        private final double valueMax;
        private final double alphaMax;

        Sampler(BufferedImage image) {
            this.image = image;
            this.raster = image.getRaster();
            ColorModel model = image.getColorModel();
            this.gray = !(model instanceof IndexColorModel) && model.getNumColorComponents() == 1;
            this.alpha = model.hasAlpha();
            this.valueMax = gray ? (1L << raster.getSampleModel().getSampleSize(0)) - 1 : 255;
            this.alphaMax = gray && alpha ? (1L << raster.getSampleModel().getSampleSize(1)) - 1 : 255;
        }

        double weight(int x, int z) {
            if (gray) {
                double value = raster.getSample(x, z, 0) / valueMax;
                return alpha ? value * (raster.getSample(x, z, 1) / alphaMax) : value;
            }
            int argb = image.getRGB(x, z);
            double luma = (0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF) + 0.0722 * (argb & 0xFF))
                    / 255.0;
            return luma * (((argb >>> 24) & 0xFF) / 255.0);
        }
    }
}
