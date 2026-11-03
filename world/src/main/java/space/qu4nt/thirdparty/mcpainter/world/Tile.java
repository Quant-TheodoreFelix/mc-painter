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

    private Tile(float[] heights, byte[] terrain, short[] water, short[] biomes) {
        this.heights = heights;
        this.terrain = terrain;
        this.water = water;
        this.biomes = biomes;
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
        return new Tile(heights.clone(), terrain.clone(), water.clone(), biomes.clone());
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
        return Arrays.equals(heights, other.heights) && Arrays.equals(terrain, other.terrain)
                && Arrays.equals(water, other.water) && Arrays.equals(biomes, other.biomes);
    }
}
