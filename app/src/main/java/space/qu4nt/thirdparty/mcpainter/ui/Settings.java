package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

final class Settings {

    static final String NODE = "space/qu4nt/thirdparty/mcpainter";
    static final String LANGUAGE = "language";
    static final String CHUNK_GRID = "chunkGridOpacity";
    static final String TILE_GRID = "tileGridOpacity";
    static final String PREVIEW = "gamePreview";
    static final int DEFAULT_CHUNK_GRID = 16;
    static final int DEFAULT_TILE_GRID = 43;

    private final Preferences preferences;

    Settings(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    static Settings user() {
        return new Settings(Preferences.userRoot().node(NODE));
    }

    Language language() {
        String code = read(LANGUAGE);
        return code == null ? Language.DEFAULT : Language.fromCode(code).orElse(Language.DEFAULT);
    }

    void setLanguage(Language language) throws BackingStoreException {
        preferences.put(LANGUAGE, language.code());
        preferences.flush();
    }

    int chunkGridOpacity() {
        return percent(CHUNK_GRID, DEFAULT_CHUNK_GRID);
    }

    int tileGridOpacity() {
        return percent(TILE_GRID, DEFAULT_TILE_GRID);
    }

    void setGridOpacity(int chunk, int tile) throws BackingStoreException {
        preferences.putInt(CHUNK_GRID, requirePercent(chunk));
        preferences.putInt(TILE_GRID, requirePercent(tile));
        preferences.flush();
    }

    boolean preview() {
        return "true".equals(read(PREVIEW));
    }

    void setPreview(boolean value) throws BackingStoreException {
        preferences.put(PREVIEW, Boolean.toString(value));
        preferences.flush();
    }

    private String read(String key) {
        try {
            return preferences.get(key, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private int percent(String key, int fallback) {
        String text = read(key);
        if (text == null || text.length() > 3) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(text);
            return value >= 0 && value <= 100 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int requirePercent(int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("opacity must be 0 to 100");
        }
        return value;
    }
}
