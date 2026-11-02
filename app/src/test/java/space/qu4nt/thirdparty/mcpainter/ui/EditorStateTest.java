package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.world.Edit;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TilePos;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

class EditorStateTest {

    @Test
    void settingsClampValidateAndNotifyOnChange() {
        BrushSettings settings = new BrushSettings();
        AtomicInteger events = new AtomicInteger();
        settings.addListener(events::incrementAndGet);
        settings.setRadius(10_000);
        assertEquals(BrushSettings.MAX_RADIUS, settings.radius());
        settings.setRadius(BrushSettings.MAX_RADIUS);
        assertEquals(1, events.get());
        settings.setStrengthPercent(0);
        assertEquals(BrushSettings.MIN_STRENGTH_PERCENT, settings.strengthPercent());
        settings.setBiome(" plains ");
        assertEquals("minecraft:plains", settings.biome());
        settings.setBiome("mymod:glow/caves");
        assertEquals("mymod:glow/caves", settings.biome());
        assertThrows(IllegalArgumentException.class, () -> settings.setBiome("<html>"));
        assertThrows(IllegalArgumentException.class, () -> settings.setBiome("minecraft:../x"));
        assertEquals("mymod:glow/caves", settings.biome());
    }

    @Test
    void documentTracksSavedRevisionAcrossUndo() {
        Messages messages = Messages.load(Language.KOREAN);
        WorldModel model = Worlds.blank(1, 1, 64, 63, TerrainType.GRASS);
        Document document = new Document(model);
        AtomicInteger states = new AtomicInteger();
        AtomicInteger replaced = new AtomicInteger();
        document.onStateChanged(states::incrementAndGet);
        document.onModelReplaced(replaced::incrementAndGet);
        assertFalse(document.dirty());
        assertEquals("제목 없음", document.displayName(messages));
        try (Edit edit = model.beginEdit("e")) {
            edit.setHeight(0, 0, 90);
        }
        assertTrue(document.dirty());
        model.undo();
        assertFalse(document.dirty());
        model.redo();
        document.markSaved(Path.of("a", "world.mcpaint"), model.revision());
        assertFalse(document.dirty());
        assertEquals("world.mcpaint", document.displayName(messages));
        model.undo();
        assertTrue(document.dirty());

        document.replace(Worlds.blank(1, 1, 64, 63, TerrainType.SAND), null, true);
        assertTrue(document.dirty());
        assertEquals(1, replaced.get());
        assertTrue(states.get() >= 2);
        document.markSaved(Path.of("b.mcpaint"), document.model().revision());
        assertFalse(document.dirty());
    }

    @Test
    void blankWorldsAreCenteredWithoutHistory() {
        WorldModel model = Worlds.blank(3, 2, 70.5f, 62, TerrainType.SNOW);
        assertEquals(6, model.tileCount());
        assertEquals(new TilePos(-1, -1), model.tilePositions().getFirst());
        assertEquals(new TilePos(1, 0), model.tilePositions().getLast());
        assertEquals(70.5f, model.height(0, 0));
        assertEquals(TerrainType.SNOW, model.terrain(-128, -128));
        assertEquals(62, model.waterLevel(5, 5));
        assertFalse(model.canUndo());
        assertEquals(Worlds.MIN_Y, model.minY());
        assertThrows(IllegalArgumentException.class, () -> Worlds.blank(100, 100, 64, 63, TerrainType.GRASS));
        assertThrows(IllegalArgumentException.class, () -> Worlds.blank(0, 1, 64, 63, TerrainType.GRASS));
    }
}
