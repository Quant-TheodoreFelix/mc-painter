package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Arrays;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class Worlds {

    static final int MIN_Y = -64;
    static final int MAX_Y = 320;
    static final int SEA_LEVEL = 63;
    static final int BASE_HEIGHT = 64;
    static final int STARTUP_TILES = 4;

    private Worlds() {
    }

    static WorldModel blank(int widthTiles, int depthTiles, float height, int seaLevel, TerrainType terrain) {
        return blank(widthTiles, depthTiles, height, seaLevel, terrain, GenerationSettings.DEFAULT);
    }

    static WorldModel blank(int widthTiles, int depthTiles, float height, int seaLevel, TerrainType terrain,
            GenerationSettings generation) {
        if (widthTiles < 1 || depthTiles < 1 || (long) widthTiles * depthTiles > WorldModel.DEFAULT_MAX_TILES) {
            throw new IllegalArgumentException("tile count out of range");
        }
        WorldModel.Builder builder = WorldModel.builder(MIN_Y, MAX_Y, seaLevel).generation(generation);
        float[] heights = new float[Tile.AREA];
        Arrays.fill(heights, Math.clamp(height, MIN_Y, MAX_Y));
        byte[] terrainOrdinals = new byte[Tile.AREA];
        Arrays.fill(terrainOrdinals, (byte) terrain.ordinal());
        short[] water = new short[Tile.AREA];
        Arrays.fill(water, (short) seaLevel);
        short[] biomes = new short[Tile.AREA];
        int startX = -(widthTiles / 2);
        int startZ = -(depthTiles / 2);
        for (int z = 0; z < depthTiles; z++) {
            for (int x = 0; x < widthTiles; x++) {
                builder.tile(new TilePos(startX + x, startZ + z), heights, terrainOrdinals, water, biomes);
            }
        }
        return builder.build();
    }

    static WorldModel startup() {
        return blank(STARTUP_TILES, STARTUP_TILES, BASE_HEIGHT, SEA_LEVEL, TerrainType.GRASS,
                new GenerationSettings(Seeds.random()));
    }
}
