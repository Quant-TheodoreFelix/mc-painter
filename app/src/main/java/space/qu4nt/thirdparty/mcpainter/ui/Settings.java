package space.qu4nt.thirdparty.mcpainter.ui;

import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

final class Settings {

    static final String NODE = "space/qu4nt/thirdparty/mcpainter";
    static final String LANGUAGE = "language";

    private final Preferences preferences;

    Settings(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    static Settings user() {
        return new Settings(Preferences.userRoot().node(NODE));
    }

    Language language() {
        String code;
        try {
            code = preferences.get(LANGUAGE, null);
        } catch (RuntimeException e) {
            return Language.DEFAULT;
        }
        return code == null ? Language.DEFAULT : Language.fromCode(code).orElse(Language.DEFAULT);
    }

    void setLanguage(Language language) throws BackingStoreException {
        preferences.put(LANGUAGE, language.code());
        preferences.flush();
    }
}
