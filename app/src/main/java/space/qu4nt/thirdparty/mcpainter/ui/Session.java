package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import space.qu4nt.thirdparty.mcpainter.io.AreaLoader;
import space.qu4nt.thirdparty.mcpainter.io.Progress;
import space.qu4nt.thirdparty.mcpainter.io.Workspace;
import space.qu4nt.thirdparty.mcpainter.io.WorldSpec;
import space.qu4nt.thirdparty.mcpainter.world.Tile;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

final class Session {

    private Session() {
    }

    static WorldModel empty() {
        return WorldModel.builder(Worlds.MIN_Y, Worlds.MAX_Y, Worlds.SEA_LEVEL).readOnly().build();
    }

    static WorldModel closedArea(Workspace workspace) {
        return workspace.spec().builder().readOnly().build();
    }

    static int[] thumbnail(WorldModel model, TilePos pos) {
        BufferedImage image = TileRenderer.render(model, pos, TileRenderer.MAX_LOD);
        int[] pixels = new int[Workspace.THUMB_PIXELS];
        for (int z = 0; z < Workspace.THUMB_SIDE; z++) {
            for (int x = 0; x < Workspace.THUMB_SIDE; x++) {
                pixels[z * Workspace.THUMB_SIDE + x] = image.getRGB(x, z) & 0xFFFFFF;
            }
        }
        return pixels;
    }

    static int baseColor(WorldSpec spec) {
        if (!spec.base().filled()) {
            return -1;
        }
        int ground = TileRenderer.terrainColor(spec.base().terrain());
        int top = (int) Math.floor(spec.base().height());
        int depth = spec.seaLevel() - 1 - top;
        if (depth > 0) {
            return TileRenderer.waterColor(ground, depth);
        }
        double range = spec.maxY() - spec.minY();
        double elevation = 0.85 + 0.3 * Math.clamp((spec.base().height() - spec.minY()) / range, 0.0, 1.0);
        return TileRenderer.multiply(ground, elevation);
    }

    static long areaCost(Workspace workspace, TileRect area) {
        TileRect loaded = AreaLoader.loadedRect(workspace, area);
        return AreaLoader.tilesIn(workspace, loaded);
    }

    static boolean fits(Workspace workspace, TileRect area, int budgetTiles) {
        return areaCost(workspace, area) <= budgetTiles;
    }

    static Optional<TileRect> initialArea(Workspace workspace, int budgetTiles) {
        Optional<TileRect> remembered = workspace.state().area();
        if (remembered.isPresent() && fits(workspace, remembered.get(), budgetTiles)) {
            return remembered;
        }
        TileRect bounds = workspace.spec().bounds();
        return fits(workspace, bounds, budgetTiles) ? Optional.of(bounds) : Optional.empty();
    }

    static int capacity(Workspace workspace, TileRect area, int budgetTiles) {
        if (workspace.spec().base().filled()) {
            return Math.toIntExact(Math.max(1, AreaLoader.loadedRect(workspace, area).count()));
        }
        return Math.max(budgetTiles, Math.toIntExact(areaCost(workspace, area)));
    }

    static WorldModel load(Workspace workspace, TileRect area, int budgetTiles, int threads, Progress progress)
            throws IOException {
        return AreaLoader.load(workspace, area, capacity(workspace, area, budgetTiles), threads, progress);
    }

    static List<Workspace.Write> pendingWrites(WorldModel model, Workspace workspace) {
        List<Workspace.Write> writes = new ArrayList<>();
        List<TilePos> dirty = new ArrayList<>(model.dirtyTiles());
        dirty.sort(null);
        for (TilePos pos : dirty) {
            if (!workspace.spec().bounds().contains(pos)) {
                continue;
            }
            Optional<Tile> tile = model.tile(pos);
            if (tile.isEmpty() || model.isBaseTile(pos)) {
                if (workspace.stored(pos)) {
                    writes.add(Workspace.Write.remove(pos));
                }
            } else {
                writes.add(new Workspace.Write(pos, tile.get(), thumbnail(model, pos), true));
            }
        }
        return writes;
    }

    static boolean flush(WorldModel model, Workspace workspace) throws IOException {
        Set<TilePos> dirty = model.dirtyTiles();
        List<Workspace.Write> writes = pendingWrites(model, workspace);
        WorldSpec spec = workspace.spec();
        WorldSpec next = spec.withBiomes(merged(spec.biomes(), model.biomes()))
                .withGeneration(model.editable().isPresent() ? model.generation() : spec.generation());
        boolean changed = !writes.isEmpty() || !next.equals(spec);
        workspace.write(writes, true);
        Workspace.State state = workspace.state().withArea(model.editable());
        workspace.update(next, changed ? state.withUnsaved(true) : state);
        model.markClean(dirty);
        return changed;
    }

    static List<String> merged(List<String> stored, List<String> current) {
        List<String> result = new ArrayList<>(stored);
        for (String id : current) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    static List<TilePos> areaTiles(WorldModel model) {
        List<TilePos> tiles = new ArrayList<>();
        Optional<TileRect> area = model.editable();
        if (area.isEmpty()) {
            return tiles;
        }
        for (TilePos pos : model.tilePositions()) {
            if (area.get().contains(pos)) {
                tiles.add(pos);
            }
        }
        return tiles;
    }

    static String workspaceName(Optional<Path> project, String fallback) {
        return project.map(Path::getFileName).map(Path::toString).orElse(fallback);
    }
}
