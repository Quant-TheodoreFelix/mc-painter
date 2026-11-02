package space.qu4nt.thirdparty.mcpainter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesTheSupportedSubset() throws JsonFormatException {
        Object value = Json.parse(" {\"b\": [1, -2, 0], \"a\": {\"t\": true, \"f\": false}, \"s\": \"x\\u00e9\\ud83d\\ude00\\n\\\"\"} ");
        Map<?, ?> map = (Map<?, ?>) value;
        assertEquals(List.of("b", "a", "s"), List.copyOf(map.keySet()));
        assertEquals(List.of(1L, -2L, 0L), map.get("b"));
        assertEquals(Map.of("t", true, "f", false), map.get("a"));
        assertEquals("x\u00e9\uD83D\uDE00\n\"", map.get("s"));
        assertEquals(Map.of(), Json.parse("{}"));
        assertEquals(List.of(), Json.parse("[ ]"));
        assertEquals(Long.MAX_VALUE, Json.parse("9223372036854775807"));
        assertEquals(Long.MIN_VALUE, Json.parse("-9223372036854775808"));
        assertEquals("한글 \uD83D\uDE00", Json.parse("\"한글 \uD83D\uDE00\""));
    }

    @Test
    void writesWhatItParses() throws JsonFormatException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "a\"b\\c\n\u0001\uD83D\uDE00한글");
        value.put("n", 42);
        value.put("list", List.of(1L, true, "x", Map.of()));
        value.put("nested", Map.of("k", List.of()));
        String text = Json.write(value);
        assertTrue(text.contains("\\u0001"));
        Map<?, ?> back = (Map<?, ?>) Json.parse(text);
        assertEquals(value.get("name"), back.get("name"));
        assertEquals(42L, back.get("n"));
        assertEquals(List.of(1L, true, "x", Map.of()), back.get("list"));
        assertThrows(IllegalArgumentException.class, () -> Json.write(nullHolder()));
        assertThrows(IllegalArgumentException.class, () -> Json.write("\uD800"));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> Json.write(1.5));
    }

    @Test
    void rejectsHostileInput() {
        String[] hostile = {
            "", " ", "\uFEFF{}", "{} x", "{}{}", "[1,]", "{\"a\":1,}", "{\"a\" 1}", "{\"a\":1 \"b\":2}", "{a:1}",
            "{\"a\":1,\"a\":2}", "\"tab\there\"", "\"nl\n\"", "\"\\ud800\"", "\"\\udc00\"", "\"\\ud800\\u0041\"",
            "\"\uD800\"", "\"\uDC00x\"", "\"\\x\"", "\"\\u12\"", "\"\\u12g4\"", "\"open", "[1", "{\"a\":",
            "01", "-", "-01", "1.5", "1e3", "1E3", "9223372036854775808", "-9223372036854775809",
            "12345678901234567890", "tru", "nul", "null", "True", "NaN", "Infinity", "'a'", "[1 2]", "+1"
        };
        for (String text : hostile) {
            assertThrows(JsonFormatException.class, () -> Json.parse(text), () -> "accepted: " + text);
        }
        assertThrows(JsonFormatException.class, () -> Json.parse(null));
    }

    @Test
    void enforcesDepthAndSizeLimits() throws JsonFormatException {
        String deep = "[".repeat(Json.MAX_DEPTH + 1) + "]".repeat(Json.MAX_DEPTH + 1);
        assertThrows(JsonFormatException.class, () -> Json.parse(deep));
        String ok = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH);
        assertTrue(Json.parse(ok) instanceof List);
        String objects = "{\"a\":".repeat(Json.MAX_DEPTH + 1) + "1" + "}".repeat(Json.MAX_DEPTH + 1);
        assertThrows(JsonFormatException.class, () -> Json.parse(objects));
        assertThrows(JsonFormatException.class, () -> Json.parse("\"" + "x".repeat(Json.MAX_CHARS) + "\""));
        assertThrows(JsonFormatException.class, () -> Json.parse("[1]", 2, Json.MAX_DEPTH));
        String huge = "[" + "1,".repeat(100_000) + "1]";
        assertEquals(100_001, ((List<?>) Json.parse(huge)).size());
    }

    private static Map<String, Object> nullHolder() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", null);
        return map;
    }
}
