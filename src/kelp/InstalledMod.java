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
 * A mod in a version's mods folder, described by the same squid.json Squid reads.
 * Turned-off mods end in ".jar.disabled", which Squid skips.
 *
 * @param squidMod false if the jar has no squid.json (so Squid will skip it anyway)
 */
public record InstalledMod(Path file, boolean enabled, boolean squidMod, String name, String version,
                           List<String> authors, String description) {
    private static final String OFF = ".disabled";

    /** Every mod in the folder, sorted by name. */
    public static List<InstalledMod> list(Path folder) {
        List<InstalledMod> mods = new ArrayList<>();
        if (!Files.isDirectory(folder)) return mods;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".jar")) mods.add(read(file, true));
                else if (fileName.endsWith(".jar" + OFF)) mods.add(read(file, false));
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
        return new InstalledMod(renamed, !enabled, squidMod, name, version, authors, description);
    }

    private static InstalledMod read(Path file, boolean enabled) {
        String fileName = file.getFileName().toString();
        String plainName = fileName.substring(0, fileName.indexOf(".jar"));
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("squid.json");
            if (entry == null) return new InstalledMod(file, enabled, false, plainName, "", List.of(), "");
            Map<String, Object> json = Json.object(Json.parse(
                    new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)));
            List<String> authors = new ArrayList<>();
            if (json.get("authors") != null) {
                for (Object author : Json.array(json.get("authors"))) authors.add(String.valueOf(author));
            }
            return new InstalledMod(file, enabled, true,
                    text(json, "name", plainName), text(json, "version", ""), authors, text(json, "description", ""));
        } catch (IOException | RuntimeException e) {
            // Not a real jar, or a broken squid.json. Still list it, so it can be turned off or removed.
            return new InstalledMod(file, enabled, false, plainName, "", List.of(), "Couldn't read this mod: " + e.getMessage());
        }
    }

    private static String text(Map<String, Object> json, String key, String fallback) {
        return json.get(key) instanceof String value ? value : fallback;
    }
}
