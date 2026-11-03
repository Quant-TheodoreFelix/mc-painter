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
import space.qu4nt.thirdparty.mcpainter.world.GenerationSettings;
import space.qu4nt.thirdparty.mcpainter.world.BaseTerrain;
import space.qu4nt.thirdparty.mcpainter.world.Layer;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;
import space.qu4nt.thirdparty.mcpainter.world.TileRect;
import space.qu4nt.thirdparty.mcpainter.world.WorldModel;

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
    void nativeDialogResultsResolveToPaths() {
        assertEquals(FileChoosers.Platform.MAC, FileChoosers.Platform.of("Mac OS X"));
        assertEquals(FileChoosers.Platform.WINDOWS, FileChoosers.Platform.of("Windows 11"));
        assertEquals(FileChoosers.Platform.OTHER, FileChoosers.Platform.of("Linux"));
        assertEquals(FileChoosers.Platform.OTHER, FileChoosers.Platform.of(null));

        assertEquals(Path.of("/saves", "void"), FileChoosers.resolve("/saves", "void").orElseThrow());
        assertTrue(FileChoosers.resolve("/saves", null).isEmpty());
        assertTrue(FileChoosers.resolve("/saves", "").isEmpty());
        assertTrue(FileChoosers.resolve("/saves", "bad\0name").isEmpty());

        assertEquals(Path.of("/saves", "void"), FileChoosers.worldDirectory(Path.of("/saves", "void", "level.dat")));
        assertEquals(Path.of("/saves", "void"), FileChoosers.worldDirectory(Path.of("/saves", "void")));
        assertEquals(Path.of("/saves", "void", "level.dat_old"),
                FileChoosers.worldDirectory(Path.of("/saves", "void", "level.dat_old")));

        assertTrue(FileChoosers.extensionFilter("mcpaint").accept(null, "World.MCPAINT"));
        assertFalse(FileChoosers.extensionFilter("mcpaint").accept(null, "world.mcpaint.png"));
        assertTrue(FileChoosers.extensionFilter("png").accept(null, "height.png"));
    }

    @Test
    void confirmsOverwriteOnlyWhenTheNativeDialogCouldNotAsk() {
        Path raw = Path.of("dir", "world");
        Path target = FileChoosers.withProjectExtension(raw);
        assertTrue(FileChoosers.needsOverwriteConfirmation(raw, target, true, false));
        assertFalse(FileChoosers.needsOverwriteConfirmation(raw, target, false, false));
        assertFalse(FileChoosers.needsOverwriteConfirmation(raw, target, true, true));
        Path typed = Path.of("dir", "world.mcpaint");
        assertFalse(FileChoosers.needsOverwriteConfirmation(typed, FileChoosers.withProjectExtension(typed), true,
                false));
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
    void formsReadTileAndBlockSizes() {
        Messages messages = Messages.load(Language.KOREAN);
        Forms.NewWorldForm form = new Forms.NewWorldForm(messages);
        form.seed().setText("42");
        assertEquals(new Forms.NewWorld(new TileRect(-2, -2, 1, 1), 64, 63, TerrainType.GRASS,
                new GenerationSettings(42)), form.read().orElseThrow());
        Forms.NewWorld world = form.read().orElseThrow();
        assertEquals(BaseTerrain.flat(64, TerrainType.GRASS), world.spec().base());
        assertEquals(new TileRect(-2, -2, 1, 1), world.spec().bounds());
        form.initialOres().setSelected(true);
        assertEquals(new GenerationSettings(42, true), form.read().orElseThrow().generation());
        form.initialOres().setSelected(false);
        form.seed().setText("mc-painter");
        assertEquals("mc-painter".hashCode(), form.read().orElseThrow().generation().seed());
        form.widthSpinner().setValue(Forms.MAX_SIDE_TILES);
        form.depthSpinner().setValue(Forms.MAX_SIDE_TILES);
        assertEquals(TileRect.WORLD, form.read().orElseThrow().bounds());
        form.useBlocks(true);
        form.widthSpinner().setValue(80_000);
        form.depthSpinner().setValue(129);
        assertEquals(new TileRect(-312, -1, 312, 0), form.read().orElseThrow().bounds());
        form.widthSpinner().setValue(Forms.MAX_SIDE_BLOCKS);
        assertEquals(TileRect.WORLD.width(), form.read().orElseThrow().bounds().width());
        Forms.ImportForm importForm = new Forms.ImportForm(messages);
        importForm.seed().setText(" -7 ");
        Forms.Import options = importForm.read();
        assertEquals(HeightmapImporter.Options.defaults(), options.options());
        assertEquals(-7, options.generation().seed());
        assertFalse(options.generation().initialOres());
        WorldModel ores = Worlds.blank(2, 1, 64, 63, TerrainType.GRASS, new GenerationSettings(1, true));
        assertEquals(Layer.INITIAL_ORE_VALUE, ores.layer(-100, 5, Layer.COAL_ORE));
        assertEquals(Layer.INITIAL_ORE_VALUE, ores.layer(100, 5, Layer.IRON_ORE));
        assertEquals(0, ores.layer(100, 5, Layer.OAK_TREES));
        assertEquals(0, Worlds.blank(1, 1, 64, 63, TerrainType.GRASS).layer(5, 5, Layer.COAL_ORE));
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
