package space.qu4nt.thirdparty.mcpainter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import space.qu4nt.thirdparty.mcpainter.world.Brush;
import space.qu4nt.thirdparty.mcpainter.world.TerrainType;

class MessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][A-Za-z0-9]*(?:\\.[A-Za-z0-9]+)+)\"");

    @Test
    void everyLanguageHasTheSameKeysAndArguments() {
        Messages reference = Messages.load(Language.DEFAULT);
        for (Language language : Language.values()) {
            Messages messages = Messages.load(language);
            assertEquals(new TreeSet<>(reference.keys()), new TreeSet<>(messages.keys()), language.code());
            for (String key : reference.keys()) {
                assertEquals(argumentCount(reference, key), argumentCount(messages, key), language.code() + " " + key);
            }
        }
    }

    @Test
    void bundlesHaveNoDuplicateKeysOrQuotes() throws IOException {
        for (Language language : Language.values()) {
            Set<String> seen = new HashSet<>();
            for (String line : lines(language)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                assertTrue(separator > 0, line);
                assertTrue(seen.add(line.substring(0, separator)), "duplicate " + line);
                assertFalse(line.contains("'"), "MessageFormat quote in " + line);
            }
        }
    }

    @Test
    void dynamicKeysExist() {
        for (Language language : Language.values()) {
            Messages messages = Messages.load(language);
            for (Language other : Language.values()) {
                assertTrue(messages.has(other.messageKey()));
            }
            for (Tool tool : Tool.values()) {
                assertTrue(messages.has(tool.messageKey()), tool.name());
            }
            for (TerrainType type : TerrainType.values()) {
                assertTrue(messages.has(Labels.terrainKey(type)), type.id());
            }
            for (Brush.Falloff falloff : Brush.Falloff.values()) {
                assertTrue(messages.has(Labels.falloffKey(falloff)), falloff.name());
            }
        }
    }

    @Test
    void everyKeyLiteralInSourcesExists() throws IOException {
        Messages messages = Messages.load(Language.DEFAULT);
        Set<String> prefixes = new HashSet<>();
        for (String key : messages.keys()) {
            prefixes.add(key.substring(0, key.indexOf('.')));
        }
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher matcher = LITERAL.matcher(Files.readString(file));
                while (matcher.find()) {
                    String literal = matcher.group(1);
                    String prefix = literal.substring(0, literal.indexOf('.'));
                    if (prefixes.contains(prefix) && !messages.has(literal)) {
                        missing.add(file.getFileName() + " " + literal);
                    }
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void formatsArgumentsAndFallsBackToKey() {
        Messages ko = Messages.load(Language.KOREAN);
        Messages en = Messages.load(Language.ENGLISH);
        assertEquals("올리기 (1)", ko.text("tool.shortcut", ko.text("tool.raise"), "1"));
        assertEquals("Undo Raise", en.text("menu.edit.undoNamed", en.text("tool.raise")));
        assertEquals("no.such.key", ko.text("no.such.key"));
        assertEquals(Locale.KOREAN, ko.language().locale());
    }

    private static int argumentCount(Messages messages, String key) {
        return new MessageFormat(rawPattern(messages, key)).getFormatsByArgumentIndex().length;
    }

    private static String rawPattern(Messages messages, String key) {
        Object[] markers = new Object[16];
        for (int i = 0; i < markers.length; i++) {
            markers[i] = "{" + i + "}";
        }
        return messages.text(key, markers);
    }

    private static List<String> lines(Language language) throws IOException {
        try (InputStream in = Messages.class.getResourceAsStream(Messages.resourceName(language))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
