package space.qu4nt.thirdparty.mcpainter.io;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Path;
import space.qu4nt.thirdparty.mcpainter.world.BrushShape;

public final class BrushMaskImporter {

    public static final long MAX_FILE_BYTES = 512L * 1024 * 1024;
    public static final int MAX_DIMENSION = 16384;

    private BrushMaskImporter() {
    }

    public static final class Source {

        private final int size;
        private final float[] values;
        private final float[] coverage;
        private final String format;
        private final int sourceWidth;
        private final int sourceHeight;

        private Source(int size, float[] values, float[] coverage, String format, int sourceWidth, int sourceHeight) {
            this.size = size;
            this.values = values;
            this.coverage = coverage;
            this.format = format;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
        }

        public int size() {
            return size;
        }

        public String format() {
            return format;
        }

        public int sourceWidth() {
            return sourceWidth;
        }

        public int sourceHeight() {
            return sourceHeight;
        }

        public BrushShape.Mask toMask(boolean autoLevels, boolean invert) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < values.length; i++) {
                if (coverage[i] > 0) {
                    min = Math.min(min, values[i]);
                    max = Math.max(max, values[i]);
                }
            }
            if (max == Double.NEGATIVE_INFINITY) {
                throw new IllegalArgumentException("image has no visible pixels");
            }
            byte[] data = new byte[values.length];
            boolean any = false;
            for (int i = 0; i < values.length; i++) {
                double value;
                if (autoLevels) {
                    value = max > min ? (values[i] - min) / (max - min) : (max > 0 ? 1 : 0);
                } else {
                    value = values[i];
                }
                value = Math.clamp(value, 0.0, 1.0);
                if (invert) {
                    value = 1 - value;
                }
                int level = (int) Math.round(value * coverage[i] * 255);
                data[i] = (byte) level;
                any |= level != 0;
            }
            if (!any) {
                throw new IllegalArgumentException("image has no brush content");
            }
            return new BrushShape.Mask(size, data);
        }
    }

    public static Source read(Path file) throws IOException {
        SafeImage.Decoded decoded = SafeImage.read(file, "brush image", MAX_FILE_BYTES, MAX_DIMENSION,
                SafeImage.COMMON, BrushShape.Mask.MAX_SIZE, (width, height) -> {
                });
        return source(decoded.image(), decoded.format(), decoded.width(), decoded.height());
    }

    static Source source(BufferedImage image, String format, int sourceWidth, int sourceHeight) {
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
        double[] weighted = new double[size * size];
        double[] alphas = new double[size * size];
        Sampler sampler = new Sampler(image);
        for (int z = 0; z < height; z++) {
            int row = cell(z + offsetZ, side, size) * size;
            for (int x = 0; x < width; x++) {
                int index = row + cell(x + offsetX, side, size);
                double alpha = sampler.alpha(x, z);
                weighted[index] += sampler.value(x, z) * alpha;
                alphas[index] += alpha;
            }
        }
        float[] values = new float[size * size];
        float[] coverage = new float[size * size];
        for (int cz = 0; cz < size; cz++) {
            for (int cx = 0; cx < size; cx++) {
                int index = cz * size + cx;
                values[index] = alphas[index] > 0 ? (float) (weighted[index] / alphas[index]) : 0f;
                coverage[index] = (float) (alphas[index] / ((double) cellsX[cx] * cellsZ[cz]));
            }
        }
        return new Source(size, values, coverage, format, sourceWidth, sourceHeight);
    }

    private static int cell(int position, int side, int size) {
        return (int) ((long) position * size / side);
    }

    private static final class Sampler {

        private final BufferedImage image;
        private final Raster raster;
        private final boolean gray;
        private final boolean floating;
        private final boolean alpha;
        private final double valueMax;
        private final double alphaMax;

        Sampler(BufferedImage image) {
            this.image = image;
            this.raster = image.getRaster();
            ColorModel model = image.getColorModel();
            int type = raster.getDataBuffer().getDataType();
            this.gray = !(model instanceof IndexColorModel) && model.getNumColorComponents() == 1;
            this.floating = type == DataBuffer.TYPE_FLOAT || type == DataBuffer.TYPE_DOUBLE;
            this.alpha = model.hasAlpha();
            this.valueMax = gray && !floating ? (1L << raster.getSampleModel().getSampleSize(0)) - 1 : 1;
            this.alphaMax = gray && alpha && !floating ? (1L << raster.getSampleModel().getSampleSize(1)) - 1 : 1;
        }

        double value(int x, int z) {
            if (gray) {
                double sample = raster.getSampleDouble(x, z, 0);
                return Double.isFinite(sample) ? sample / valueMax : 0;
            }
            int argb = image.getRGB(x, z);
            return (0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF) + 0.0722 * (argb & 0xFF)) / 255.0;
        }

        double alpha(int x, int z) {
            if (!alpha) {
                return 1;
            }
            if (gray) {
                double sample = raster.getSampleDouble(x, z, 1) / alphaMax;
                return Double.isFinite(sample) ? Math.clamp(sample, 0.0, 1.0) : 0;
            }
            return ((image.getRGB(x, z) >>> 24) & 0xFF) / 255.0;
        }
    }
}
