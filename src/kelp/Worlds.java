package kelp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * An instance's worlds (its saves folder), and bringing in worlds from elsewhere: a world folder from another
 * launcher, or a world someone downloaded as a .zip. A world is any folder with a level.dat in it.
 */
public final class Worlds {
    private Worlds() {
    }

    /** One world: its folder and when it was last played (the level.dat's time). */
    public record World(Path folder, long lastPlayed) {
        public String name() {
            return folder.getFileName().toString();
        }
    }

    public static Path saves(Instance instance) {
        return instance.folder().resolve("saves");
    }

    /** The instance's worlds, most recently played first. */
    public static List<World> list(Path saves) {
        List<World> worlds = new ArrayList<>();
        if (!Files.isDirectory(saves)) return worlds;
        try (Stream<Path> folders = Files.list(saves)) {
            for (Path folder : folders.filter(f -> Files.exists(f.resolve("level.dat"))).toList()) {
                worlds.add(new World(folder, Files.getLastModifiedTime(folder.resolve("level.dat")).toMillis()));
            }
        } catch (IOException e) {
            System.err.println("Couldn't list worlds in " + saves + ": " + e.getMessage());
        }
        worlds.sort(Comparator.comparingLong(World::lastPlayed).reversed());
        return worlds;
    }

    /**
     * Copies a world (a folder or a .zip) into the saves folder and gives back where it went. If it isn't a world
     * itself but has exactly one world inside it, like most downloaded zips, that one is used.
     * A world with the same name already there is kept: the new one gets a number, like "My World (2)".
     */
    public static Path importWorld(Path source, Path saves) throws IOException {
        Files.createDirectories(saves);
        if (Files.isRegularFile(source) && source.getFileName().toString().toLowerCase().endsWith(".zip")) {
            return importZip(source, saves);
        }
        if (!Files.isDirectory(source)) throw new IOException("Pick a world folder, or a world saved as a .zip.");
        Path world = findWorld(source);
        if (saves.toAbsolutePath().normalize().startsWith(world.toAbsolutePath().normalize())) {
            throw new IOException("That's the instance's own saves folder. Pick one world inside it instead.");
        }
        Path target = freeName(saves, world.getFileName().toString());
        copy(world, target);
        return target;
    }

    /** The folder with the level.dat: the folder itself, or its only world one level down. */
    static Path findWorld(Path folder) throws IOException {
        if (Files.exists(folder.resolve("level.dat"))) return folder;
        List<Path> worlds;
        try (Stream<Path> inside = Files.list(folder)) {
            worlds = inside.filter(f -> Files.exists(f.resolve("level.dat"))).toList();
        }
        if (worlds.size() == 1) return worlds.get(0);
        if (worlds.size() > 1) throw new IOException("That folder has " + worlds.size() + " worlds in it. Pick just one of them.");
        throw new IOException("That isn't a Minecraft world: there's no level.dat in it.");
    }

    private static Path importZip(Path zip, Path saves) throws IOException {
        // Unpack into a temporary folder in saves first, then move the world out, so a broken zip leaves nothing behind
        Path unpack = Files.createTempDirectory(saves, ".importing-");
        try {
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    Path out = unpack.resolve(entry.getName()).normalize();
                    // A zip can name files like "../../something"; those would land outside the folder, so they're refused
                    if (!out.startsWith(unpack)) throw new IOException("That zip has a file that would go outside the world, so it wasn't imported.");
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        copyStream(in, out);
                    }
                }
            } catch (IllegalArgumentException badZip) {
                throw new IOException("That zip couldn't be read.");
            }
            Path world = findWorld(unpack);
            String name = world.equals(unpack) ? zip.getFileName().toString().replaceAll("(?i)\\.zip$", "") : world.getFileName().toString();
            Path target = freeName(saves, name);
            Files.move(world, target);
            return target;
        } finally {
            if (Files.exists(unpack)) deleteFolder(unpack);
        }
    }

    private static void copyStream(InputStream in, Path out) throws IOException {
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
    }

    /** "My World", or "My World (2)", "My World (3)"... whichever isn't taken. */
    static Path freeName(Path saves, String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) clean = "World";
        Path target = saves.resolve(clean);
        for (int n = 2; Files.exists(target); n++) target = saves.resolve(clean + " (" + n + ")");
        return target;
    }

    private static void copy(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().equals("session.lock")) { // the game's "in use" marker; never needed in a copy
                    Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteFolder(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
