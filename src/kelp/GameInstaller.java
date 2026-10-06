package kelp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Gets everything a Minecraft version needs to run: the game itself, its libraries, its assets and its Java. */
public class GameInstaller {
    private static final String ASSETS_URL = "https://resources.download.minecraft.net/";

    private final Downloader downloader = new Downloader();
    private volatile String stage = "Starting";

    public Downloader getDownloader() {
        return downloader;
    }

    /** What the installer is doing right now, to show on screen. */
    public String getStage() {
        return stage;
    }

    public void install(VersionManifest.Version version) throws IOException, InterruptedException {
        stage = "Getting version details";
        Path versionFolder = Folders.versions().resolve(version.id());
        Map<String, Object> details = Json.object(Json.parse(
                downloader.fetchText(version.url(), versionFolder.resolve(version.id() + ".json"))));

        // Keyed by where the file goes, because some assets are listed twice under different names
        Map<Path, Downloader.Job> jobs = new LinkedHashMap<>();

        // 1. The game itself
        Map<String, Object> client = Json.object(Json.object(details.get("downloads")).get("client"));
        add(jobs, client, versionFolder.resolve(version.id() + ".jar"));

        // 2. Libraries: code the game uses, like LWJGL for graphics and sound
        for (Object entry : Json.array(details.get("libraries"))) {
            Map<String, Object> library = Json.object(entry);
            if (!Rules.allowed(library.get("rules"))) continue; // for a different operating system
            Map<String, Object> downloads = Json.object(library.get("downloads"));
            if (downloads == null) continue;
            Map<String, Object> artifact = Json.object(downloads.get("artifact"));
            if (artifact != null) add(jobs, artifact, Folders.libraries().resolve((String) artifact.get("path")));

            // Versions before 1.19 list their native files (DLLs) separately, under "classifiers"
            Map<String, Object> natives = Json.object(library.get("natives"));
            Map<String, Object> classifiers = Json.object(downloads.get("classifiers"));
            if (natives != null && classifiers != null && natives.get(Rules.osName()) != null) {
                String classifier = ((String) natives.get(Rules.osName())).replace("${arch}", "64");
                Map<String, Object> file = Json.object(classifiers.get(classifier));
                if (file != null) add(jobs, file, Folders.libraries().resolve((String) file.get("path")));
            }
        }

        // 3. The game's logging settings
        Map<String, Object> logging = Json.object(details.get("logging"));
        if (logging != null && logging.get("client") != null) {
            Map<String, Object> logFile = Json.object(Json.object(logging.get("client")).get("file"));
            add(jobs, logFile, Folders.assets().resolve("log_configs").resolve((String) logFile.get("id")));
        }

        // 4. Assets: sounds, music and languages. Each one is stored under its fingerprint (hash).
        stage = "Getting the asset list";
        Map<String, Object> indexInfo = Json.object(details.get("assetIndex"));
        Path indexFile = Folders.assets().resolve("indexes").resolve(indexInfo.get("id") + ".json");
        Map<String, Object> index = Json.object(Json.parse(downloader.fetchText((String) indexInfo.get("url"), indexFile)));
        for (Object entry : Json.object(index.get("objects")).values()) {
            Map<String, Object> asset = Json.object(entry);
            String hash = (String) asset.get("hash");
            String path = hash.substring(0, 2) + "/" + hash;
            Path file = Folders.assets().resolve("objects").resolve(path);
            jobs.put(file, new Downloader.Job(ASSETS_URL + path, file, hash, size(asset)));
        }

        // 5. The Java this version runs on
        stage = "Getting the Java list";
        String java = JavaRuntime.componentFor(details);
        for (Downloader.Job job : JavaRuntime.jobs(java, downloader)) jobs.put(job.file(), job);

        stage = "Downloading";
        downloader.downloadAll(new ArrayList<>(jobs.values()));
        JavaRuntime.markRunnable(java);
        stage = "Done";
    }

    /** Adds a download described the way Mojang describes them: with a url, sha1 and size. */
    private static void add(Map<Path, Downloader.Job> jobs, Map<String, Object> info, Path file) {
        jobs.put(file, new Downloader.Job((String) info.get("url"), file, (String) info.get("sha1"), size(info)));
    }

    private static long size(Map<String, Object> info) {
        return ((Number) info.get("size")).longValue();
    }
}
