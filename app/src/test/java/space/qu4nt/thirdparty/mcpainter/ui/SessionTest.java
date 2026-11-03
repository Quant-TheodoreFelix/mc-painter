package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.qu4nt.thirdparty.mcpainter.io.Progress;
import space.qu4nt.thirdparty.mcpainter.io.Workspace;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class SessionTest {

    @TempDir
    Path dir;

    private Workspace flatWorkspace(TileRect bounds) throws IOException {
        Forms.NewWorld world = new Forms.NewWorld(bounds, 64, 63, TerrainType.GRASS, new GenerationSettings(9));
        return Workspace.create(dir, world.spec(), "test");
    }

    @Test
    void editsSurviveSwitchingAreasAndOnlyTheAreaIsEditable() throws IOException {
        try (Workspace workspace = flatWorkspace(TileRect.centeredBlocks(80_000, 80_000))) {
            assertEquals(Optional.empty(), Session.initialArea(workspace, 2_000));
            TileRect area = new TileRect(10, 10, 12, 12);
            assertEquals(25, Session.areaCost(workspace, area));
            WorldModel model = Session.load(workspace, area, 2_000, 2, Progress.NONE);
            assertEquals(25, model.tileCount());
            Document document = new Document(Session.empty());
            document.open(workspace, model, null);
            assertFalse(document.dirty());
            int x = 11 * 128 + 5;
            try (Edit edit = model.beginEdit("paint")) {
                assertTrue(edit.setHeight(x, x, 90));
                assertTrue(edit.setBiome(x, x, "minecraft:desert"));
                assertFalse(edit.setHeight(9 * 128 + 5, x, 90));
            }
            assertTrue(document.dirty());
            assertTrue(Session.flush(model, workspace));
            document.synced();
            assertTrue(document.dirty());
            assertEquals(1, workspace.tileCount());
            assertTrue(workspace.entry(new TilePos(11, 11)).orElseThrow().pending());
            assertEquals(Optional.of(area), workspace.state().area());
            assertTrue(workspace.spec().biomes().contains("minecraft:desert"));

            WorldModel other = Session.load(workspace, new TileRect(100, 100, 100, 100), 2_000, 1, Progress.NONE);
            assertEquals(9, other.tileCount());
            WorldModel again = Session.load(workspace, new TileRect(11, 11, 11, 11), 2_000, 1, Progress.NONE);
            assertEquals(90f, again.height(x, x));
            assertEquals("minecraft:desert", again.biome(x, x));
            assertEquals(64f, again.height(x + 128, x));
            try (Edit edit = again.beginEdit("reset")) {
                assertTrue(edit.resetTile(new TilePos(11, 11)));
            }
            Session.flush(again, workspace);
            assertEquals(0, workspace.tileCount());
            assertEquals(Optional.of(new TileRect(11, 11, 11, 11)), Session.initialArea(workspace, 2_000));
        }
    }

    @Test
    void smallWorldsOpenAsOneAreaAndVoidWorldsCountStoredTiles() throws IOException {
        try (Workspace workspace = flatWorkspace(TileRect.centered(4, 4))) {
            assertEquals(Optional.of(TileRect.centered(4, 4)), Session.initialArea(workspace, 2_000));
            assertEquals(Optional.empty(), Session.initialArea(workspace, 10));
            WorldModel model = Session.load(workspace, TileRect.centered(4, 4), 2_000, 1, Progress.NONE);
            assertEquals(16, model.tileCount());
            assertEquals(16, Session.areaTiles(model).size());
            assertEquals(Workspace.THUMB_PIXELS, Session.thumbnail(model, new TilePos(0, 0)).length);
        }
        WorldModel legacy = Worlds.blank(2, 1, 70, 63, TerrainType.SAND);
        try (Workspace workspace = Workspace.create(dir, space.qu4nt.thirdparty.mcpainter.io.WorldSpec.of(legacy),
                "legacy")) {
            List<Workspace.Write> writes = Session.pendingWrites(legacy, workspace);
            assertTrue(writes.isEmpty());
            for (TilePos pos : legacy.tilePositions()) {
                workspace.write(List.of(new Workspace.Write(pos, legacy.tile(pos).orElseThrow(),
                        Session.thumbnail(legacy, pos), true)), true);
            }
            assertEquals(Optional.of(TileRect.WORLD), Session.initialArea(workspace, 100));
            WorldModel loaded = Session.load(workspace, TileRect.WORLD, 100, 2, Progress.NONE);
            assertEquals(2, loaded.tileCount());
            assertEquals(100, loaded.maxTiles());
            try (Edit edit = loaded.beginEdit("add")) {
                assertTrue(edit.addTile(new TilePos(50, 50), 70, TerrainType.GRASS));
            }
            Session.flush(loaded, workspace);
            assertEquals(3, workspace.tileCount());
        }
    }
}
