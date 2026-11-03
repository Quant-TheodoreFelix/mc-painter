package space.qu4nt.thirdparty.mcpainter.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class Edit implements AutoCloseable {

    private final WorldModel model;
    private final String label;
    private final Map<TilePos, Tile> before = new HashMap<>();
    private GenerationSettings generationBefore;
    private boolean closed;

    Edit(WorldModel model, String label) {
        this.model = model;
        this.label = Objects.requireNonNull(label, "label");
    }

    public String label() {
        return label;
    }

    public WorldModel model() {
        return model;
    }

    public boolean addTile(TilePos pos, float height, TerrainType terrain) {
        requireOpen();
        Map<TilePos, Tile> tiles = model.tilesForEdit();
        if (tiles.containsKey(pos)) {
            return false;
        }
        if (tiles.size() >= model.maxTiles()) {
            throw new IllegalStateException("tile limit " + model.maxTiles() + " reached");
        }
        if (!before.containsKey(pos)) {
            before.put(pos, null);
        }
        Tile tile = Tile.filled(model.clampHeight(height), terrain, model.seaLevel(), 0);
        if (model.generation().initialOres()) {
            for (Layer layer : Layer.values()) {
                if (layer.ore()) {
                    tile.fillLayer(layer, Layer.INITIAL_ORE_VALUE);
                }
            }
        }
        tiles.put(pos, tile);
        return true;
    }

    public void setGeneration(GenerationSettings settings) {
        requireOpen();
        Objects.requireNonNull(settings, "settings");
        if (generationBefore == null) {
            generationBefore = model.generation();
        }
        model.assignGeneration(settings);
    }

    public boolean removeTile(TilePos pos) {
        requireOpen();
        Objects.requireNonNull(pos, "pos");
        Map<TilePos, Tile> tiles = model.tilesForEdit();
        Tile tile = tiles.remove(pos);
        if (tile == null) {
            return false;
        }
        if (!before.containsKey(pos)) {
            before.put(pos, tile);
        }
        return true;
    }

    public boolean setHeight(int x, int z, float height) {
        Tile tile = writable(x, z);
        if (tile == null) {
            return false;
        }
        tile.setHeight(WorldModel.localIndex(x, z), model.clampHeight(height));
        return true;
    }

    public boolean setTerrain(int x, int z, TerrainType terrain) {
        Objects.requireNonNull(terrain, "terrain");
        Tile tile = writable(x, z);
        if (tile == null) {
            return false;
        }
        tile.setTerrain(WorldModel.localIndex(x, z), terrain);
        return true;
    }

    public boolean setWaterLevel(int x, int z, int level) {
        Tile tile = writable(x, z);
        if (tile == null) {
            return false;
        }
        tile.setWaterLevel(WorldModel.localIndex(x, z), model.clampWater(level));
        return true;
    }

    public boolean setLayer(int x, int z, Layer layer, int value) {
        Objects.requireNonNull(layer, "layer");
        Tile tile = writable(x, z);
        if (tile == null) {
            return false;
        }
        tile.setLayer(layer, WorldModel.localIndex(x, z), Math.clamp(value, 0, Layer.MAX_VALUE));
        return true;
    }

    public boolean fillLayer(TilePos pos, Layer layer, int value) {
        Objects.requireNonNull(layer, "layer");
        Tile tile = writable(pos);
        if (tile == null) {
            return false;
        }
        tile.fillLayer(layer, Math.clamp(value, 0, Layer.MAX_VALUE));
        return true;
    }

    public boolean setBiome(int x, int z, String biomeId) {
        Tile tile = writable(x, z);
        if (tile == null) {
            return false;
        }
        tile.setBiome(WorldModel.localIndex(x, z), model.biomeIndex(biomeId));
        return true;
    }

    private Tile writable(int x, int z) {
        return writable(TilePos.ofBlock(x, z));
    }

    private Tile writable(TilePos pos) {
        requireOpen();
        Tile tile = model.tilesForEdit().get(pos);
        if (tile == null) {
            return null;
        }
        if (!before.containsKey(pos)) {
            before.put(pos, tile.copy());
        }
        return tile;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("edit is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Map<TilePos, Tile> tiles = model.tilesForEdit();
        Iterator<Map.Entry<TilePos, Tile>> entries = before.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<TilePos, Tile> entry = entries.next();
            Tile now = tiles.get(entry.getKey());
            boolean unchanged = entry.getValue() == null ? now == null
                    : now != null && entry.getValue().sameContent(now);
            if (unchanged) {
                entries.remove();
            }
        }
        GenerationSettings changedFrom = generationBefore != null && !generationBefore.equals(model.generation())
                ? generationBefore : null;
        EditHistory.Step step = before.isEmpty() && changedFrom == null ? null
                : new EditHistory.Step(label, Collections.unmodifiableMap(new HashMap<>(before)), changedFrom);
        model.finishEdit(this, step);
    }
}
