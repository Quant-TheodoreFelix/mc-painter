package space.qu4nt.thirdparty.mcpainter.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    public static final int MAX_CHARS = 1 << 20;
    public static final int MAX_DEPTH = 32;
    private static final int MAX_DIGITS = 19;

    private final String text;
    private final int maxDepth;
    private int pos;

    private Json(String text, int maxDepth) {
        this.text = text;
        this.maxDepth = maxDepth;
    }

    public static Object parse(String text) throws JsonFormatException {
        return parse(text, MAX_CHARS, MAX_DEPTH);
    }

    public static Object parse(String text, int maxChars, int maxDepth) throws JsonFormatException {
        if (text == null) {
            throw new JsonFormatException("no JSON text");
        }
        if (text.length() > maxChars) {
            throw new JsonFormatException("JSON text exceeds " + maxChars + " characters");
        }
        Json parser = new Json(text, maxDepth);
        parser.skipWhitespace();
        Object value = parser.value(0);
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    private Object value(int depth) throws JsonFormatException {
        if (pos >= text.length()) {
            throw error("unexpected end of JSON");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object(depth + 1);
            case '[' -> array(depth + 1);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }
                throw error("unexpected character");
            }
        };
    }

    private Map<String, Object> object(int depth) throws JsonFormatException {
        requireDepth(depth);
        pos++;
        Map<String, Object> members = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return Collections.unmodifiableMap(members);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected a member name");
            }
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = value(depth);
            if (members.putIfAbsent(key, value) != null) {
                throw error("duplicate member " + key);
            }
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return Collections.unmodifiableMap(members);
            }
            if (c != ',') {
                throw error("expected , or }");
            }
        }
    }

    private List<Object> array(int depth) throws JsonFormatException {
        requireDepth(depth);
        pos++;
        List<Object> items = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return Collections.unmodifiableList(items);
        }
        while (true) {
            skipWhitespace();
            items.add(value(depth));
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return Collections.unmodifiableList(items);
            }
            if (c != ',') {
                throw error("expected , or ]");
            }
        }
    }

    private String string() throws JsonFormatException {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw error("control character in string");
            }
            if (Character.isSurrogate(c)) {
                if (!Character.isHighSurrogate(c) || pos >= text.length()
                        || !Character.isLowSurrogate(text.charAt(pos))) {
                    throw error("unpaired surrogate");
                }
                out.append(c).append(text.charAt(pos++));
                continue;
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    char unit = hexUnit();
                    if (Character.isHighSurrogate(unit)) {
                        if (pos + 1 >= text.length() || text.charAt(pos) != '\\' || text.charAt(pos + 1) != 'u') {
                            throw error("unpaired surrogate");
                        }
                        pos += 2;
                        char low = hexUnit();
                        if (!Character.isLowSurrogate(low)) {
                            throw error("unpaired surrogate");
                        }
                        out.append(unit).append(low);
                    } else if (Character.isLowSurrogate(unit)) {
                        throw error("unpaired surrogate");
                    } else {
                        out.append(unit);
                    }
                }
                default -> throw error("invalid escape");
            }
        }
    }

    private char hexUnit() throws JsonFormatException {
        if (pos + 4 > text.length()) {
            throw error("truncated unicode escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(pos++), 16);
            if (digit < 0) {
                throw error("invalid unicode escape");
            }
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private Long number() throws JsonFormatException {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        int digitsStart = pos;
        while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
            pos++;
        }
        int digits = pos - digitsStart;
        if (digits == 0) {
            throw error("expected digits");
        }
        if (digits > 1 && text.charAt(digitsStart) == '0') {
            throw error("leading zero");
        }
        if (pos < text.length() && (text.charAt(pos) == '.' || text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            throw error("only integers are supported");
        }
        if (digits > MAX_DIGITS) {
            throw error("number too large");
        }
        try {
            return Long.parseLong(text, start, pos, 10);
        } catch (NumberFormatException e) {
            throw error("number too large");
        }
    }

    private Boolean literal(String word, Boolean value) throws JsonFormatException {
        if (!text.startsWith(word, pos)) {
            throw error("invalid literal");
        }
        pos += word.length();
        return value;
    }

    private void requireDepth(int depth) throws JsonFormatException {
        if (depth > maxDepth) {
            throw error("nesting deeper than " + maxDepth);
        }
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private char peek() throws JsonFormatException {
        if (pos >= text.length()) {
            throw error("unexpected end of JSON");
        }
        return text.charAt(pos);
    }

    private char next() throws JsonFormatException {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) throws JsonFormatException {
        if (next() != c) {
            throw error("expected " + c);
        }
    }

    private JsonFormatException error(String message) {
        return new JsonFormatException(message + " at offset " + pos);
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value, 0);
        return out.append('\n').toString();
    }

    private static void write(StringBuilder out, Object value, int indent) {
        switch (value) {
            case String s -> quote(out, s);
            case Boolean b -> out.append(b);
            case Long n -> out.append(n.longValue());
            case Integer n -> out.append(n.intValue());
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException("JSON member names must be strings");
                    }
                    out.repeat("  ", indent + 1);
                    quote(out, key);
                    out.append(": ");
                    write(out, entry.getValue(), indent + 1);
                    out.append(++i < map.size() ? ",\n" : "\n");
                }
                out.repeat("  ", indent).append('}');
            }
            case List<?> list -> {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    write(out, list.get(i), indent + 1);
                }
                out.append(']');
            }
            case null -> throw new IllegalArgumentException("JSON null is not supported");
            default -> throw new IllegalArgumentException("unsupported JSON value " + value.getClass().getName());
        }
    }

    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Character.isSurrogate(c) && !validPair(s, i)) {
                        throw new IllegalArgumentException("unpaired surrogate in JSON string");
                    }
                    if (c < 0x20) {
                        out.append("\\u").append(HexFormat.of().toHexDigits((short) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static boolean validPair(String s, int i) {
        char c = s.charAt(i);
        if (Character.isHighSurrogate(c)) {
            return i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1));
        }
        return i > 0 && Character.isHighSurrogate(s.charAt(i - 1));
    }
}
