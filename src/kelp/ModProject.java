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
        Files.createDirectories(modsFolder);
        String base = ModTemplate.className(name);
        String className = base;
        for (int n = 2; taken(modsFolder, className); n++) className = base + n;
        String shownName = name.isBlank() ? "My Mod" : name.trim();

        Path folder = modsFolder.resolve(className);
        Files.createDirectories(folder.resolve("src"));
        Files.createDirectories(folder.resolve("resources"));
        Files.writeString(folder.resolve("squid.json"), "{\n    \"name\": " + quote(shownName) + ",\n    \"version\": \"1.0\"\n}\n");
        Files.writeString(folder.resolve("src").resolve(className + ".java"),
                code(shownName, className).replace("\n", System.lineSeparator()));
        editorSettings(folder, className, minecraftVersion);
        return folder;
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
            try {
                json.putAll(Json.object(Json.parse(Files.readString(squidJson, StandardCharsets.UTF_8))));
            } catch (RuntimeException e) {
                throw new IOException("its squid.json is broken: " + e.getMessage());
            }
        }
        String spaced = ModTemplate.spaced(className);
        json.putIfAbsent("id", spaced.toLowerCase(Locale.ROOT).replace(' ', '-'));
        json.putIfAbsent("name", spaced);
        json.putIfAbsent("version", "1.0");
        json.putIfAbsent("main", className);

        Files.createDirectories(outFolder);
        Path file = outFolder.resolve(className + ".squid");
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("squid.json"));
            zip.write(toJson(json).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String part : new String[] {"src", "resources"}) {
                Path folder = project.resolve(part);
                if (!Files.isDirectory(folder)) continue;
                try (Stream<Path> walk = Files.walk(folder)) {
                    for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                        zip.putNextEntry(new ZipEntry(project.relativize(p).toString().replace('\\', '/')));
                        Files.copy(p, zip);
                        zip.closeEntry();
                    }
                }
            }
        }
        return file;
    }

    /** Writes squid.json's simple values back out: text, numbers, true/false, and lists of those. */
    static String toJson(Map<String, Object> json) {
        StringBuilder out = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            out.append("    ").append(quote(entry.getKey())).append(": ").append(value(entry.getValue()));
            out.append(++i < json.size() ? ",\n" : "\n");
        }
        return out.append("}\n").toString();
    }

    private static String value(Object value) {
        if (value instanceof String text) return quote(text);
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) items.add(value(item));
            return "[" + String.join(", ", items) + "]";
        }
        if (value instanceof Double number && number == Math.rint(number)) return String.valueOf(number.longValue());
        return String.valueOf(value);
    }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
