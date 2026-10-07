package kelp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An instance's Minecraft settings, from the options.txt in its folder: one "name:value" per line.
 * Kelp only changes the lines it knows about and keeps every other line exactly as it was.
 */
public final class GameOptions {
    /**
     * The settings Minecraft's graphics presets (Fast, Fancy, Fabulous) change. Minecraft applies the chosen preset
     * every time it starts, so changing one of these by hand has to switch the preset to Custom, like the game does.
     */
    static final Set<String> PRESET_SETTINGS = Set.of("renderDistance", "simulationDistance", "biomeBlendRadius",
            "prioritizeChunkUpdates", "ao", "renderClouds", "particles", "mipmapLevels", "entityShadows",
            "entityDistanceScaling", "menuBackgroundBlurriness", "cloudRange", "cutoutLeaves", "improvedTransparency",
            "weatherRadius", "maxAnisotropyBit", "textureFiltering");

    private final Path file;
    private final List<String> lines;
    private final String newline; // keep whatever line endings the file already has

    private GameOptions(Path file, List<String> lines, String newline) {
        this.file = file;
        this.lines = lines;
        this.newline = newline;
    }

    /**
     * Reads an instance's settings. If the game hasn't made the file yet, Kelp starts a new one,
     * marked with the game's version number so Minecraft knows what format it's in.
     */
    public static GameOptions load(Instance instance) throws IOException {
        Path file = instance.folder().resolve("options.txt");
        if (Files.exists(file)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return new GameOptions(file, new ArrayList<>(text.lines().toList()), text.contains("\r\n") ? "\r\n" : "\n");
        }

        String id = instance.version().id();
        Path jar = Folders.versions().resolve(id).resolve(id + ".jar");
        if (!Files.exists(jar)) throw new IOException("Play this instance once first, then its game options can be changed here.");
        List<String> lines = new ArrayList<>();
        lines.add("version:" + dataVersion(jar));
        return new GameOptions(file, lines, "\n");
    }

    /** The settings format number Minecraft keeps in version.json inside the game's jar. */
    private static int dataVersion(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("version.json");
            if (entry == null) throw new IOException("This version is too old for Kelp to change its game options.");
            Map<String, Object> json = Json.object(Json.parse(
                    new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)));
            return ((Number) json.get("world_version")).intValue();
        }
    }

    /** The setting's value as written in the file, or the fallback if it isn't there. */
    public String get(String key, String fallback) {
        for (String line : lines) {
            if (line.startsWith(key + ":")) return line.substring(key.length() + 1);
        }
        return fallback;
    }

    public double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    /** Changes a setting and saves the file right away. A graphics setting switches the graphics preset to Custom. */
    public void set(String key, String value) throws IOException {
        if (PRESET_SETTINGS.contains(key)) put("graphicsPreset", "\"custom\"");
        put(key, value);
        save();
    }

    /**
     * Minecraft's own Fast graphics preset, which the game applies when it starts. Render and simulation distance
     * are written now too (8 and 6, what Fast uses), so Kelp's sliders show what the game will use.
     */
    public void useFastPreset() throws IOException {
        put("graphicsPreset", "\"fast\"");
        put("renderDistance", "8");
        put("simulationDistance", "6");
        save();
    }

    private void put(String key, String value) {
        String line = key + ":" + value;
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + ":")) {
                lines.set(i, line);
                found = true;
            }
        }
        if (!found) lines.add(line);
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join(newline, lines) + newline, StandardCharsets.UTF_8);
    }
}
