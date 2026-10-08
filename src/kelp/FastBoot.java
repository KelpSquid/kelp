package kelp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Squid's fast boot, from Kelp's side. After a normal start, Squid leaves a patched Minecraft and the classpath to
 * start from in the instance's .squid-boot folder (see squid.FastBoot). If nothing that could change the mods' hooks
 * has changed since (Squid, its parts, the mods folder, the libraries), Kelp starts the game straight from there,
 * with Java's AOT cache if it has one, which makes Minecraft start several times faster.
 *
 * The AOT cache is made after the game closes, by a short training run in the background with no window, so nobody
 * waits for it. If a fast start finds the hooks don't match after all, Squid exits with {@link #RESTART} before
 * Minecraft loads, and Kelp starts it the normal way.
 */
final class FastBoot {
    /** Squid's exit code for "start me the normal way". */
    static final int RESTART = 86;

    private FastBoot() {
    }

    static Path folder(Path gameFolder) {
        return gameFolder.resolve(".squid-boot");
    }

    private static Properties read(Path file) {
        Properties p = new Properties();
        if (!Files.exists(file)) return null;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    /** The classpath to start from, if fast boot is ready and still matches everything; otherwise null. */
    static List<String> classpath(Path gameFolder) {
        Properties boot = read(folder(gameFolder).resolve("boot.properties"));
        if (boot == null || boot.getProperty("classpath") == null || boot.getProperty("fingerprint") == null) return null;
        List<String> classpath = List.of(boot.getProperty("classpath").split(java.io.File.pathSeparator));
        try {
            for (String entry : classpath) if (!Files.exists(Path.of(entry))) return null;
            return boot.getProperty("fingerprint").equals(fingerprint(gameFolder, Folders.squidInUse(), classpath)) ? classpath : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The AOT cache, if one was made for exactly this fast boot; otherwise null. */
    static Path aotCache(Path gameFolder) {
        Path dir = folder(gameFolder);
        Properties boot = read(dir.resolve("boot.properties"));
        Properties aot = read(dir.resolve("aot.properties"));
        if (boot == null || aot == null || !Files.exists(dir.resolve("squid.aot"))) return null;
        String fp = boot.getProperty("fingerprint");
        return fp != null && fp.equals(aot.getProperty("fingerprint")) ? dir.resolve("squid.aot") : null;
    }

    /** Makes the next start a normal one (Squid said the fast boot files don't match anymore). */
    static void forget(Path gameFolder) {
        try {
            Files.deleteIfExists(folder(gameFolder).resolve("boot.properties"));
        } catch (IOException ignored) {
            // the next start checks again anyway
        }
    }

    /**
     * After the game closes, makes the AOT cache for this fast boot if there isn't one yet: the same command with
     * Squid in training mode and Java told to save what it learns. It runs in the background with no window.
     */
    static void trainAfter(Process game, List<String> command, Path gameFolder) {
        game.onExit().thenRun(() -> {
            if (classpath(gameFolder) == null || aotCache(gameFolder) != null) return;
            Path dir = folder(gameFolder);
            Properties boot = read(dir.resolve("boot.properties"));
            if (boot == null) return;
            String fingerprint = boot.getProperty("fingerprint");
            Path part = dir.resolve("squid.aot.part");
            List<String> train = new ArrayList<>();
            train.add(command.get(0)); // java
            train.add("-XX:AOTCacheOutput=" + part);
            train.add("-Dsquid.train=true");
            for (String arg : command.subList(1, command.size())) {
                if (!arg.startsWith("-XX:AOTCache=")) train.add(arg);
            }
            try {
                Files.deleteIfExists(part);
                Process run = new ProcessBuilder(train).directory(gameFolder.toFile()).redirectErrorStream(true)
                        .redirectOutput(dir.resolve("train.log").toFile()).start();
                if (run.waitFor() == 0 && Files.exists(part)) {
                    Files.move(part, dir.resolve("squid.aot"), StandardCopyOption.REPLACE_EXISTING);
                    Properties aot = new Properties();
                    aot.setProperty("fingerprint", fingerprint);
                    try (Writer w = Files.newBufferedWriter(dir.resolve("aot.properties"), StandardCharsets.UTF_8)) {
                        aot.store(w, "Which fast boot squid.aot was made for");
                    }
                } else {
                    Files.deleteIfExists(part);
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Couldn't make the fast boot cache: " + e.getMessage());
            }
        });
    }

    // ---- The fingerprint (Squid works it out the same way, in squid.FastBoot) ----

    static String fingerprint(Path gameFolder, Path squidFolder, List<String> classpath) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String entry : classpath) lines.add("C " + describe(Path.of(entry), entry));
        for (Path p : listFiles(squidFolder, ".jar")) lines.add("S " + describe(p, p.getFileName().toString()));
        for (Path p : listFiles(squidFolder.resolve("builtin"), ".jar")) lines.add("B " + describe(p, p.getFileName().toString()));
        for (Path p : listFiles(gameFolder.resolve("mods"), "")) lines.add("M " + describe(p, p.getFileName().toString()));
        lines.sort(null);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String describe(Path p, String name) throws IOException {
        if (!Files.exists(p)) return name + " missing";
        return name + " " + Files.size(p) + " " + Files.getLastModifiedTime(p).toMillis();
    }

    private static List<Path> listFiles(Path folder, String ending) throws IOException {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(ending)).sorted().toList();
        }
    }
}
