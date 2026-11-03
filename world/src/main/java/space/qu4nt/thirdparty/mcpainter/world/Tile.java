package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Arrays;
import java.util.Objects;

public final class Tile {

    public static final int SHIFT = 7;
    public static final int SIZE = 1 << SHIFT;
    public static final int AREA = SIZE * SIZE;
    static final long ESTIMATED_BYTES = (long) AREA * (Float.BYTES + Byte.BYTES + Short.BYTES + Short.BYTES);

    private final float[] heights;
    private final byte[] terrain;
    private final short[] water;
    private final short[] biomes;
    private final byte[][] layers;
    private final int[] layerFill;

    private Tile(float[] heights, byte[] terrain, short[] water, short[] biomes, byte[][] layers, int[] layerFill) {
        this.heights = heights;
        this.terrain = terrain;
        this.water = water;
        this.biomes = biomes;
        this.layers = layers;
        this.layerFill = layerFill;
    }

    private Tile(float[] heights, byte[] terrain, short[] water, short[] biomes) {
        this(heights, terrain, water, biomes, new byte[Layer.count()][], new int[Layer.count()]);
    }

    static Tile filled(float height, TerrainType type, int waterLevel, int biomeIndex) {
        float[] heights = new float[AREA];
        byte[] terrain = new byte[AREA];
        short[] water = new short[AREA];
        short[] biomes = new short[AREA];
        Arrays.fill(heights, height);
        Arrays.fill(terrain, (byte) type.ordinal());
        Arrays.fill(water, (short) waterLevel);
        Arrays.fill(biomes, (short) biomeIndex);
        return new Tile(heights, terrain, water, biomes);
    }

    static Tile of(float[] heights, byte[] terrain, short[] water, short[] biomes) {
        requireLength(heights.length, "heights");
        requireLength(terrain.length, "terrain");
        requireLength(water.length, "water");
        requireLength(biomes.length, "biomes");
        return new Tile(heights.clone(), terrain.clone(), water.clone(), biomes.clone());
    }

    private static void requireLength(int length, String what) {
        if (length != AREA) {
            throw new IllegalArgumentException(what + " must have " + AREA + " entries, got " + length);
        }
    }

    public static int index(int localX, int localZ) {
        Objects.checkIndex(localX, SIZE);
        Objects.checkIndex(localZ, SIZE);
        return (localZ << SHIFT) | localX;
    }

    Tile copy() {
        byte[][] layerCopy = new byte[layers.length][];
        for (int i = 0; i < layers.length; i++) {
            layerCopy[i] = layers[i] == null ? null : layers[i].clone();
        }
        return new Tile(heights.clone(), terrain.clone(), water.clone(), biomes.clone(), layerCopy, layerFill.clone());
    }

    long estimatedBytes() {
        long bytes = ESTIMATED_BYTES;
        for (byte[] values : layers) {
            bytes += values == null ? 0 : AREA;
        }
        return bytes;
    }

    public int layer(Layer layer, int index) {
        byte[] values = layers[layer.ordinal()];
        return values == null ? layerFill[layer.ordinal()] : values[index] & 0xFF;
    }

    public boolean layerUniform(Layer layer) {
        return layers[layer.ordinal()] == null;
    }

    public int layerFill(Layer layer) {
        if (layers[layer.ordinal()] != null) {
            throw new IllegalStateException(layer + " is not uniform");
        }
        return layerFill[layer.ordinal()];
    }

    public byte[] layerValues(Layer layer) {
        byte[] values = layers[layer.ordinal()];
        if (values == null) {
            byte[] filled = new byte[AREA];
            Arrays.fill(filled, (byte) layerFill[layer.ordinal()]);
            return filled;
        }
        return values.clone();
    }

    public boolean layerEmpty(Layer layer) {
        byte[] values = layers[layer.ordinal()];
        if (values == null) {
            return layerFill[layer.ordinal()] == 0;
        }
        for (byte value : values) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    void setLayer(Layer layer, int index, int value) {
        int ordinal = layer.ordinal();
        byte[] values = layers[ordinal];
        if (values == null) {
            if (layerFill[ordinal] == value) {
                return;
            }
            values = new byte[AREA];
            Arrays.fill(values, (byte) layerFill[ordinal]);
            layers[ordinal] = values;
        }
        values[index] = (byte) value;
    }

    void fillLayer(Layer layer, int value) {
        layers[layer.ordinal()] = null;
        layerFill[layer.ordinal()] = value;
    }

    void loadLayer(Layer layer, byte[] values) {
        requireLength(values.length, layer.id());
        layers[layer.ordinal()] = values.clone();
    }

    public float height(int index) {
        return heights[index];
    }

    public TerrainType terrain(int index) {
        return TerrainType.fromOrdinal(terrain[index]);
    }

    public int waterLevel(int index) {
        return water[index];
    }

    public int biomeIndex(int index) {
        return biomes[index] & 0xFFFF;
    }

    public float[] heights() {
        return heights.clone();
    }

    public byte[] terrainOrdinals() {
        return terrain.clone();
    }

    public short[] waterLevels() {
        return water.clone();
    }

    public short[] biomeIndices() {
        return biomes.clone();
    }

    void setHeight(int index, float value) {
        heights[index] = value;
    }

    void setTerrain(int index, TerrainType type) {
        terrain[index] = (byte) type.ordinal();
    }

    void setWaterLevel(int index, int level) {
        water[index] = (short) level;
    }

    void markBiomes(boolean[] used) {
        int last = -1;
        for (short value : biomes) {
            int index = value & 0xFFFF;
            if (index != last) {
                used[index] = true;
                last = index;
            }
        }
    }

    void setBiome(int index, int biomeIndex) {
        biomes[index] = (short) biomeIndex;
    }

    boolean sameContent(Tile other) {
        if (!(Arrays.equals(heights, other.heights) && Arrays.equals(terrain, other.terrain)
                && Arrays.equals(water, other.water) && Arrays.equals(biomes, other.biomes))) {
            return false;
        }
        for (Layer layer : Layer.values()) {
            if (!sameLayer(other, layer)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameLayer(Tile other, Layer layer) {
        int ordinal = layer.ordinal();
        if (layers[ordinal] == null && other.layers[ordinal] == null) {
            return layerFill[ordinal] == other.layerFill[ordinal];
        }
        if (layers[ordinal] != null && other.layers[ordinal] != null) {
            return Arrays.equals(layers[ordinal], other.layers[ordinal]);
        }
        for (int i = 0; i < AREA; i++) {
            if (layer(layer, i) != other.layer(layer, i)) {
                return false;
            }
        }
        return true;
    }
}
