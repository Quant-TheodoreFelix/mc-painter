package space.qu4nt.thirdparty.mcpainter.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class WorldModel {

    public static final int DEFAULT_MAX_TILES = 2048;
    public static final int MAX_BIOMES = 0xFFFF;
    public static final String DEFAULT_BIOME = "minecraft:plains";

    private static final Pattern BIOME_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+(/[a-z0-9_.-]+)*");

    private final int minY;
    private final int maxY;
    private final int seaLevel;
    private final int maxTiles;
    private final List<String> biomes;
    private final Map<TilePos, Tile> tiles;
    private final EditHistory history = new EditHistory();
    private Edit activeEdit;

    private WorldModel(int minY, int maxY, int seaLevel, int maxTiles, List<String> biomes, Map<TilePos, Tile> tiles) {
        this.minY = minY;
        this.maxY = maxY;
        this.seaLevel = seaLevel;
        this.maxTiles = maxTiles;
        this.biomes = biomes;
        this.tiles = tiles;
    }

    public static WorldModel create(int minY, int maxY, int seaLevel) {
        return builder(minY, maxY, seaLevel).build();
    }

    public static Builder builder(int minY, int maxY, int seaLevel) {
        return new Builder(minY, maxY, seaLevel);
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public int seaLevel() {
        return seaLevel;
    }

    public int maxTiles() {
        return maxTiles;
    }

    public List<String> biomes() {
        return Collections.unmodifiableList(biomes);
    }

    public String biome(int index) {
        return biomes.get(index);
    }

    public List<String> usedBiomes() {
        boolean[] used = new boolean[biomes.size()];
        for (Tile tile : tiles.values()) {
            tile.markBiomes(used);
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < used.length; i++) {
            if (used[i]) {
                result.add(biomes.get(i));
            }
        }
        return result;
    }

    public int tileCount() {
        return tiles.size();
    }

    public List<TilePos> tilePositions() {
        List<TilePos> positions = new ArrayList<>(tiles.keySet());
        Collections.sort(positions);
        return positions;
    }

    public Optional<Tile> tile(TilePos pos) {
        return Optional.ofNullable(tiles.get(pos));
    }

    public boolean contains(int x, int z) {
        return tiles.containsKey(TilePos.ofBlock(x, z));
    }

    public float height(int x, int z) {
        return requireTile(x, z).height(localIndex(x, z));
    }

    public TerrainType terrain(int x, int z) {
        return requireTile(x, z).terrain(localIndex(x, z));
    }

    public int waterLevel(int x, int z) {
        return requireTile(x, z).waterLevel(localIndex(x, z));
    }

    public String biome(int x, int z) {
        return biomes.get(biomeIndex(x, z));
    }

    public int biomeIndex(int x, int z) {
        return requireTile(x, z).biomeIndex(localIndex(x, z));
    }

    public Edit beginEdit(String label) {
        if (activeEdit != null) {
            throw new IllegalStateException("an edit is already in progress: " + activeEdit.label());
        }
        activeEdit = new Edit(this, label);
        return activeEdit;
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public Optional<String> undoLabel() {
        return history.undoLabel();
    }

    public long revision() {
        return history.revision();
    }

    public boolean undo() {
        requireNoActiveEdit();
        return history.undo(tiles);
    }

    public boolean redo() {
        requireNoActiveEdit();
        return history.redo(tiles);
    }

    float clampHeight(float value) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException("height must be a number");
        }
        return Math.clamp(value, minY, maxY);
    }

    int clampWater(int level) {
        return Math.clamp(level, minY, maxY);
    }

    int biomeIndex(String id) {
        int existing = biomes.indexOf(id);
        if (existing >= 0) {
            return existing;
        }
        requireBiomeId(id);
        if (biomes.size() >= MAX_BIOMES) {
            throw new IllegalStateException("too many biomes");
        }
        biomes.add(id);
        return biomes.size() - 1;
    }

    Map<TilePos, Tile> tilesForEdit() {
        return tiles;
    }

    void finishEdit(Edit edit, EditHistory.Step step) {
        if (activeEdit != edit) {
            throw new IllegalStateException("edit is not active");
        }
        activeEdit = null;
        if (step != null) {
            history.push(step);
        }
    }

    private void requireNoActiveEdit() {
        if (activeEdit != null) {
            throw new IllegalStateException("cannot undo or redo during an edit");
        }
    }

    private Tile requireTile(int x, int z) {
        Tile tile = tiles.get(TilePos.ofBlock(x, z));
        if (tile == null) {
            throw new IndexOutOfBoundsException("no tile at block (" + x + ", " + z + ")");
        }
        return tile;
    }

    static int localIndex(int x, int z) {
        return Tile.index(x & (Tile.SIZE - 1), z & (Tile.SIZE - 1));
    }

    static void requireBiomeId(String id) {
        if (id == null || id.length() > 256 || !BIOME_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid biome id: " + id);
        }
        for (String segment : id.split("[:/]")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("invalid biome id: " + id);
            }
        }
    }

    public static final class Builder {

        private final int minY;
        private final int maxY;
        private final int seaLevel;
        private int maxTiles = DEFAULT_MAX_TILES;
        private final List<String> biomes = new ArrayList<>(List.of(DEFAULT_BIOME));
        private final Map<TilePos, Tile> tiles = new HashMap<>();

        private Builder(int minY, int maxY, int seaLevel) {
            if (minY >= maxY || minY % 16 != 0 || maxY % 16 != 0) {
                throw new IllegalArgumentException("height range must be section aligned with minY < maxY");
            }
            if (seaLevel < minY || seaLevel > maxY) {
                throw new IllegalArgumentException("sea level outside height range");
            }
            this.minY = minY;
            this.maxY = maxY;
            this.seaLevel = seaLevel;
        }

        public Builder maxTiles(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("maxTiles must be positive");
            }
            maxTiles = value;
            return this;
        }

        public Builder biomes(List<String> ids) {
            if (ids.isEmpty() || ids.size() > MAX_BIOMES) {
                throw new IllegalArgumentException("biome palette size out of range");
            }
            List<String> copy = new ArrayList<>(ids.size());
            for (String id : ids) {
                requireBiomeId(id);
                if (copy.contains(id)) {
                    throw new IllegalArgumentException("duplicate biome id " + id);
                }
                copy.add(id);
            }
            biomes.clear();
            biomes.addAll(copy);
            return this;
        }

        public Builder tile(TilePos pos, float[] heights, byte[] terrain, short[] water, short[] biomeIndices) {
            Objects.requireNonNull(pos, "pos");
            if (tiles.containsKey(pos)) {
                throw new IllegalArgumentException("duplicate tile " + pos);
            }
            if (tiles.size() >= maxTiles) {
                throw new IllegalArgumentException("tile limit " + maxTiles + " exceeded");
            }
            Tile tile = Tile.of(heights, terrain, water, biomeIndices);
            for (int i = 0; i < Tile.AREA; i++) {
                float height = tile.height(i);
                if (!(height >= minY && height <= maxY)) {
                    throw new IllegalArgumentException("height out of range in tile " + pos);
                }
                int ordinal = terrain[i];
                if (ordinal < 0 || ordinal >= TerrainType.values().length) {
                    throw new IllegalArgumentException("unknown terrain in tile " + pos);
                }
                if (tile.waterLevel(i) < minY || tile.waterLevel(i) > maxY) {
                    throw new IllegalArgumentException("water level out of range in tile " + pos);
                }
            }
            tiles.put(pos, tile);
            return this;
        }

        public WorldModel build() {
            for (Map.Entry<TilePos, Tile> entry : tiles.entrySet()) {
                for (int i = 0; i < Tile.AREA; i++) {
                    if (entry.getValue().biomeIndex(i) >= biomes.size()) {
                        throw new IllegalArgumentException("biome index out of range in tile " + entry.getKey());
                    }
                }
            }
            return new WorldModel(minY, maxY, seaLevel, maxTiles, new ArrayList<>(biomes), new HashMap<>(tiles));
        }
    }
}
