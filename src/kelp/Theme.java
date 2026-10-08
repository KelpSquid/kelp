package kelp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * How Kelp looks: the scene behind every screen (ocean, lava, sky, nether or the End), its colors, the buttons' color,
 * and optionally a picture of your own as the background and music. Kelp comes with a few; more can be made in the Theme
 * Maker or got from the Store, and those live in Kelp's themes folder, one folder each with a theme.properties.
 *
 * @param water    the color the background tiles are tinted (they're gray), or -1 for no tiles (just the base color)
 * @param base     the color under everything
 * @param buttons  the buttons' color, as a hue (0-359), or -1 for the sea-glass teal they come in
 * @param picture  a picture to use as the whole background, or null
 * @param music    music to play over and over (.wav, .mp3, .flac, .ogg or .sqda), or null
 */
public record Theme(String id, String name, Scene scene, int water, int base, int buttons, Path picture, Path music) {
    /** What moves in the background. */
    public enum Scene {
        OCEAN("Ocean", true, Particles.BUBBLES), LAVA("Lava", false, Particles.EMBERS), SKY("Sky", false, Particles.CLOUDS),
        NETHER("Nether", false, Particles.ASH), END("End", false, Particles.STARS), PLAIN("Plain", false, Particles.NONE);

        public final String label;
        public final boolean kelp;
        public final Particles particles;

        Scene(String label, boolean kelp, Particles particles) {
            this.label = label;
            this.kelp = kelp;
            this.particles = particles;
        }
    }

    /** Bubbles and embers float up, ash falls, clouds drift sideways, stars twinkle in place. */
    public enum Particles { BUBBLES, EMBERS, ASH, CLOUDS, STARS, NONE }

    public static final Theme OCEAN = new Theme("ocean", "Ocean", Scene.OCEAN, 0x3F76E4, 0x0B1633, -1, null, null);
    public static final List<Theme> BUILT_IN = List.of(OCEAN,
            new Theme("lava", "Lava", Scene.LAVA, 0xD8541A, 0x2A0A02, 22, null, null),
            new Theme("sky", "Sky", Scene.SKY, -1, 0x78A7FF, 205, null, null),
            new Theme("nether", "Nether", Scene.NETHER, 0x6B1E1E, 0x1C0606, 355, null, null),
            new Theme("end", "End", Scene.END, -1, 0x0D0718, 275, null, null));

    private static volatile Theme current;

    /** The theme picked in Options (Ocean until another is picked). */
    public static Theme current() {
        Theme theme = current;
        if (theme == null) {
            theme = find(Settings.theme());
            current = theme;
        }
        return theme;
    }

    /** Uses a theme from now on, and remembers it. */
    public static void use(Theme theme) {
        current = theme;
        Settings.setTheme(theme.id());
    }

    /** Shows a theme without remembering it, like while it's being made in the Theme Maker. */
    public static void preview(Theme theme) {
        current = theme;
    }

    /** The theme with this id, or Ocean. */
    public static Theme find(String id) {
        for (Theme theme : all()) {
            if (theme.id().equals(id)) return theme;
        }
        return OCEAN;
    }

    /** Kelp's own themes, then the ones made or downloaded, by name. */
    public static List<Theme> all() {
        List<Theme> themes = new ArrayList<>(BUILT_IN);
        Path folder = folder();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> dirs = Files.list(folder)) {
                for (Path dir : dirs.filter(d -> Files.exists(d.resolve("theme.properties"))).sorted().toList()) {
                    Theme theme = read(dir);
                    if (theme != null) themes.add(theme);
                }
            } catch (IOException e) {
                System.err.println("Couldn't list themes: " + e.getMessage());
            }
        }
        return themes;
    }

    /** Kelp's themes folder. */
    public static Path folder() {
        return Folders.home().resolve("themes");
    }

    /** The name to show: Kelp's own themes in the chosen language, people's own themes as they named them. */
    public String label() {
        return builtIn() ? Lang.t(name) : name;
    }

    public boolean builtIn() {
        return BUILT_IN.stream().anyMatch(t -> t.id().equals(id));
    }

    /** Reads a theme folder's theme.properties. Null if it can't be read. */
    static Theme read(Path dir) {
        Properties values = new Properties();
        try (Reader in = Files.newBufferedReader(dir.resolve("theme.properties"), StandardCharsets.UTF_8)) {
            values.load(in);
        } catch (IOException e) {
            return null;
        }
        Scene scene;
        try {
            scene = Scene.valueOf(values.getProperty("scene", "OCEAN").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            scene = Scene.OCEAN;
        }
        Path picture = file(dir, values.getProperty("picture"));
        Path music = file(dir, values.getProperty("music"));
        return new Theme(dir.getFileName().toString(), values.getProperty("name", dir.getFileName().toString()), scene,
                color(values.getProperty("water"), OCEAN.water()), color(values.getProperty("base"), OCEAN.base()),
                number(values.getProperty("buttons"), -1), picture, music);
    }

    /** A file the theme brings (like its picture), only if it's inside the theme's own folder. */
    private static Path file(Path dir, String name) {
        if (name == null || name.isBlank()) return null;
        Path file = dir.resolve(name).normalize();
        return file.startsWith(dir.normalize()) && Files.exists(file) ? file : null;
    }

    /** Saves a theme into the themes folder as its own folder, copying its picture and music in. Gives back the saved theme. */
    public static Theme save(String name, Scene scene, int water, int base, int buttons, Path picture, Path music) throws IOException {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        // A name with no a-z or 0-9 in it (like one in Japanese) still gets its own folder, the same every time
        if (id.isEmpty()) id = "theme-" + Integer.toHexString(name.hashCode());
        return save(id, name, scene, water, base, buttons, picture, music);
    }

    /** Saves a theme in the folder with this id (so a theme made by Kelp itself, like a screenshot one, has its own). */
    public static Theme save(String id, String name, Scene scene, int water, int base, int buttons, Path picture, Path music) throws IOException {
        Path dir = folder().resolve(id);
        Files.createDirectories(dir);
        Properties values = new Properties();
        values.setProperty("name", name);
        values.setProperty("scene", scene.name().toLowerCase(Locale.ROOT));
        values.setProperty("water", water < 0 ? "none" : String.format("#%06X", water));
        values.setProperty("base", String.format("#%06X", base));
        values.setProperty("buttons", String.valueOf(buttons));
        if (picture != null) {
            String fileName = "background" + extension(picture);
            if (!picture.toAbsolutePath().normalize().equals(dir.resolve(fileName).toAbsolutePath().normalize())) {
                Files.copy(picture, dir.resolve(fileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            values.setProperty("picture", fileName);
        }
        if (music != null) {
            // Kept as the kind it is (music.mp3, music.sqda...): Kelp plays them all
            String musicName = "music" + musicExtension(music);
            if (!music.toAbsolutePath().normalize().equals(dir.resolve(musicName).toAbsolutePath().normalize())) {
                Files.copy(music, dir.resolve(musicName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            values.setProperty("music", musicName);
        }
        try (Writer out = Files.newBufferedWriter(dir.resolve("theme.properties"), StandardCharsets.UTF_8)) {
            values.store(out, "A Kelp theme");
        }
        return read(dir);
    }

    private static String musicExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String kind : ThemeMusic.KINDS) if (name.endsWith("." + kind)) return "." + kind;
        return ".wav";
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ? ".jpg" : ".png";
    }

    /** "#3F76E4" (or "none" for no tiles: -1). */
    static int color(String text, int fallback) {
        if (text == null) return fallback;
        if (text.equalsIgnoreCase("none")) return -1;
        try {
            return Integer.parseInt(text.replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int number(String text, int fallback) {
        try {
            return text == null ? fallback : Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
