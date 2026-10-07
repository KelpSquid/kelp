package kelp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * NeoForge and Forge. They have to patch Minecraft before it can run, so Kelp downloads their own installer
 * and runs it quietly (no window) into Kelp's folder, which is laid out like Minecraft's own. The installer
 * patches the game and writes a version file that Kelp merges into Minecraft's, the same way it does for Fabric.
 */
public final class ForgeInstallers {
    // Where their versions and installers come from. Tests point these at a pretend server.
    static String neoForgeMaven = "https://maven.neoforged.net";
    static String forgeMaven = "https://maven.minecraftforge.net";
    static String forgePromotions = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

    private ForgeInstallers() {
    }

    /**
     * Installs the newest NeoForge or Forge for this Minecraft version (unless it's already installed) and gives back
     * its version id, like "neoforge-26.3.0.55-beta". java is the Java that runs the installer.
     */
    public static String install(Loader loader, String minecraft, Downloader downloader, Path java)
            throws IOException, InterruptedException {
        Path cache = Folders.versions().resolve("loaders");
        String url;
        if (loader == Loader.NEOFORGE) {
            String version = neoForgeVersion(minecraft, downloader);
            url = neoForgeMaven + "/releases/net/neoforged/neoforge/" + version + "/neoforge-" + version + "-installer.jar";
        } else if (loader == Loader.FORGE) {
            String version = minecraft + "-" + forgeVersion(minecraft, downloader);
            url = forgeMaven + "/net/minecraftforge/forge/" + version + "/forge-" + version + "-installer.jar";
        } else {
            throw new IllegalArgumentException(loader.label() + " isn't installed this way");
        }
        Path installer = cache.resolve(url.substring(url.lastIndexOf('/') + 1));
        String sha1 = downloader.fetchText(url + ".sha1", installer.resolveSibling(installer.getFileName() + ".sha1")).trim();
        downloader.downloadAll(List.of(new Downloader.Job(url, installer, sha1.isEmpty() ? null : sha1, -1)));

        String id = profileId(installer);
        Path versionFile = Folders.versions().resolve(id).resolve(id + ".json");
        if (Files.exists(versionFile)) return id; // installed before

        // The installer expects a Minecraft launcher folder, which always has this file
        Path profiles = Folders.home().resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) Files.writeString(profiles, "{\"profiles\": {}}");
        Path log = cache.resolve(loader.name().toLowerCase() + "-install.log");
        Process run = new ProcessBuilder(java.toString(), "-jar", installer.toString(),
                loader == Loader.NEOFORGE ? "--install-client" : "--installClient", Folders.home().toString())
                .directory(cache.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        int exit = run.waitFor();
        if (exit != 0 || !Files.exists(versionFile)) {
            throw new IOException(loader.label() + " couldn't install. What it said is in " + Folders.home().relativize(log) + ".");
        }
        return id;
    }

    /** The version id an installer makes, from the install_profile.json inside it. */
    static String profileId(Path installer) throws IOException {
        try (ZipFile zip = new ZipFile(installer.toFile())) {
            ZipEntry entry = zip.getEntry("install_profile.json");
            if (entry == null) throw new IOException(installer.getFileName() + " isn't an installer Kelp knows.");
            try (InputStream in = zip.getInputStream(entry)) {
                Map<String, Object> profile = Json.object(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                if (!(profile.get("version") instanceof String id) || !id.matches("[A-Za-z0-9._+-]+")) {
                    throw new IOException(installer.getFileName() + " doesn't say which version it makes.");
                }
                return id;
            }
        }
    }

    /** NeoForge's newest version for this Minecraft: a release if there is one, else the newest beta. */
    static String neoForgeVersion(String minecraft, Downloader downloader) throws IOException, InterruptedException {
        String list = downloader.fetchText(neoForgeMaven + "/api/maven/versions/releases/net/neoforged/neoforge",
                Folders.versions().resolve("loaders").resolve("neoforge-versions.json"));
        String prefix = neoForgePrefix(minecraft);
        String newest = null;
        String newestStable = null;
        for (Object entry : Json.array(Json.object(Json.parse(list)).get("versions"))) {
            String version = (String) entry;
            if (!version.startsWith(prefix)) continue;
            if (newest == null || LoaderProfiles.compare(version, newest) > 0) newest = version;
            if (!version.contains("-") && (newestStable == null || LoaderProfiles.compare(version, newestStable) > 0)) newestStable = version;
        }
        String chosen = newestStable != null ? newestStable : newest;
        if (chosen == null) throw new IOException("NeoForge doesn't support Minecraft " + minecraft + " yet.");
        return chosen;
    }

    /**
     * NeoForge's versions start with the Minecraft version, written their way: 1.21.1 is "21.1.", 1.21 is "21.0.",
     * and newer ones like 26.3 are "26.3.0." (and 26.3.1 is "26.3.1.").
     */
    static String neoForgePrefix(String minecraft) {
        String[] parts = (minecraft.startsWith("1.") ? minecraft.substring(2) : minecraft).split("\\.");
        if (minecraft.startsWith("1.")) return parts[0] + "." + (parts.length > 1 ? parts[1] : "0") + ".";
        return parts[0] + "." + (parts.length > 1 ? parts[1] : "0") + "." + (parts.length > 2 ? parts[2] : "0") + ".";
    }

    /** Forge's recommended version for this Minecraft, or its latest if none is recommended yet. */
    static String forgeVersion(String minecraft, Downloader downloader) throws IOException, InterruptedException {
        String text = downloader.fetchText(forgePromotions, Folders.versions().resolve("loaders").resolve("forge-promotions.json"));
        Map<String, Object> promos = Json.object(Json.object(Json.parse(text)).get("promos"));
        Object chosen = promos.get(minecraft + "-recommended");
        if (chosen == null) chosen = promos.get(minecraft + "-latest");
        if (!(chosen instanceof String version)) throw new IOException("Forge doesn't support Minecraft " + minecraft + " yet.");
        return version;
    }
}
