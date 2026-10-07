package kelp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Kelp's settings, saved in %APPDATA%\Kelp\settings.properties so they're remembered next time. */
public final class Settings {
    /** The memory choices, in GB. 0 means "let Mojang decide", which is 2-4 GB for new versions. */
    public static final int[] MEMORY_CHOICES = {0, 2, 4, 6, 8, 12, 16};

    private static final Path FILE = Folders.home().resolve("settings.properties");
    private static final Properties VALUES = load();

    private Settings() {
    }

    /** The name Kelp played as before it had accounts. Accounts turns it into an offline account the first time. */
    static String playerName() {
        return VALUES.getProperty("playerName", "Player");
    }

    /** Minecraft's rule for names: 3 to 16 letters, numbers or underscores. */
    public static boolean isValidName(String name) {
        return name.matches("[A-Za-z0-9_]{3,16}");
    }

    /** How much memory the game gets, in GB, or 0 to use Mojang's choice. */
    public static int memoryGb() {
        try {
            return Integer.parseInt(VALUES.getProperty("memoryGb", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void setMemoryGb(int gb) {
        set("memoryGb", String.valueOf(gb));
    }

    /** The id of the instance played last, which the title screen's Play button starts. Null if none yet. */
    public static String lastInstance() {
        return VALUES.getProperty("lastInstance");
    }

    public static void setLastInstance(String id) {
        set("lastInstance", id);
    }

    private static void set(String key, String value) {
        VALUES.setProperty(key, value);
        save();
    }

    private static Properties load() {
        Properties values = new Properties();
        if (Files.exists(FILE)) {
            try (Reader in = Files.newBufferedReader(FILE)) {
                values.load(in);
            } catch (IOException e) {
                System.err.println("Couldn't read " + FILE + ", using default settings: " + e.getMessage());
            }
        }
        return values;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer out = Files.newBufferedWriter(FILE)) {
                VALUES.store(out, "Kelp settings");
            }
        } catch (IOException e) {
            System.err.println("Couldn't save " + FILE + ": " + e.getMessage());
        }
    }
}
