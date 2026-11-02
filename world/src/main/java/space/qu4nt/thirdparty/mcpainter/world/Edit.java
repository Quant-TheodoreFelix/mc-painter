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
        tiles.put(pos, Tile.filled(model.clampHeight(height), terrain, model.seaLevel(), 0));
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

    public boolean setBiome(int x, int z, String biomeId) {
        Tile tile = writable(x, z);
        if (tile == null) {
            return false;
        }
        tile.setBiome(WorldModel.localIndex(x, z), model.biomeIndex(biomeId));
        return true;
    }

    private Tile writable(int x, int z) {
        requireOpen();
        TilePos pos = TilePos.ofBlock(x, z);
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
            if (entry.getValue() != null && now != null && entry.getValue().sameContent(now)) {
                entries.remove();
            }
        }
        model.finishEdit(this, before.isEmpty() ? null : new EditHistory.Step(label, Collections.unmodifiableMap(new HashMap<>(before))));
    }
}
