package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.world.BaseTerrain;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class WorkspaceTest {

    @TempDir
    Path dir;

    private static final TileRect BOUNDS = new TileRect(-40, -40, 40, 40);

    private static WorldSpec spec() {
        return new WorldSpec(-64, 320, 63, List.of("minecraft:plains"), new GenerationSettings(3, true), BOUNDS,
                BaseTerrain.flat(64.5f, TerrainType.GRASS));
    }

    private static WorldModel edited(TilePos pos) {
        WorldModel model = spec().builder().editable(BOUNDS).baseTile(pos).build();
        try (Edit edit = model.beginEdit("paint")) {
            edit.setHeight(pos.minBlockX() + 3, pos.minBlockZ() + 4, 100.25f);
            edit.setLayer(pos.minBlockX() + 3, pos.minBlockZ() + 4, Layer.POPPY, 77);
            edit.setBiome(pos.minBlockX(), pos.minBlockZ(), "minecraft:desert");
        }
        return model;
    }

    private static int[] thumb(int value) {
        int[] pixels = new int[Workspace.THUMB_PIXELS];
        java.util.Arrays.fill(pixels, value);
        return pixels;
    }

    @Test
    void storesTilesAcrossGroupsAndReopens() throws IOException {
        Path root = dir.resolve("workspaces");
        TilePos near = new TilePos(1, 2);
        TilePos far = new TilePos(-33, 35);
        Path folder;
        try (Workspace workspace = Workspace.create(root, spec(), "demo")) {
            folder = workspace.directory();
            WorldModel a = edited(near);
            WorldModel b = edited(far);
            workspace.write(List.of(new Workspace.Write(near, a.tile(near).orElseThrow(), thumb(1), true),
                    new Workspace.Write(far, b.tile(far).orElseThrow(), thumb(2), true)), true);
            workspace.update(workspace.spec().withBiomes(a.biomes()), workspace.state()
                    .withArea(Optional.of(new TileRect(0, 0, 3, 3))));
            assertEquals(2, workspace.tileCount());
            assertThrows(WorkspaceLockedException.class, () -> Workspace.open(folder));
            assertTrue(Workspace.list(root).getFirst().locked());
            workspace.markDumped(List.of(near));
            assertEquals(1, workspace.pendingCount());
        }
        List<Workspace.Summary> summaries = Workspace.list(root);
        assertEquals(1, summaries.size());
        assertEquals("demo", summaries.getFirst().state().name());
        assertEquals(2, summaries.getFirst().tiles());
        assertFalse(summaries.getFirst().locked());
        try (Workspace again = Workspace.open(folder)) {
            assertEquals(Optional.of(new TileRect(0, 0, 3, 3)), again.state().area());
            assertFalse(again.entry(near).orElseThrow().pending());
            assertTrue(again.entry(far).orElseThrow().pending());
            assertArrayEquals(thumb(2), again.entry(far).orElseThrow().thumb());
            WorldModel.Builder builder = again.spec().builder();
            again.loadInto(builder, far);
            WorldModel loaded = builder.build();
            assertEquals(100.25f, loaded.height(far.minBlockX() + 3, far.minBlockZ() + 4));
            assertEquals(77, loaded.layer(far.minBlockX() + 3, far.minBlockZ() + 4, Layer.POPPY));
            assertEquals("minecraft:desert", loaded.biome(far.minBlockX(), far.minBlockZ()));
            again.write(List.of(Workspace.Write.remove(far)), true);
            assertFalse(again.stored(far));
            assertThrows(IOException.class, () -> again.readRaw(far));
        }
        Workspace.delete(folder);
        assertTrue(Workspace.list(root).isEmpty());
    }

    @Test
    void rejectsTamperedFiles() throws IOException {
        Path root = dir.resolve("workspaces");
        TilePos pos = new TilePos(1, 1);
        TilePos other = new TilePos(2, 1);
        Path folder;
        try (Workspace workspace = Workspace.create(root, spec(), "demo")) {
            folder = workspace.directory();
            workspace.write(List.of(new Workspace.Write(pos, edited(pos).tile(pos).orElseThrow(), thumb(1), true),
                    new Workspace.Write(other, edited(other).tile(other).orElseThrow(), thumb(1), true)), true);
        }
        Path group = folder.resolve(Workspace.TILES).resolve("g.0.0");
        Files.copy(group.resolve("t.2.1.dat"), group.resolve("t.1.1.dat"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (Workspace again = Workspace.open(folder)) {
            assertThrows(ProjectFormatException.class, () -> again.loadInto(again.spec().builder(), pos));
            assertThrows(IllegalArgumentException.class, () -> again.write(List.of(new Workspace.Write(
                    new TilePos(99, 0), edited(pos).tile(pos).orElseThrow(), thumb(1), true)), true));
        }
        Files.writeString(group.resolve(Workspace.INDEX), "garbage");
        assertThrows(ProjectFormatException.class, () -> Workspace.open(folder));
        assertThrows(IOException.class, () -> Workspace.open(dir));
        Path link = Files.createSymbolicLink(root.resolve("0123456789abcdef"), folder);
        assertThrows(IOException.class, () -> Workspace.open(link));
    }

    @Test
    void dataFoldersFollowEachPlatform() {
        assertEquals(Path.of("/Users/u/Library/Application Support/mc-painter"),
                AppDirectories.data("Mac OS X", "/Users/u", null, null));
        assertEquals(Path.of("/local", "mc-painter"), AppDirectories.data("Windows 11", "/home/u", "/local", null));
        assertEquals(Path.of("/home/u/AppData/Local/mc-painter"),
                AppDirectories.data("Windows 11", "/home/u", "relative", null));
        assertEquals(Path.of("/home/u/.local/share/mc-painter"), AppDirectories.data("Linux", "/home/u", null, null));
        assertEquals(Path.of("/xdg/mc-painter"), AppDirectories.data("Linux", "/home/u", null, "/xdg"));
    }
}
