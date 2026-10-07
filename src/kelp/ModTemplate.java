package kelp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Makes a new mod file from a name, like "My Cool Mod" becoming MyCoolMod.java.
 * It's a working mod right away, with every easy command listed at the top, so changing it is the whole lesson.
 */
public final class ModTemplate {
    private ModTemplate() {
    }

    /** A Java class name made from any name: "my cool mod!" becomes "MyCoolMod". */
    public static String className(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.split("[^A-Za-z0-9]+")) {
            if (word.isEmpty()) continue;
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        if (out.isEmpty()) return "MyMod";
        if (Character.isDigit(out.charAt(0))) out.insert(0, "Mod"); // Java names can't start with a number
        return out.toString();
    }

    /** Makes the mod file in the folder and gives back where it is. A taken name gets a number: MyMod2.java. */
    public static Path create(Path modsFolder, String name) throws IOException {
        Files.createDirectories(modsFolder);
        String base = className(name);
        String className = base;
        for (int n = 2; Files.exists(modsFolder.resolve(className + ".java"))
                || Files.exists(modsFolder.resolve(className + ".java.disabled")); n++) {
            className = base + n;
        }
        String shownName = name.isBlank() ? "My Mod" : name.trim();
        Path file = modsFolder.resolve(className + ".java");
        Files.writeString(file, text(shownName, className).replace("\n", System.lineSeparator()));
        return file;
    }

    static String text(String name, String className) {
        return """
                // %1$s: your own Squid mod!
                // Change anything below, save the file, then play. Squid does the rest.
                //
                // Commands you can use (inside start):
                //   say("Hello!");                         a chat message only you see
                //   showText("Hello!");                    text just above your hotbar
                //   playSound("entity.experience_orb.pickup");
                //   giveItem("diamond", 3);                gives you items (cheats need to be on)
                //   command("time set day");               runs a command, like typing /time set day
                //   splash("Hello!");                      changes the yellow text on the title screen
                //
                // When things happen:
                //   onJoin(() -> { ... });                 when you join a world
                //   onKey("G", () -> { ... });             when you press G (you can change it in Controls)
                //   every(10, () -> { ... });              every 10 seconds
                //   onTick(() -> { ... });                 20 times a second
                //
                // Things to know: x(), y(), z(), health(), playerName(), random(1, 6)

                public class %2$s extends EasyMod {
                    void start() {
                        say("%1$s is working!");

                        onKey("G", () -> {
                            say("You pressed G! You're at " + x() + ", " + y() + ", " + z());
                            playSound("entity.experience_orb.pickup");
                        });
                    }
                }
                """.formatted(name.replace("\\", "").replace("\"", "'"), className);
    }
}
