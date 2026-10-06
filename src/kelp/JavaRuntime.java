package kelp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Java that Mojang made for each Minecraft version, downloaded the same way the official launcher does.
 * Each one has a name: 26.3 uses "java-runtime-epsilon" (Java 25), very old versions use "jre-legacy" (Java 8).
 */
public final class JavaRuntime {
    private static final String ALL_RUNTIMES =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private JavaRuntime() {
    }

    /** Which Java a version wants. Versions from before Mojang started choosing just get Java 8. */
    public static String componentFor(Map<String, Object> versionDetails) {
        Map<String, Object> javaVersion = Json.object(versionDetails.get("javaVersion"));
        return javaVersion != null ? (String) javaVersion.get("component") : "jre-legacy";
    }

    /** The folder a Java gets downloaded into. */
    public static Path folder(String component) {
        return Folders.runtimes().resolve(component);
    }

    /** The program that runs the game. javaw is the Windows one that doesn't open a black console window. */
    public static Path executable(String component) {
        return folder(component).resolve(switch (Rules.osName()) {
            case "windows" -> "bin/javaw.exe";
            case "osx" -> "jre.bundle/Contents/Home/bin/java"; // Mac Javas come packed inside a bundle
            default -> "bin/java";
        });
    }

    /** Mojang's name for this kind of computer. */
    private static String platform() {
        String arch = System.getProperty("os.arch");
        boolean arm = arch.equals("aarch64") || arch.equals("arm64");
        return switch (Rules.osName()) {
            case "windows" -> arm ? "windows-arm64" : "windows-x64";
            case "osx" -> arm ? "mac-os-arm64" : "mac-os";
            default -> "linux";
        };
    }

    /** Works out every file this Java needs and adds them to the download list. */
    public static List<Downloader.Job> jobs(String component, Downloader downloader) throws IOException, InterruptedException {
        Path folder = folder(component);
        Map<String, Object> all = Json.object(Json.parse(downloader.fetchText(ALL_RUNTIMES, Folders.runtimes().resolve("all.json"))));
        Map<String, Object> forThisComputer = Json.object(all.get(platform()));
        List<Object> choices = forThisComputer == null ? null : Json.array(forThisComputer.get(component));
        if (choices == null || choices.isEmpty()) {
            throw new IOException("Mojang doesn't have " + component + " for " + platform());
        }

        Map<String, Object> manifestInfo = Json.object(Json.object(choices.get(0)).get("manifest"));
        Map<String, Object> manifest = Json.object(Json.parse(
                downloader.fetchText((String) manifestInfo.get("url"), folder.resolve("manifest.json"))));

        List<Downloader.Job> jobs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : Json.object(manifest.get("files")).entrySet()) {
            Map<String, Object> file = Json.object(entry.getValue());
            if (!"file".equals(file.get("type"))) continue; // folders get made as files land in them
            Map<String, Object> raw = Json.object(Json.object(file.get("downloads")).get("raw"));
            jobs.add(new Downloader.Job((String) raw.get("url"), folder.resolve(entry.getKey()),
                    (String) raw.get("sha1"), ((Number) raw.get("size")).longValue()));
        }
        return jobs;
    }

    /** On Mac and Linux, java has to be marked as a program before it can run. Windows doesn't need this. */
    public static void markRunnable(String component) throws IOException {
        Path java = executable(component);
        if (Files.exists(java) && !java.toFile().setExecutable(true)) {
            throw new IOException("Couldn't make " + java + " runnable");
        }
    }
}
