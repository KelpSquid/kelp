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
 *
 * Languages use Minecraft's own codes (es_es, ja_jp...), listed with their names in lang/languages.txt. Every one but
 * English is marked beta: they were written by an AI and haven't been checked by native speakers yet.
 */
public final class Lang {
    /** A language Kelp can show, by its Minecraft code and its own name for itself. */
    public record Language(String code, String name) {
        /** Whether it's still being checked (every language but English). */
        public boolean beta() {
            return !code.equals(ENGLISH);
        }
    }

    public static final String ENGLISH = "en_us";
    /** Every language, English first, then the rest by name. */
    public static final List<Language> ALL = languages();

    private static Map<String, String> table = Map.of();
    private static String loaded;

    private Lang() {
    }

    /**
     * The text in the chosen language. {0}, {1}... are filled in with the extra values, so a sentence with a
     * name in it is translated as one sentence: t("Added {0}!", name).
     */
    public static String t(String english, Object... values) {
        String code = current().code();
        if (!code.equals(loaded)) load(code);
        String text = table.getOrDefault(english, english);
        for (int i = 0; i < values.length; i++) text = text.replace("{" + i + "}", String.valueOf(values[i]));
        return text;
    }

    public static Language current() {
        String code = upgrade(Settings.language());
        for (Language language : ALL) {
            if (language.code().equals(code)) return language;
        }
        return ALL.get(0);
    }

    /** Older Kelps saved short codes like "es"; they're Minecraft's codes now. */
    static String upgrade(String code) {
        return switch (code) {
            case "en" -> ENGLISH;
            case "es" -> "es_es";
            case "fr" -> "fr_fr";
            case "de" -> "de_de";
            case "sv" -> "sv_se";
            case "is" -> "is_is";
            case "ru" -> "ru_ru";
            default -> code;
        };
    }

    /** lang/languages.txt: one language per line, "code|name". */
    private static List<Language> languages() {
        List<Language> all = new java.util.ArrayList<>();
        all.add(new Language(ENGLISH, "English"));
        try {
            String index = text("lang/languages.txt");
            if (index != null) {
                for (String line : index.split("\\R")) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2 && !line.startsWith("#") && !parts[0].equals(ENGLISH)) all.add(new Language(parts[0], parts[1]));
                }
            }
        } catch (IOException e) {
            System.err.println("Couldn't read the list of languages: " + e.getMessage());
        }
        return List.copyOf(all);
    }

    private static synchronized void load(String code) {
        Map<String, String> read = new HashMap<>();
        if (!code.equals(ENGLISH)) {
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
