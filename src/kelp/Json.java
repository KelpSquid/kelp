package kelp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A tiny JSON reader. Objects become Maps, arrays become Lists, and numbers become Doubles. */
public final class Json {
    /** Deeper than this is never a real file, and would run Java out of room to keep reading it. */
    private static final int MAX_DEPTH = 512;

    private final String text;
    private int pos;
    private int depth;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.skipSpace();
        if (json.pos != text.length()) throw json.error("extra text after the end");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        return (List<Object>) value;
    }

    private Object value() {
        skipSpace();
        return switch (peek()) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> word("true", Boolean.TRUE);
            case 'f' -> word("false", Boolean.FALSE);
            case 'n' -> word("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // skip the {
        if (++depth > MAX_DEPTH) throw error("nested too deep");
        skipSpace();
        if (peek() == '}') {
            pos++;
            depth--;
            return map;
        }
        while (true) {
            skipSpace();
            String key = readString();
            skipSpace();
            if (next() != ':') throw error("expected :");
            map.put(key, value());
            skipSpace();
            char c = next();
            if (c == '}') {
                depth--;
                return map;
            }
            if (c != ',') throw error("expected , or }");
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // skip the [
        if (++depth > MAX_DEPTH) throw error("nested too deep");
        skipSpace();
        if (peek() == ']') {
            pos++;
            depth--;
            return list;
        }
        while (true) {
            list.add(value());
            skipSpace();
            char c = next();
            if (c == ']') {
                depth--;
                return list;
            }
            if (c != ',') throw error("expected , or ]");
        }
    }

    private String readString() {
        if (next() != '"') throw error("expected a string");
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char escaped = next();
            switch (escaped) {
                case '"', '\\', '/' -> sb.append(escaped);
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw error("unexpected end");
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException notHex) {
                        throw error("bad \\u escape");
                    }
                    pos += 4;
                }
                default -> throw error("unknown escape \\" + escaped);
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        if (start == pos) throw error("unexpected character");
        try {
            return Double.parseDouble(text.substring(start, pos));
        } catch (NumberFormatException notANumber) {
            throw error("bad number");
        }
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, pos)) throw error("expected " + word);
        pos += word.length();
        return value;
    }

    private char peek() {
        if (pos >= text.length()) throw error("unexpected end");
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Bad JSON at character " + pos + ": " + message);
    }
}
