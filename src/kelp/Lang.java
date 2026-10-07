package kelp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kelp in other languages. Every bit of text goes through {@link #t}, written in English. Each language is a
 * plain text file in lang/ with one line per text, like "Play => Jugar", so translating Kelp means filling in one
 * file. Anything a file doesn't have yet just shows in English.
 */
public final class Lang {
    /** A language Kelp can show, by its code and its own name for itself. */
    public record Language(String code, String name) {
    }

    public static final List<Language> ALL = List.of(
            new Language("en", "English"),
            new Language("es", "Español"),
            new Language("fr", "Français"),
            new Language("de", "Deutsch"),
            new Language("sv", "Svenska"),
            new Language("is", "Íslenska"),
            new Language("ru", "Русский"));

    private static Map<String, String> table = Map.of();
    private static String loaded;

    private Lang() {
    }

    /**
     * The text in the chosen language. {0}, {1}... are filled in with the extra values, so a sentence with a
     * name in it is translated as one sentence: t("Added {0}!", name).
     */
    public static String t(String english, Object... values) {
        String code = Settings.language();
        if (!code.equals(loaded)) load(code);
        String text = table.getOrDefault(english, english);
        for (int i = 0; i < values.length; i++) text = text.replace("{" + i + "}", String.valueOf(values[i]));
        return text;
    }

    public static Language current() {
        String code = Settings.language();
        for (Language language : ALL) {
            if (language.code().equals(code)) return language;
        }
        return ALL.get(0);
    }

    /** The language after this one, for the Language button. */
    public static Language next() {
        return ALL.get((ALL.indexOf(current()) + 1) % ALL.size());
    }

    private static synchronized void load(String code) {
        Map<String, String> read = new HashMap<>();
        if (!code.equals("en")) {
            try {
                String text = text("lang/" + code + ".txt");
                if (text != null) read = parse(text);
            } catch (IOException e) {
                System.err.println("Couldn't read the " + code + " language file: " + e.getMessage());
            }
        }
        table = read;
        loaded = code;
    }

    /** Reads a language file: "English => translation" on each line. Lines starting with # are notes. */
    static Map<String, String> parse(String text) {
        Map<String, String> map = new HashMap<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int arrow = line.indexOf(" => ");
            if (arrow < 0) continue;
            String english = line.substring(0, arrow).strip();
            String translated = line.substring(arrow + 4).strip();
            if (!translated.isEmpty()) map.put(english, translated);
        }
        return map;
    }

    /** A text file that comes with Kelp: inside kelp.jar when packaged, or in the project folder from source. */
    private static String text(String path) throws IOException {
        try (InputStream in = Lang.class.getResourceAsStream("/" + path)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path file = Path.of(path);
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
    }

    /** Forgets the loaded language, so the next text reads the file again. Only tests need this. */
    static synchronized void reload() {
        loaded = null;
    }
}
