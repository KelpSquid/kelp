package kelp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A mod in an instance's mods folder: a Squid mod (a jar with a squid.json), one Squid builds from its code (a .java
 * file, a project folder, or a project packed into a .squid file), or a Fabric, Quilt, NeoForge or Forge mod.
 * Turned-off mods end in ".disabled", which every loader skips.
 *
 * @param kind      which loader the mod is for, or null if Kelp can't tell (then it isn't a mod any loader knows)
 * @param minecraft the Minecraft versions the mod says it works on, like "26.3.x". Empty means any.
 */
public record InstalledMod(Path file, boolean enabled, Loader kind, String name, String version,
                           List<String> authors, String description, List<String> minecraft) {
    private static final String OFF = ".disabled";
    private static final Pattern TOML_TEXT = Pattern.compile("(?m)^\\s*(displayName|version|description|authors)\\s*=\\s*\"([^\"]*)\"");

    /** Whether it's a Squid mod. */
    public boolean squidMod() {
        return kind == Loader.SQUID;
    }

    /** Whether it's your own code, which can be opened and changed: a .java file or a project folder. */
    public boolean source() {
        return file.getFileName().toString().contains(".java") || project();
    }

    /** Whether it's a project folder, which can be packed into a .squid file. */
    public boolean project() {
        return Files.isDirectory(file);
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
                if (ModProject.isProject(file)) mods.add(readProject(file, !fileName.endsWith(OFF)));
                else if (fileName.endsWith(".jar") || fileName.endsWith(".squid")) mods.add(read(file, true));
                else if (fileName.endsWith(".jar" + OFF) || fileName.endsWith(".squid" + OFF)) mods.add(read(file, false));
                else if (fileName.endsWith(".java")) mods.add(readSource(file, true));
                else if (fileName.endsWith(".java" + OFF)) mods.add(readSource(file, false));
            }
        } catch (IOException e) {
            System.err.println("Couldn't list " + folder + ": " + e.getMessage());
        }
        mods.sort(Comparator.comparing(mod -> mod.name().toLowerCase()));
        return mods;
    }

    /** One mod file or folder, read the same way the list reads it. Null if it isn't a mod at all. */
    public static InstalledMod of(Path file) {
        String fileName = file.getFileName().toString();
        if (ModProject.isProject(file)) return readProject(file, !fileName.endsWith(OFF));
        if (fileName.endsWith(".jar") || fileName.endsWith(".squid")) return read(file, true);
        if (fileName.endsWith(".jar" + OFF) || fileName.endsWith(".squid" + OFF)) return read(file, false);
        if (fileName.endsWith(".java")) return readSource(file, true);
        if (fileName.endsWith(".java" + OFF)) return readSource(file, false);
        return null;
    }

    /** Turns the mod on or off by renaming its file. Gives back the mod as it is now. */
    public InstalledMod toggle() throws IOException {
        String fileName = file.getFileName().toString();
        Path renamed = file.resolveSibling(enabled ? fileName + OFF : fileName.substring(0, fileName.length() - OFF.length()));
        Files.move(file, renamed);
        return new InstalledMod(renamed, !enabled, kind, name, version, authors, description, minecraft);
    }

    /** A .java mod: its name comes from the file name, like MyCoolMod.java becoming "My Cool Mod". */
    private static InstalledMod readSource(Path file, boolean enabled) {
        String fileName = file.getFileName().toString();
        String className = fileName.substring(0, fileName.indexOf(".java"));
        return new InstalledMod(file, enabled, Loader.SQUID, ModTemplate.spaced(className), "", List.of(),
                Lang.t("Your own mod, in {0}. Click Edit to change it, then play!", fileName), List.of());
    }

    /** A project folder: its squid.json says what it's called, or else its folder's name does, like Squid reads it. */
    private static InstalledMod readProject(Path folder, boolean enabled) {
        String folderName = folder.getFileName().toString();
        if (!enabled) folderName = folderName.substring(0, folderName.length() - OFF.length());
        String name = ModTemplate.spaced(folderName.replaceAll("[^A-Za-z0-9_]", ""));
        Map<String, Object> json = Map.of();
        try {
            Path squidJson = folder.resolve("squid.json");
            if (Files.exists(squidJson)) json = object(ModProject.text(Files.readAllBytes(squidJson)));
        } catch (IOException | RuntimeException e) {
            return new InstalledMod(folder, enabled, Loader.SQUID, name, "", List.of(), "Its squid.json is broken: " + e.getMessage(), List.of());
        }
        if (json == null) json = Map.of();
        List<String> minecraft = json.get("minecraft") instanceof String one ? List.of(one) : strings(json, "minecraft");
        String description = text(json, "description", "");
        return new InstalledMod(folder, enabled, Loader.SQUID, text(json, "name", name), text(json, "version", ""),
                strings(json, "authors"), description.isEmpty()
                ? Lang.t("Your own project, in the {0} folder. Edit it, play, or Pack it to share!", folderName) : description, minecraft);
    }

    private static InstalledMod read(Path file, boolean enabled) {
        String fileName = file.getFileName().toString();
        String plainName = fileName.substring(0, fileName.contains(".squid") ? fileName.indexOf(".squid") : fileName.indexOf(".jar"));
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry squid = zip.getEntry("squid.json");
            if (squid != null) {
                Map<String, Object> json = Json.object(Json.parse(text(zip, squid)));
                // "minecraft" can be one version ("26.3") or a list (["26.3", "26.4"])
                List<String> minecraft = json.get("minecraft") instanceof String one ? List.of(one) : strings(json, "minecraft");
                return new InstalledMod(file, enabled, Loader.SQUID, text(json, "name", plainName), text(json, "version", ""),
                        strings(json, "authors"), text(json, "description", ""), minecraft);
            }
            ZipEntry quilt = zip.getEntry("quilt.mod.json");
            if (quilt != null) {
                Map<String, Object> loader = Json.object(Json.object(Json.parse(text(zip, quilt))).get("quilt_loader"));
                Map<String, Object> about = loader == null ? null : Json.object(loader.get("metadata"));
                if (about == null) about = Map.of();
                return new InstalledMod(file, enabled, Loader.QUILT, text(about, "name", plainName),
                        loader == null ? "" : text(loader, "version", ""), List.of(), text(about, "description", ""), List.of());
            }
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                Map<String, Object> json = Json.object(Json.parse(text(zip, fabric)));
                return new InstalledMod(file, enabled, Loader.FABRIC, text(json, "name", plainName), text(json, "version", ""),
                        names(json.get("authors")), text(json, "description", ""), List.of());
            }
            ZipEntry neoforge = zip.getEntry("META-INF/neoforge.mods.toml");
            ZipEntry forge = zip.getEntry("META-INF/mods.toml");
            if (neoforge != null || forge != null) {
                String toml = text(zip, neoforge != null ? neoforge : forge);
                Map<String, String> values = new java.util.HashMap<>();
                Matcher m = TOML_TEXT.matcher(toml);
                while (m.find()) values.putIfAbsent(m.group(1), m.group(2));
                String version = values.getOrDefault("version", "");
                if (version.startsWith("$")) version = ""; // filled in when the mod was built, so not readable here
                return new InstalledMod(file, enabled, neoforge != null ? Loader.NEOFORGE : Loader.FORGE,
                        values.getOrDefault("displayName", plainName), version,
                        values.containsKey("authors") ? List.of(values.get("authors")) : List.of(),
                        values.getOrDefault("description", "").trim(), List.of());
            }
            return new InstalledMod(file, enabled, null, plainName, "", List.of(), "", List.of());
        } catch (IOException | RuntimeException e) {
            // Not a real jar, or a broken file inside. Still list it, so it can be turned off or removed.
            return new InstalledMod(file, enabled, null, plainName, "", List.of(), "Couldn't read this mod: " + e.getMessage(),
                    List.of());
        }
    }

    private static String text(ZipFile zip, ZipEntry entry) throws IOException {
        return ModProject.text(zip.getInputStream(entry).readAllBytes());
    }

    /**
     * A Squid mod's id, the way Squid reads it: from its squid.json, or else from its file or folder name (MegaMod
     * becomes mega-mod). Null for mods that aren't Squid mods, or that can't be read.
     */
    public static String squidId(Path file) {
        String fileName = file.getFileName().toString();
        if (fileName.endsWith(OFF)) fileName = fileName.substring(0, fileName.length() - OFF.length());
        try {
            Map<String, Object> json = null;
            String plain;
            if (Files.isDirectory(file)) {
                plain = fileName;
                Path squidJson = file.resolve("squid.json");
                if (Files.exists(squidJson)) json = object(ModProject.text(Files.readAllBytes(squidJson)));
            } else if (fileName.endsWith(".java")) {
                return ModProject.idFor(fileName.substring(0, fileName.length() - ".java".length()).replaceAll("[^A-Za-z0-9_]", ""));
            } else if (fileName.endsWith(".jar") || fileName.endsWith(".squid")) {
                plain = fileName.substring(0, fileName.lastIndexOf('.'));
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    String root = fileName.endsWith(".squid") ? ModProject.packedRoot(zip) : "";
                    ZipEntry entry = root == null ? null : fileName.endsWith(".squid") ? ModProject.entry(zip, root + "squid.json") : zip.getEntry("squid.json");
                    if (entry == null) return null;
                    json = object(text(zip, entry));
                }
                if (fileName.endsWith(".jar")) return json.get("id") instanceof String id ? id : null;
            } else {
                return null;
            }
            if (json != null && json.get("id") instanceof String id && !id.isBlank()) return id;
            return ModProject.idFor(plain.replaceAll("[^A-Za-z0-9_]", ""));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Map<String, Object> object(String text) {
        Object parsed = Json.parse(text);
        return parsed instanceof Map<?, ?> ? Json.object(parsed) : Map.of();
    }

    /** Fabric lists authors as names, or as {"name": ...} objects. */
    private static List<String> names(Object list) {
        List<String> names = new ArrayList<>();
        if (list != null) {
            for (Object author : Json.array(list)) {
                if (author instanceof String name) names.add(name);
                else if (Json.object(author).get("name") instanceof String name) names.add(name);
            }
        }
        return List.copyOf(names);
    }

    /** A list from squid.json. One value on its own ("authors": "Sam") counts as a list of one, like Squid reads it. */
    private static List<String> strings(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return List.of();
        List<?> list = value instanceof List<?> many ? many : List.of(value);
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) values.add(String.valueOf(item));
        }
        return List.copyOf(values);
    }

    private static String text(Map<String, Object> json, String key, String fallback) {
        return json.get(key) instanceof String value ? value : fallback;
    }
}
