package kelp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Puts a downloaded update in (see {@link Updates}). It runs from the new kelp.jar in the update folder, so the old
 * kelp.jar isn't in use: it waits for the old Kelp to close, copies the new kelp.jar over it, puts the new Squid in,
 * and opens Kelp again. What happened is written to update/update-log.txt.
 */
public final class Updater {
    private Updater() {
    }

    /** args: Kelp's folder, and the old Kelp's process id to wait for. */
    public static void main(String[] args) throws Exception {
        Path app = Path.of(args[0]);
        long oldKelp = Long.parseLong(args[1]);
        try {
            ProcessHandle old = ProcessHandle.of(oldKelp).orElse(null);
            if (old != null) old.onExit().get(30, TimeUnit.SECONDS);
            install(app);
            log(app, "Updated to " + Updates.current());
        } catch (Exception e) {
            log(app, "Couldn't update: " + e); // the old Kelp is still there and still works
        }
        String java = ProcessHandle.current().info().command().orElse("java");
        new ProcessBuilder(List.of(java, "-jar", app.resolve("kelp.jar").toString())).directory(app.toFile()).start();
    }

    /** Copies the update into Kelp's folder: kelp.jar, and Squid (whose old built-in parts and library are removed first). */
    static void install(Path app) throws IOException, InterruptedException {
        Path update = Updates.folder(app);
        if (!Files.exists(update.resolve("ready.txt"))) throw new IOException("there's no finished download to put in");
        // Windows can hold the old kelp.jar for a moment after Kelp closes, so try again a few times
        for (int attempt = 1; ; attempt++) {
            try {
                Files.copy(update.resolve("kelp.jar"), app.resolve("kelp.jar"), StandardCopyOption.REPLACE_EXISTING);
                break;
            } catch (IOException e) {
                if (attempt == 20) throw e;
                Thread.sleep(500);
            }
        }
        Path squid = app.resolve("squid");
        for (String part : new String[] {"builtin", "library"}) deleteFolder(squid.resolve(part));
        unzip(update.resolve("squid.zip"), squid);
        Files.delete(update.resolve("squid.zip"));
        Files.delete(update.resolve("ready.txt")); // the new kelp.jar here is cleaned up by the next Kelp
    }

    private static void unzip(Path zip, Path into) throws IOException {
        Files.createDirectories(into);
        Path root = into.toAbsolutePath().normalize();
        try (InputStream file = Files.newInputStream(zip); ZipInputStream in = new ZipInputStream(file)) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) throw new IOException("squid.zip has a file that tries to leave its folder");
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path p : walk.sorted((a, b) -> b.compareTo(a)).toList()) Files.delete(p);
        }
    }

    private static void log(Path app, String line) {
        try {
            Path folder = Updates.folder(app);
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("update-log.txt"), java.time.LocalDateTime.now() + " " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // nowhere to write it down: nothing else to do
        }
    }
}
