package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;

class SafeTextTest {

    private static final String HOSTILE = "<html><img src=\"http://127.0.0.1:9/x.png\">";

    @Test
    void neutralizesHtmlAndDirectionalControls() {
        assertFalse(BasicHTML.isHTMLString(SafeText.neutralize(HOSTILE)));
        assertFalse(BasicHTML.isHTMLString(SafeText.neutralize("<HTML>x")));
        assertEquals("a�b\nc\td", SafeText.neutralize("a‮b\nc\td"));
        assertEquals("x�y", SafeText.neutralize("x\u0000y"));
        assertEquals("", SafeText.neutralize(null));
        assertEquals("plain", SafeText.neutralize("plain"));
    }

    @Test
    void labelsAndMessagesNeverBuildHtmlViews() {
        JLabel label = SafeText.label(HOSTILE);
        assertEquals(Boolean.TRUE, label.getClientProperty(SafeText.HTML_DISABLE));
        assertNull(label.getClientProperty(BasicHTML.propertyKey));
        SafeText.setText(label, HOSTILE);
        assertNull(label.getClientProperty(BasicHTML.propertyKey));
        JTextArea area = SafeText.message(HOSTILE);
        assertFalse(area.isEditable());
        assertEquals(SafeText.neutralize(HOSTILE), area.getText());
    }

    @Test
    void describesCauseChainsWithoutRepeats() {
        IOException root = new IOException("disk full");
        IllegalStateException top = new IllegalStateException("save failed", root);
        assertEquals("save failed\ndisk full", Dialogs.describe(top));
        assertEquals("NullPointerException", Dialogs.describe(new NullPointerException()));
        assertEquals("same", Dialogs.describe(new IOException("same", new IOException("same"))));
    }

    @Test
    void storedLanguageFallsBackToKorean() throws BackingStoreException {
        MemoryPreferences preferences = new MemoryPreferences();
        Settings settings = new Settings(preferences);
        assertEquals(Language.KOREAN, settings.language());
        settings.setLanguage(Language.ENGLISH);
        assertEquals(Language.ENGLISH, settings.language());
        preferences.put(Settings.LANGUAGE, "<html>");
        assertEquals(Language.KOREAN, settings.language());
    }

    private static final class MemoryPreferences extends AbstractPreferences {

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
}
