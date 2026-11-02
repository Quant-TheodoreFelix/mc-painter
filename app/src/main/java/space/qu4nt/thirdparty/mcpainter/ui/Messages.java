package space.qu4nt.thirdparty.mcpainter.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

final class Messages {

    private final Language language;
    private final Map<String, String> patterns;

    private Messages(Language language, Map<String, String> patterns) {
        this.language = language;
        this.patterns = patterns;
    }

    static String resourceName(Language language) {
        return "messages_" + language.code() + ".properties";
    }

    static Messages load(Language language) {
        Objects.requireNonNull(language, "language");
        String name = resourceName(language);
        try (InputStream in = Messages.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("missing message bundle " + name);
            }
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT));
            Properties properties = new Properties();
            properties.load(reader);
            Map<String, String> patterns = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                patterns.put(key, properties.getProperty(key));
            }
            return new Messages(language, Map.copyOf(patterns));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read message bundle " + name, e);
        }
    }

    Language language() {
        return language;
    }

    Set<String> keys() {
        return patterns.keySet();
    }

    boolean has(String key) {
        return patterns.containsKey(key);
    }

    String text(String key, Object... args) {
        String pattern = patterns.get(key);
        if (pattern == null) {
            return key;
        }
        return new MessageFormat(pattern, language.locale()).format(args);
    }
}
