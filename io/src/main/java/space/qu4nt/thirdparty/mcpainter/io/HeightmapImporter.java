package space.qu4nt.thirdparty.mcpainter.io;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

public final class HeightmapImporter {

    public static final int MAX_DIMENSION = 8192;
    public static final long MAX_FILE_BYTES = 256L * 1024 * 1024;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public record Options(int minY, int maxY, int seaLevel, float low, float high, int maxTiles) {

        public static Options defaults() {
            return new Options(-64, 320, 63, 40, 160, WorldModel.DEFAULT_MAX_TILES);
        }

        public Options {
            if (!(Float.isFinite(low) && Float.isFinite(high)) || low >= high) {
                throw new IllegalArgumentException("low must be below high");
            }
            if (low < minY || high > maxY) {
                throw new IllegalArgumentException("height mapping outside world range");
            }
        }
    }

    private HeightmapImporter() {
    }

    public static WorldModel importPng(Path file, Options options) throws IOException {
        AtomicFiles.requireNotSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("not a regular file: " + file);
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("heightmap file exceeds " + MAX_FILE_BYTES + " bytes");
        }
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Arrays.equals(in.readNBytes(PNG_SIGNATURE.length), PNG_SIGNATURE)) {
                throw new IOException("heightmap must be a PNG file");
            }
        }
        float[][] samples = decode(file, options);
        return buildModel(samples, options);
    }

    private static float[][] decode(Path file, Options options) throws IOException {
        ImageIO.setUseCache(false);
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new IOException("no PNG decoder available");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                throw new IOException("cannot open heightmap " + file);
            }
            reader.setInput(in, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                throw new IOException("heightmap size " + width + "x" + height + " outside 1.." + MAX_DIMENSION);
            }
            long tiles = (long) ceilDiv(width, Tile.SIZE) * ceilDiv(height, Tile.SIZE);
            if (tiles > options.maxTiles()) {
                throw new IOException("heightmap needs " + tiles + " tiles, limit " + options.maxTiles());
            }
            BufferedImage image = reader.read(0);
            return toFractions(image);
        } finally {
            reader.dispose();
        }
    }

    private static float[][] toFractions(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[][] fractions = new float[height][width];
        Raster raster = image.getRaster();
        int bits = raster.getSampleModel().getSampleSize(0);
        boolean direct = !(image.getColorModel() instanceof IndexColorModel) && (bits == 8 || bits == 16);
        float max = direct ? (1 << bits) - 1 : 255f;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int sample = direct ? raster.getSample(x, z, 0) : (image.getRGB(x, z) >> 16) & 0xFF;
                fractions[z][x] = sample / max;
            }
        }
        return fractions;
    }

    private static WorldModel buildModel(float[][] fractions, Options options) {
        int height = fractions.length;
        int width = fractions[0].length;
        int tilesX = ceilDiv(width, Tile.SIZE);
        int tilesZ = ceilDiv(height, Tile.SIZE);
        int firstTileX = -(tilesX / 2);
        int firstTileZ = -(tilesZ / 2);
        WorldModel.Builder builder = WorldModel.builder(options.minY(), options.maxY(), options.seaLevel())
                .maxTiles(options.maxTiles());
        float range = options.high() - options.low();
        float snowLine = options.low() + range * 0.9f;
        for (int tz = 0; tz < tilesZ; tz++) {
            for (int tx = 0; tx < tilesX; tx++) {
                float[] heights = new float[Tile.AREA];
                byte[] terrain = new byte[Tile.AREA];
                short[] water = new short[Tile.AREA];
                short[] biomes = new short[Tile.AREA];
                for (int lz = 0; lz < Tile.SIZE; lz++) {
                    for (int lx = 0; lx < Tile.SIZE; lx++) {
                        int px = Math.min(tx * Tile.SIZE + lx, width - 1);
                        int pz = Math.min(tz * Tile.SIZE + lz, height - 1);
                        float h = options.low() + fractions[pz][px] * range;
                        int i = Tile.index(lx, lz);
                        heights[i] = h;
                        terrain[i] = (byte) autoTerrain(h, options.seaLevel(), snowLine).ordinal();
                        water[i] = (short) options.seaLevel();
                    }
                }
                builder.tile(new TilePos(firstTileX + tx, firstTileZ + tz), heights, terrain, water, biomes);
            }
        }
        return builder.build();
    }

    static TerrainType autoTerrain(float height, int seaLevel, float snowLine) {
        if (height < seaLevel - 4) {
            return TerrainType.GRAVEL;
        }
        if (height < seaLevel + 2) {
            return TerrainType.SAND;
        }
        if (height >= snowLine && height > seaLevel + 20) {
            return TerrainType.SNOW;
        }
        return TerrainType.GRASS;
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
