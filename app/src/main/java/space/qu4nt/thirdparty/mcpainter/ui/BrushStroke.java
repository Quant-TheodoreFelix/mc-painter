package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.TerrainOps;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class BrushStroke {

    static final float RAISE_PER_DAB = 1.0f;
    static final double PAINT_THRESHOLD = 1e-9;

    interface Listener {

        void changed(SequencedSet<TilePos> tiles);

        void tileLimitReached(int limit);
    }

    private final WorldModel model;
    private final Edit edit;
    private final Tool tool;
    private final Brush brush;
    private final float strength;
    private final TerrainType terrain;
    private final int waterLevel;
    private final String biome;
    private final boolean autoAddTiles;
    private final Listener listener;
    private float flattenTarget = Float.NaN;
    private int lastX;
    private int lastZ;
    private boolean limitReported;
    private boolean finished;

    private BrushStroke(WorldModel model, Edit edit, Tool tool, BrushSettings settings, Listener listener) {
        this.model = model;
        this.edit = edit;
        this.tool = tool;
        this.brush = settings.brush();
        this.strength = settings.strength();
        this.terrain = settings.terrain();
        this.waterLevel = settings.waterLevel();
        this.biome = settings.biome();
        this.autoAddTiles = settings.autoAddTiles();
        this.listener = listener;
    }

    static BrushStroke begin(WorldModel model, BrushSettings settings, Tool tool, String label, int x, int z,
            Listener listener) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(listener, "listener");
        Edit edit = model.beginEdit(label);
        BrushStroke stroke = new BrushStroke(model, edit, tool, settings, listener);
        try {
            stroke.dab(x, z);
        } catch (RuntimeException e) {
            stroke.finish();
            throw e;
        }
        return stroke;
    }

    Tool tool() {
        return tool;
    }

    boolean finished() {
        return finished;
    }

    int spacing() {
        int r = brush.radius();
        return Math.max(1, tool.continuous() ? r / 3 : r / 2);
    }

    void dragTo(int x, int z) {
        requireOpen();
        double dx = x - lastX;
        double dz = z - lastZ;
        double distance = Math.hypot(dx, dz);
        int spacing = spacing();
        int steps = (int) Math.floor(distance / spacing);
        if (steps == 0) {
            return;
        }
        int startX = lastX;
        int startZ = lastZ;
        for (int i = 1; i <= steps; i++) {
            double t = i * spacing / distance;
            dab((int) Math.round(startX + dx * t), (int) Math.round(startZ + dz * t));
        }
    }

    void repeat() {
        requireOpen();
        if (tool.continuous()) {
            dab(lastX, lastZ);
        }
    }

    void finish() {
        if (!finished) {
            finished = true;
            edit.close();
        }
    }

    private void dab(int x, int z) {
        lastX = x;
        lastZ = z;
        boolean added = (tool == Tool.ADD_TILES || autoAddTiles) && addTiles(x, z);
        if (Float.isNaN(flattenTarget)) {
            flattenTarget = model.contains(x, z) ? (float) Math.floor(model.height(x, z)) + 0.5f
                    : model.seaLevel() + 0.5f;
        }
        switch (tool) {
            case RAISE -> TerrainOps.raise(edit, x, z, brush, RAISE_PER_DAB * strength);
            case LOWER -> TerrainOps.raise(edit, x, z, brush, -RAISE_PER_DAB * strength);
            case SMOOTH -> TerrainOps.smooth(edit, x, z, brush, strength);
            case FLATTEN -> TerrainOps.flatten(edit, x, z, brush, flattenTarget, strength);
            case TERRAIN -> TerrainOps.paintTerrain(edit, x, z, brush, terrain, PAINT_THRESHOLD);
            case WATER -> TerrainOps.setWaterLevel(edit, x, z, brush, waterLevel, PAINT_THRESHOLD);
            case BIOME -> TerrainOps.paintBiome(edit, x, z, brush, biome, PAINT_THRESHOLD);
            case ADD_TILES -> {
            }
        }
        int margin = brush.radius() + (added ? Tile.SIZE : TileRenderer.SHADE_MARGIN);
        listener.changed(tilesAround(x, z, margin));
    }

    private boolean addTiles(int x, int z) {
        int r = brush.radius();
        TilePos min = TilePos.ofBlock(x - r, z - r);
        TilePos max = TilePos.ofBlock(x + r, z + r);
        double reach = (r + 0.5) * (r + 0.5);
        boolean added = false;
        for (int tz = min.z(); tz <= max.z(); tz++) {
            for (int tx = min.x(); tx <= max.x(); tx++) {
                TilePos pos = new TilePos(tx, tz);
                double nearestX = Math.clamp(x, pos.minBlockX(), pos.minBlockX() + Tile.SIZE - 1);
                double nearestZ = Math.clamp(z, pos.minBlockZ(), pos.minBlockZ() + Tile.SIZE - 1);
                double ddx = nearestX - x;
                double ddz = nearestZ - z;
                boolean touches = brush.shape().square() || ddx * ddx + ddz * ddz <= reach;
                if (!touches || model.tile(pos).isPresent()) {
                    continue;
                }
                if (model.tileCount() >= model.maxTiles()) {
                    if (!limitReported) {
                        limitReported = true;
                        listener.tileLimitReached(model.maxTiles());
                    }
                    return added;
                }
                added |= edit.addTile(pos, model.seaLevel() + 1, terrain);
            }
        }
        return added;
    }

    static SequencedSet<TilePos> tilesAround(int x, int z, int margin) {
        TilePos min = TilePos.ofBlock(x - margin, z - margin);
        TilePos max = TilePos.ofBlock(x + margin, z + margin);
        SequencedSet<TilePos> tiles = new LinkedHashSet<>();
        for (int tz = min.z(); tz <= max.z(); tz++) {
            for (int tx = min.x(); tx <= max.x(); tx++) {
                tiles.add(new TilePos(tx, tz));
            }
        }
        return tiles;
    }

    private void requireOpen() {
        if (finished) {
            throw new IllegalStateException("stroke is finished");
        }
    }
}
