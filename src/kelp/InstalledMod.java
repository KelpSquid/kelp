package kelp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A mod in a version's mods folder, described by the same squid.json Squid reads,
 * or a mod written as one .java file, which Squid compiles by itself.
 * Turned-off mods end in ".disabled", which Squid skips.
 *
 * @param squidMod  false if the jar has no squid.json (so Squid will skip it anyway)
 * @param minecraft the Minecraft versions the mod says it works on, like "26.3.x". Empty means any.
 */
public record InstalledMod(Path file, boolean enabled, boolean squidMod, String name, String version,
                           List<String> authors, String description, List<String> minecraft) {
    private static final String OFF = ".disabled";

    /** Whether it's a mod written as a .java file, which can be opened and changed. */
    public boolean source() {
        return file.getFileName().toString().contains(".java");
    }

    /** Whether the mod says it works on this Minecraft version, the same way Squid checks. "26.3.x" means 26.3 and its updates. */
    public boolean worksOn(String minecraftVersion) {
        if (minecraft.isEmpty()) return true;
        for (String wanted : minecraft) {
            if (wanted.equals(minecraftVersion)) return true;
            if (wanted.endsWith(".x")) {
                String series = wanted.substring(0, wanted.length() - 2);
                if (minecraftVersion.equals(series) || minecraftVersion.startsWith(series + ".")) return true;
            }
        }
        return false;
    }

    /** Every mod in the folder, sorted by name. */
    public static List<InstalledMod> list(Path folder) {
        List<InstalledMod> mods = new ArrayList<>();
        if (!Files.isDirectory(folder)) return mods;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".jar")) mods.add(read(file, true));
                else if (fileName.endsWith(".jar" + OFF)) mods.add(read(file, false));
                else if (fileName.endsWith(".java")) mods.add(readSource(file, true));
                else if (fileName.endsWith(".java" + OFF)) mods.add(readSource(file, false));
            }
        } catch (IOException e) {
            System.err.println("Couldn't list " + folder + ": " + e.getMessage());
        }
        mods.sort(Comparator.comparing(mod -> mod.name().toLowerCase()));
        return mods;
    }

    /** Turns the mod on or off by renaming its file. Gives back the mod as it is now. */
    public InstalledMod toggle() throws IOException {
        String fileName = file.getFileName().toString();
        Path renamed = file.resolveSibling(enabled ? fileName + OFF : fileName.substring(0, fileName.length() - OFF.length()));
        Files.move(file, renamed);
        return new InstalledMod(renamed, !enabled, squidMod, name, version, authors, description, minecraft);
    }

    /** A .java mod: its name comes from the file name, like MyCoolMod.java becoming "My Cool Mod". */
    private static InstalledMod readSource(Path file, boolean enabled) {
        String fileName = file.getFileName().toString();
        String className = fileName.substring(0, fileName.indexOf(".java"));
        String name = className.replace('_', ' ')
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ").trim();
        return new InstalledMod(file, enabled, true, name, "", List.of(),
                "Your own mod, in " + fileName + ". Click Edit to change it, then play!", List.of());
    }

    private static InstalledMod read(Path file, boolean enabled) {
        String fileName = file.getFileName().toString();
        String plainName = fileName.substring(0, fileName.indexOf(".jar"));
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("squid.json");
            if (entry == null) return new InstalledMod(file, enabled, false, plainName, "", List.of(), "", List.of());
            Map<String, Object> json = Json.object(Json.parse(
                    new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)));
            // "minecraft" can be one version ("26.3") or a list (["26.3", "26.4"])
            List<String> minecraft = json.get("minecraft") instanceof String one ? List.of(one) : strings(json, "minecraft");
            return new InstalledMod(file, enabled, true, text(json, "name", plainName), text(json, "version", ""),
                    strings(json, "authors"), text(json, "description", ""), minecraft);
        } catch (IOException | RuntimeException e) {
            // Not a real jar, or a broken squid.json. Still list it, so it can be turned off or removed.
            return new InstalledMod(file, enabled, false, plainName, "", List.of(), "Couldn't read this mod: " + e.getMessage(),
                    List.of());
        }
    }

    private static List<String> strings(Map<String, Object> json, String key) {
        List<String> values = new ArrayList<>();
        if (json.get(key) != null) {
            for (Object value : Json.array(json.get(key))) values.add(String.valueOf(value));
        }
        return List.copyOf(values);
    }

    private static String text(Map<String, Object> json, String key, String fallback) {
        return json.get(key) instanceof String value ? value : fallback;
    }
}
