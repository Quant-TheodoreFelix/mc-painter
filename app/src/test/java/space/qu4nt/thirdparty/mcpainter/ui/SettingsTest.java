package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.BackingStoreException;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void gridOpacityDefaultsPersistsAndRejectsGarbage() throws BackingStoreException {
        MemoryPreferences preferences = new MemoryPreferences();
        Settings settings = new Settings(preferences);
        assertEquals(Settings.DEFAULT_CHUNK_GRID, settings.chunkGridOpacity());
        assertEquals(Settings.DEFAULT_TILE_GRID, settings.tileGridOpacity());
        settings.setGridOpacity(0, 100);
        assertEquals(0, settings.chunkGridOpacity());
        assertEquals(100, settings.tileGridOpacity());
        assertThrows(IllegalArgumentException.class, () -> settings.setGridOpacity(-1, 50));
        assertThrows(IllegalArgumentException.class, () -> settings.setGridOpacity(50, 101));
        for (String bad : new String[] {"101", "-5", "abc", "1e2", "0x10", "99999999999"}) {
            preferences.put(Settings.CHUNK_GRID, bad);
            assertEquals(Settings.DEFAULT_CHUNK_GRID, settings.chunkGridOpacity(), bad);
        }
    }

    @Test
    void previewFlagIsOffUnlessStoredAsTrue() throws BackingStoreException {
        MemoryPreferences preferences = new MemoryPreferences();
        Settings settings = new Settings(preferences);
        assertFalse(settings.preview());
        settings.setPreview(true);
        assertTrue(settings.preview());
        preferences.put(Settings.PREVIEW, "yes");
        assertFalse(settings.preview());
    }

    @Test
    void gridColorsUseTheOpacityAsAlpha() {
        assertEquals(0, MapView.gridColor(0).getAlpha());
        assertEquals(41, MapView.gridColor(Settings.DEFAULT_CHUNK_GRID).getAlpha());
        assertEquals(110, MapView.gridColor(Settings.DEFAULT_TILE_GRID).getAlpha());
        assertEquals(255, MapView.gridColor(100).getAlpha());
        assertEquals(255, MapView.gridColor(500).getAlpha());
    }
}
