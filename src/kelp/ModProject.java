package kelp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Squid projects: a mod that's a folder in the mods folder, for when one file isn't enough.
 *
 * <pre>
 * MegaMod/
 *   squid.json     {"name": "Mega Mod"}
 *   src/           the code, as many files as it needs
 *   resources/     pictures and sounds
 * </pre>
 *
 * Squid builds it by itself as the game starts. Kelp makes new ones already set up for VS Code and IntelliJ, and packs
 * them into one .squid file for sharing.
 */
public final class ModProject {
    private ModProject() {
    }

    /** Whether this is a project folder (on or off): a folder with a squid.json or a src folder. */
    public static boolean isProject(Path path) {
        return Files.isDirectory(path) && !path.getFileName().toString().startsWith(".")
                && (Files.exists(path.resolve("squid.json")) || Files.isDirectory(path.resolve("src")));
    }

    /** Makes a working project from a name and gives back its folder. A taken name gets a number: MegaMod2. */
    public static Path create(Path modsFolder, String name, String minecraftVersion) throws IOException {
        return create(modsFolder, name, minecraftVersion, null);
    }

    /** The same, starting from a starter mod (null for a blank one). */
    public static Path create(Path modsFolder, String name, String minecraftVersion, ModStarters.Starter starter) throws IOException {
        Files.createDirectories(modsFolder);
        String base = ModTemplate.className(name);
        String className = base;
        for (int n = 2; taken(modsFolder, className); n++) className = base + n;
        String shownName = name.isBlank() ? "My Mod" : name.trim();

        Path folder = modsFolder.resolve(className);
        Files.createDirectories(folder.resolve("src"));
        Files.createDirectories(folder.resolve("resources"));
        // The id and main class are written down, so renaming the folder later doesn't change them (the id keeps
        // the mod's settings, and main is the class Squid starts)
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", idFor(className));
        json.put("name", shownName);
        json.put("version", "1.0");
        json.put("main", className);
        json.put("icon", "icon.png");
        Files.writeString(folder.resolve("squid.json"), toJson(json));
        // Its own little icon, made from its name, to paint over or replace
        javax.imageio.ImageIO.write(ModIcons.generate(shownName), "png", folder.resolve("resources").resolve("icon.png").toFile());
        String code = starter != null ? "import squid.api.*;\n\n" + starter.codeFor(shownName, className) : code(shownName, className);
        Files.writeString(folder.resolve("src").resolve(className + ".java"), code.replace("\n", System.lineSeparator()));
        editorSettings(folder, className, minecraftVersion);
        return folder;
    }

    /** A mod's id from its class name, the same way Squid makes one: MegaMod becomes mega-mod. */
    static String idFor(String className) {
        return ModTemplate.spaced(className).toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    static boolean taken(Path modsFolder, String className) {
        for (String name : new String[] {className, className + ".disabled", className + ".java", className + ".java.disabled",
                className + ".squid", className + ".squid.disabled"}) {
            if (Files.exists(modsFolder.resolve(name))) return true;
        }
        return false;
    }

    static String code(String name, String className) {
        return """
                // %1$s: your own Squid project!
                // Your code goes in src (as many files as you like), pictures and sounds in resources.
                // Save, then play: Squid builds it by itself. In Kelp's Mods screen, Pack makes it one .squid file to share.
                //
                // Start with the easy commands: say, showText, playSound, giveItem, command, onKey, onJoin, every...
                // When you want more, squid() reaches into any part of Minecraft. Your editor knows every command:
                // type squid(). and have a look.

                import squid.api.*;

                public class %2$s extends EasyMod {
                    void start() {
                        say("%1$s is working!");

                        onKey("H", () -> {
                            say("Hello from your project! You're at " + x() + ", " + y() + ", " + z());
                            playSound("entity.experience_orb.pickup");
                        });
                    }
                }
                """.formatted(name.replace("\\", "").replace("\"", "'"), className);
    }

    /**
     * Tells VS Code and IntelliJ where Squid and Minecraft are, so they autocomplete every command and explain it.
     * The Squid library comes with Squid (squid/library in Kelp's folder), so it's always the Squid the mod runs on.
     * Its code jar is where the editors read what each command does.
     */
    static void editorSettings(Path folder, String className, String minecraftVersion) throws IOException {
        Path library = Folders.squidInUse().resolve("library");
        Path squidApi = library.resolve("squid-api.jar");
        Path squidSources = library.resolve("squid-api-sources.jar");
        List<String> minecraft = Launcher.gameClasspath(minecraftVersion);

        // VS Code (with its Java extension): the libraries, and where Squid's code is, for its explanations
        List<String> all = new ArrayList<>();
        all.add(quote(squidApi.toString()));
        for (String jar : minecraft) all.add(quote(jar));
        Files.createDirectories(folder.resolve(".vscode"));
        Files.writeString(folder.resolve(".vscode").resolve("settings.json"), """
                {
                    "java.project.sourcePaths": ["src"],
                    "java.project.referencedLibraries": {
                        "include": [
                            %s
                        ],
                        "sources": {
                            %s: %s
                        }
                    }
                }
                """.formatted(String.join(",\n            ", all), quote(squidApi.toString()), quote(squidSources.toString())));

        // IntelliJ: one module with src and resources, Squid (with its code and docs) and Minecraft
        Path idea = folder.resolve(".idea");
        Files.createDirectories(idea);
        Files.writeString(idea.resolve("modules.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="ProjectModuleManager">
                    <modules>
                      <module fileurl="file://$PROJECT_DIR$/.idea/%1$s.iml" filepath="$PROJECT_DIR$/.idea/%1$s.iml" />
                    </modules>
                  </component>
                </project>
                """.formatted(className));
        StringBuilder minecraftRoots = new StringBuilder();
        for (String jar : minecraft) minecraftRoots.append("          <root url=\"").append(jarUrl(jar)).append("\" />\n");
        Files.writeString(idea.resolve(className + ".iml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <module type="JAVA_MODULE" version="4">
                  <component name="NewModuleRootManager" inherit-compiler-output="true">
                    <exclude-output />
                    <content url="file://$MODULE_DIR$/..">
                      <sourceFolder url="file://$MODULE_DIR$/../src" isTestSource="false" />
                      <sourceFolder url="file://$MODULE_DIR$/../resources" type="java-resource" />
                    </content>
                    <orderEntry type="inheritedJdk" />
                    <orderEntry type="sourceFolder" forTests="false" />
                    <orderEntry type="module-library">
                      <library name="Squid">
                        <CLASSES>
                          <root url="%s" />
                        </CLASSES>
                        <JAVADOC />
                        <SOURCES>
                          <root url="%s" />
                        </SOURCES>
                      </library>
                    </orderEntry>
                    <orderEntry type="module-library">
                      <library name="Minecraft">
                        <CLASSES>
                %s        </CLASSES>
                        <JAVADOC />
                        <SOURCES />
                      </library>
                    </orderEntry>
                  </component>
                </module>
                """.formatted(jarUrl(squidApi.toString()), jarUrl(squidSources.toString()),
                minecraftRoots));
    }

    /** IntelliJ's way of naming a jar: jar://C:/path/to/it.jar!/ */
    private static String jarUrl(String jar) {
        return "jar://" + jar.replace('\\', '/') + "!/";
    }

    /**
     * Packs a project into one .squid file in the folder given, and gives back the file. Its squid.json gets everything
     * filled in (id, name, version, main class), so the file still works if it's renamed.
     */
    public static Path pack(Path project, Path outFolder) throws IOException {
        String folderName = project.getFileName().toString();
        if (folderName.endsWith(".disabled")) folderName = folderName.substring(0, folderName.length() - ".disabled".length());
        String className = folderName.replaceAll("[^A-Za-z0-9_]", "");
        Path src = project.resolve("src");
        if (!Files.isDirectory(src)) throw new IOException("it has no src folder");

        Map<String, Object> json = new LinkedHashMap<>();
        Path squidJson = project.resolve("squid.json");
        if (Files.exists(squidJson)) {
            Object parsed;
            try {
                parsed = Json.parse(text(Files.readAllBytes(squidJson)));
            } catch (RuntimeException e) {
                throw new IOException("its squid.json is broken: " + e.getMessage());
            }
            if (!(parsed instanceof Map<?, ?>)) throw new IOException("its squid.json is broken: it has to start with { and end with }");
            json.putAll(Json.object(parsed));
        }
        String spaced = ModTemplate.spaced(className);
        json.putIfAbsent("id", idFor(className));
        json.putIfAbsent("name", spaced);
        json.putIfAbsent("version", "1.0");
        json.putIfAbsent("main", className);
        // Checked before packing, so nobody shares a mod that can't load
        if (!(json.get("id") instanceof String id) || !id.matches("[a-z0-9_-]+")) {
            throw new IOException("its id can only use a-z, 0-9, _ and -");
        }
        String main = String.valueOf(json.get("main"));
        if (!Files.exists(src.resolve(main.replace('.', '/') + ".java"))) {
            throw new IOException("its main class " + main + " isn't in src. Put \"main\": \"YourClass\" in squid.json");
        }

        Files.createDirectories(outFolder);
        Path file = outFolder.resolve(className + ".squid");
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "squid.json");
            zip.write(toJson(json).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String part : new String[] {"src", "resources"}) {
                Path folder = project.resolve(part);
                if (!Files.isDirectory(folder)) continue;
                List<Path> files;
                try (Stream<Path> walk = Files.walk(folder)) {
                    files = walk.filter(Files::isRegularFile).filter(p -> !junk(p.getFileName().toString())).toList();
                }
                // Sorted by their names with /, so the same project packs to the same bytes on every computer
                List<String> names = new ArrayList<>();
                for (Path p : files) names.add(project.relativize(p).toString().replace('\\', '/'));
                java.util.Collections.sort(names);
                for (String name : names) {
                    put(zip, name);
                    Files.copy(project.resolve(name), zip);
                    zip.closeEntry();
                }
            }
        }
        return file;
    }

    /** A fixed time on every file in a .squid, so packing the same project twice makes the same file (and fingerprint). */
    private static final long PACKED_TIME = java.time.LocalDateTime.of(2026, 1, 1, 0, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

    private static void put(ZipOutputStream zip, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(PACKED_TIME);
        zip.putNextEntry(entry);
    }

    /** Files computers make by themselves, which nobody wants in a shared mod. */
    static boolean junk(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.equals("thumbs.db") || lower.equals("desktop.ini") || lower.equals(".ds_store")
                || lower.startsWith("._") || lower.endsWith("~") || lower.endsWith(".tmp") || lower.endsWith(".swp");
    }

    /** Text from a file, with or without the mark (BOM) some editors put at the start. */
    static String text(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    /** Writes squid.json back out: text, numbers, true/false, null, and lists and objects of those. */
    static String toJson(Map<String, Object> json) {
        return value(json, "") + "\n";
    }

    private static String value(Object value, String indent) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) items.add(value(item, indent));
            return "[" + String.join(", ", items) + "]";
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{}";
            String inner = indent + "    ";
            List<String> items = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                items.add(inner + quote(String.valueOf(entry.getKey())) + ": " + value(entry.getValue(), inner));
            }
            return "{\n" + String.join(",\n", items) + "\n" + indent + "}";
        }
        if (value instanceof Double number && number == Math.rint(number) && !number.isInfinite()) return String.valueOf(number.longValue());
        return String.valueOf(value);
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
