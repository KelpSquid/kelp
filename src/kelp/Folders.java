package kelp;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * Where Kelp keeps its files: %APPDATA%\Kelp on Windows, ~/Library/Application Support/Kelp on Mac,
 * and ~/.local/share/kelp on Linux.
 */
public final class Folders {
    private Folders() {
    }

    public static Path home() {
        String custom = System.getProperty("kelp.home"); // lets tests use a different folder
        if (custom != null) return Path.of(custom);
        Path userHome = Path.of(System.getProperty("user.home"));
        return switch (Rules.osName()) {
            case "windows" -> {
                String appData = System.getenv("APPDATA");
                yield (appData != null ? Path.of(appData) : userHome).resolve("Kelp");
            }
            case "osx" -> userHome.resolve("Library").resolve("Application Support").resolve("Kelp");
            default -> {
                // Linux programs keep their data where XDG_DATA_HOME says, or ~/.local/share if it's not set
                String data = System.getenv("XDG_DATA_HOME");
                yield (data != null && !data.isBlank() ? Path.of(data) : userHome.resolve(".local").resolve("share")).resolve("kelp");
            }
        };
    }

    /**
     * The folder Kelp was started from: the one holding kelp.jar, or the project folder when running from
     * source (where the code is in out/). Either way, it's the folder around Kelp's code.
     */
    public static Path app() {
        try {
            return Path.of(Kelp.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (URISyntaxException | RuntimeException e) {
            return Path.of(".");
        }
    }

    /** One folder per Minecraft version, holding its game file and its details. */
    public static Path versions() {
        return home().resolve("versions");
    }

    /** Code the game needs, shared between versions. */
    public static Path libraries() {
        return home().resolve("libraries");
    }

    /** Where each instance keeps its saves, settings, screenshots and logs. */
    public static Path instances() {
        return home().resolve("instances");
    }

    /** The Squid mod loader: squid.jar and the libraries it needs. Squid's build.bat puts them here. */
    public static Path squid() {
        return home().resolve("squid");
    }

    /**
     * The Squid Kelp plays with: a packaged Kelp brings its own in a squid folder next to kelp.jar; otherwise it's the
     * one Squid's build.bat installs in Kelp's folder.
     */
    public static Path squidInUse() {
        Path packaged = app().resolve("squid");
        return java.nio.file.Files.exists(packaged.resolve("squid.jar")) ? packaged : squid();
    }

    /** The Javas Mojang made for running the game, one folder each. */
    public static Path runtimes() {
        return home().resolve("runtimes");
    }

    /** Sounds, languages and textures, shared between versions. */
    public static Path assets() {
        return home().resolve("assets");
    }

    /**
     * Where a relative path from a file someone else made (a modpack, a version's details) lands inside root, or an
     * IOException if it would land anywhere else. Windows also counts \Users\... and D:foo as "not absolute", so
     * those are turned away by name before Java ever resolves them.
     */
    public static Path inside(Path root, String relative) throws IOException {
        if (relative == null || relative.isBlank() || relative.indexOf(':') >= 0 || relative.indexOf('\0') >= 0
                || relative.startsWith("/") || relative.startsWith("\\")) {
            throw new IOException("Refused a path that could go outside " + root.getFileName() + ": " + relative);
        }
        Path base = root.toAbsolutePath().normalize();
        Path target;
        try {
            target = base.resolve(relative).normalize();
        } catch (java.nio.file.InvalidPathException e) {
            throw new IOException("Refused a path that isn't a real file name: " + relative);
        }
        if (target.equals(base) || !target.startsWith(base)) {
            throw new IOException("Refused a path that would go outside " + root.getFileName() + ": " + relative);
        }
        return target;
    }

    /** Whether a name from a file is fine as one folder name (a version id like "26.3" or "fabric-loader-0.19.5-26.3"). */
    public static boolean safeName(String name) {
        return name != null && name.matches("[A-Za-z0-9._+-]{1,100}") && !name.matches("\\.+");
    }
}
