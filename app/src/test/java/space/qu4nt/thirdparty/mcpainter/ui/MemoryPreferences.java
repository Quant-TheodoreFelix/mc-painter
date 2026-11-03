package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

final class MemoryPreferences extends AbstractPreferences {

    private final Map<String, String> values = new HashMap<>();

    MemoryPreferences() {
        super(null, "");
    }

    @Override
    protected void putSpi(String key, String value) {
        values.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
        return values.get(key);
    }

    @Override
    protected void removeSpi(String key) {
        values.remove(key);
    }

    @Override
    protected void removeNodeSpi() {
        values.clear();
    }

    @Override
    protected String[] keysSpi() {
        return values.keySet().toArray(String[]::new);
    }

    @Override
    protected String[] childrenNamesSpi() {
        return new String[0];
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void syncSpi() {
    }

    @Override
    protected void flushSpi() {
    }
}
