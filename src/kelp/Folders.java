package kelp;

import java.nio.file.Path;

/** Where Kelp keeps its files: %APPDATA%\Kelp on Windows. */
public final class Folders {
    private Folders() {
    }

    public static Path home() {
        String custom = System.getProperty("kelp.home"); // lets tests use a different folder
        if (custom != null) return Path.of(custom);
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("Kelp");
    }

    /** One folder per Minecraft version, holding its game file and its details. */
    public static Path versions() {
        return home().resolve("versions");
    }

    /** Code the game needs, shared between versions. */
    public static Path libraries() {
        return home().resolve("libraries");
    }

    /** Sounds, languages and textures, shared between versions. */
    public static Path assets() {
        return home().resolve("assets");
    }
}
