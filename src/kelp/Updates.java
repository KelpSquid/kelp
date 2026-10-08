package kelp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Kelp keeps itself (and the Squid that comes with it) up to date. When Kelp opens, it looks at update.json on Kelp's
 * newest GitHub release. If that's a newer version, it downloads the new kelp.jar and squid.zip into the update folder
 * next to kelp.jar, checking each against the fingerprint (sha256) update.json gives, and throwing away anything that
 * doesn't match. The title screen then offers to update: Kelp closes, {@link Updater} swaps the files in, and Kelp
 * opens again.
 *
 * Kelp run from its source code (not a packaged kelp.jar) never updates itself.
 */
public final class Updates {
    private Updates() {
    }

    /** Where the newest release's details are. Tests point this at a pretend server with -Dkelp.updates. */
    static String manifest = System.getProperty("kelp.updates",
            "https://github.com/KelpSquid/kelp/releases/latest/download/update.json");

    /** One file to download: where it is, its sha256 fingerprint, and its size in bytes. */
    public record Download(String url, String sha256, long size) {
    }

    /** A Kelp release: its version, a few words about what's new, and its two files. */
    public record Release(String version, String notes, Download kelp, Download squid) {
    }

    private static volatile String ready; // the version downloaded and waiting to be put in, or null

    /** This Kelp's version, from kelp.jar. Null when Kelp runs from its source code. */
    public static String current() {
        return Kelp.class.getPackage().getImplementationVersion();
    }

    /** The update folder, next to kelp.jar. */
    static Path folder(Path app) {
        return app.resolve("update");
    }

    /** The version that's downloaded and ready to put in, or null. */
    public static String ready() {
        return ready;
    }

    /**
     * Looks for a newer Kelp and downloads it, in the background. Does nothing when running from source code.
     * Problems (like no internet) are only written to the log: Kelp works fine without updating.
     */
    public static void checkInBackground() {
        String current = current();
        if (current == null) return;
        Thread thread = new Thread(() -> {
            try {
                // A download from last time may still be waiting; files left over from a finished update go
                Path folder = folder(Folders.app());
                Path readyFile = folder.resolve("ready.txt");
                if (Files.exists(readyFile) && newer(Files.readString(readyFile).strip(), current)) {
                    ready = Files.readString(readyFile).strip();
                } else {
                    for (String leftover : new String[] {"ready.txt", "kelp.jar", "squid.zip"}) Files.deleteIfExists(folder.resolve(leftover));
                }
                Release release = newest();
                if (release != null && newer(release.version(), current)) {
                    download(release, folder(Folders.app()));
                    ready = release.version();
                }
            } catch (Exception e) {
                System.err.println("Couldn't check for updates: " + e.getMessage());
            }
        }, "Kelp update check");
        thread.setDaemon(true);
        thread.start();
    }

    /** The newest release's details. */
    static Release newest() throws IOException, InterruptedException {
        HttpResponse<String> response = client().send(request(manifest), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("update.json answered with error " + response.statusCode());
        return parse(response.body());
    }

    static Release parse(String json) {
        Map<String, Object> release = Json.object(Json.parse(json));
        return new Release((String) release.get("version"), release.get("notes") instanceof String notes ? notes : "",
                download(Json.object(release.get("kelp"))), download(Json.object(release.get("squid"))));
    }

    private static Download download(Map<String, Object> file) {
        return new Download((String) file.get("url"), ((String) file.get("sha256")).toLowerCase(),
                ((Number) file.get("size")).longValue());
    }

    /** Whether version a comes after version b: 0.10 is newer than 0.9, and 1.0 newer than 0.12. */
    static boolean newer(String a, String b) {
        String[] x = a.split("[.-]");
        String[] y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? number(x[i]) : 0;
            long q = i < y.length ? number(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static long number(String part) {
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Downloads a release's files into the update folder: kelp.jar and squid.zip, plus ready.txt saying which version
     * they are. Each is checked against its fingerprint first, so a damaged or changed file never gets used.
     */
    static void download(Release release, Path folder) throws IOException, InterruptedException {
        Files.createDirectories(folder);
        Files.deleteIfExists(folder.resolve("ready.txt"));
        fetch(release.kelp(), folder.resolve("kelp.jar"));
        fetch(release.squid(), folder.resolve("squid.zip"));
        Files.writeString(folder.resolve("ready.txt"), release.version());
    }

    private static void fetch(Download file, Path target) throws IOException, InterruptedException {
        if (Files.exists(target) && Files.size(target) == file.size() && sha256(target).equals(file.sha256())) return; // already here
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            HttpResponse<Path> response = client().send(request(file.url()), HttpResponse.BodyHandlers.ofFile(part));
            if (response.statusCode() != 200) throw new IOException("the download answered with error " + response.statusCode());
            if (Files.size(part) != file.size() || !sha256(part).equals(file.sha256())) {
                throw new IOException(target.getFileName() + " arrived damaged, so it wasn't used");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int read; (read = in.read(buffer)) > 0; ) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Closes Kelp and puts the update in. Kelp's own kelp.jar can't be replaced while it's running, so the new kelp.jar
     * (in the update folder) does it: it waits for this Kelp to close, copies itself and Squid into place, and opens
     * Kelp again.
     */
    public static void installAndRestart() throws IOException {
        Path app = Folders.app();
        String java = ProcessHandle.current().info().command().orElse("java");
        new ProcessBuilder(List.of(java, "-cp", folder(app).resolve("kelp.jar").toString(), "kelp.Updater",
                app.toString(), String.valueOf(ProcessHandle.current().pid()))).start();
        System.exit(0);
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "KelpSquid/kelp/" + (current() == null ? "dev" : current()))
                .timeout(Duration.ofMinutes(5)).build();
    }
}
