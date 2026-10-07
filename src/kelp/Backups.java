package kelp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Backs worlds up while they're played, like Legacy Console Edition's autosave: every 15 minutes, every world that
 * changed since its last backup is zipped into Kelp's backups folder, and once more when the game closes. The newest
 * 12 backups of each world are kept. Restoring one adds it as a new world next to the original, which is never touched.
 */
public final class Backups {
    private Backups() {
    }

    static final long EVERY_MINUTES = 15;
    static final int KEEP = 12;
    private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    /** One backup: its zip, and when it was made (from its name). */
    public record Backup(Path file, long time) {
    }

    /** Kelp/backups/<instance>/<world>/ */
    static Path folder(Instance instance, String world) {
        return Folders.home().resolve("backups").resolve(instance.id()).resolve(world);
    }

    /** A world's backups, newest first. */
    public static List<Backup> list(Instance instance, String world) {
        List<Backup> backups = new ArrayList<>();
        Path folder = folder(instance, world);
        if (!Files.isDirectory(folder)) return backups;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".zip")).toList()) {
                try {
                    String stamp = file.getFileName().toString().replace(".zip", "");
                    backups.add(new Backup(file, Instant.from(FILE_NAME.parse(stamp)).toEpochMilli()));
                } catch (RuntimeException notOurs) {
                    // a zip someone put here by hand: not a backup Kelp made
                }
            }
        } catch (IOException e) {
            System.err.println("Couldn't list backups in " + folder + ": " + e.getMessage());
        }
        backups.sort(Comparator.comparingLong(Backup::time).reversed());
        return backups;
    }

    /** Zips one world into its backups folder now, and keeps only the newest ones. */
    public static Backup backUp(Instance instance, Path world) throws IOException {
        Path folder = folder(instance, world.getFileName().toString());
        Files.createDirectories(folder);
        long now = System.currentTimeMillis();
        Path zip = folder.resolve(FILE_NAME.format(Instant.ofEpochMilli(now)) + ".zip");
        Path part = folder.resolve(zip.getFileName() + ".part");
        try (OutputStream file = Files.newOutputStream(part); ZipOutputStream out = new ZipOutputStream(file);
             Stream<Path> walk = Files.walk(world)) {
            String top = world.getFileName().toString() + "/";
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                if (p.getFileName().toString().equals("session.lock")) continue; // the game holds this one open
                byte[] bytes;
                try {
                    bytes = Files.readAllBytes(p);
                } catch (IOException busy) {
                    continue; // a file the game is writing right now; the next backup gets it
                }
                out.putNextEntry(new ZipEntry(top + world.relativize(p).toString().replace('\\', '/')));
                out.write(bytes);
                out.closeEntry();
            }
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        Files.move(part, zip, StandardCopyOption.REPLACE_EXISTING);
        // Its time is when it started: anything the game changed while it was being zipped goes in the next one
        Files.setLastModifiedTime(zip, java.nio.file.attribute.FileTime.fromMillis(now));
        List<Backup> all = list(instance, world.getFileName().toString());
        for (Backup old : all.subList(Math.min(KEEP, all.size()), all.size())) Files.deleteIfExists(old.file());
        return new Backup(zip, now);
    }

    /** Backs up every world that changed since its newest backup. Gives back how many were backed up. */
    public static int backUpChanged(Instance instance) {
        int count = 0;
        for (Worlds.World world : Worlds.list(Worlds.saves(instance))) {
            try {
                List<Backup> backups = list(instance, world.name());
                long last = backups.isEmpty() ? 0 : Files.getLastModifiedTime(backups.get(0).file()).toMillis();
                if (newestChange(world.folder()) > last) {
                    backUp(instance, world.folder());
                    count++;
                }
            } catch (IOException e) {
                System.err.println("Couldn't back up " + world.name() + ": " + e.getMessage());
            }
        }
        return count;
    }

    /** When anything in the world last changed. */
    static long newestChange(Path world) throws IOException {
        try (Stream<Path> walk = Files.walk(world)) {
            return walk.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals("session.lock"))
                    .mapToLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0;
                        }
                    }).max().orElse(0);
        }
    }

    /**
     * Brings a backup back as a new world called "<world> (backup <when>)", next to the original, which stays as it is.
     */
    public static Path restore(Instance instance, Backup backup) throws IOException {
        Path saves = Worlds.saves(instance);
        Path restored = Worlds.importWorld(backup.file(), saves);
        String world = backup.file().getParent().getFileName().toString();
        String when = DateTimeFormatter.ofPattern("MMM d HH-mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(backup.time()));
        Path named = Worlds.freeName(saves, world + " (backup " + when + ")");
        Files.move(restored, named);
        return named;
    }

    /**
     * Keeps an eye on a running game: backs its changed worlds up every 15 minutes, and once more when it closes.
     * Only when auto-backups are on in Options.
     */
    public static void watch(Instance instance, Process game) {
        if (!Settings.autoBackup()) return;
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "world backups");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY); // the game comes first
            return thread;
        });
        timer.scheduleAtFixedRate(() -> backUpChanged(instance), EVERY_MINUTES, EVERY_MINUTES, TimeUnit.MINUTES);
        game.onExit().thenRun(() -> timer.execute(() -> {
            backUpChanged(instance);
            timer.shutdown();
        }));
    }
}
