package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JSpinner;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.anvil.world.UnsupportedVersionException;
import space.qu4nt.thirdparty.mcpainter.anvil.world.WorldInUseException;
import space.qu4nt.thirdparty.mcpainter.io.HeightmapImporter;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;

class ProjectActionsTest {

    @Test
    void appendsProjectExtensionOnce() {
        assertEquals(Path.of("dir", "world.mcpaint"), FileChoosers.withProjectExtension(Path.of("dir", "world")));
        assertEquals(Path.of("dir", "world.mcpaint"),
                FileChoosers.withProjectExtension(Path.of("dir", "world.mcpaint")));
        assertEquals(Path.of("dir", "World.MCPAINT"),
                FileChoosers.withProjectExtension(Path.of("dir", "World.MCPAINT")));
        assertEquals(Path.of("a.b.mcpaint"), FileChoosers.withProjectExtension(Path.of("a.b")));
    }

    @Test
    void findsMinecraftSavesPerPlatform() {
        assertEquals(Path.of("/Users/u/Library/Application Support/minecraft/saves"),
                FileChoosers.minecraftSaves("Mac OS X", "/Users/u", null));
        assertEquals(Path.of("/home/u/.minecraft/saves"), FileChoosers.minecraftSaves("Linux", "/home/u", null));
        assertEquals(Path.of("/home/u"), FileChoosers.minecraftSaves("Windows 11", "/home/u", " "));
        assertEquals(Path.of("/roaming", ".minecraft", "saves"),
                FileChoosers.minecraftSaves("Windows 11", "/home/u", "/roaming"));
        assertEquals(Path.of("/home/u"), FileChoosers.minecraftSaves("Windows 11", "/home/u", "bad\0path"));
    }

    @Test
    void formsReadDefaultsAndRejectTooManyTiles() {
        Messages messages = Messages.load(Language.KOREAN);
        Forms.NewWorldForm form = new Forms.NewWorldForm(messages);
        assertEquals(new Forms.NewWorld(4, 4, 64, 63, TerrainType.GRASS), form.read().orElseThrow());
        for (Component c : form.panel().getComponents()) {
            if (c instanceof JSpinner spinner && ((Number) spinner.getValue()).intValue() == 4) {
                spinner.setValue(Forms.MAX_SIDE_TILES);
            }
        }
        assertTrue(form.read().isEmpty());
        HeightmapImporter.Options options = new Forms.ImportForm(messages).read();
        assertEquals(HeightmapImporter.Options.defaults(), options);
    }

    @Test
    void mapsExportFailuresToMessages() {
        Messages messages = Messages.load(Language.KOREAN);
        String inUse = ProjectActions.exportFailureKey(new WorldInUseException("locked"));
        String unsupported = ProjectActions.exportFailureKey(new UnsupportedVersionException(1343));
        String other = ProjectActions.exportFailureKey(new IOException("disk"));
        assertEquals("export.inUse", inUse);
        assertEquals("export.unsupported", unsupported);
        assertEquals("error.export", other);
        for (String key : new String[] {inUse, unsupported, other}) {
            assertFalse(messages.text(key).equals(key), key);
        }
    }
}
