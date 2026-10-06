package kelp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Kelp's settings, saved in %APPDATA%\Kelp\settings.properties so they're remembered next time. */
public final class Settings {
    private static final Path FILE = Folders.home().resolve("settings.properties");
    private static final Properties VALUES = load();

    private Settings() {
    }

    /** Whether games start with the Squid mod loader. */
    public static boolean squid() {
        return Boolean.parseBoolean(VALUES.getProperty("squid", "false"));
    }

    public static void setSquid(boolean on) {
        VALUES.setProperty("squid", String.valueOf(on));
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
