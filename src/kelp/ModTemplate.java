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

    /** "MyCoolMod" becomes "My Cool Mod", the same way Squid names mods. */
    public static String spaced(String className) {
        return className.replace('_', ' ')
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ")
                .replaceAll("(?<=[A-Za-z])(?=[0-9])", " ").trim();
    }

    /** Makes the mod file in the folder and gives back where it is. A taken name gets a number: MyMod2.java. */
    public static Path create(Path modsFolder, String name) throws IOException {
        return create(modsFolder, name, null);
    }

    /** The same, starting from a starter mod (null for a blank one). */
    public static Path create(Path modsFolder, String name, ModStarters.Starter starter) throws IOException {
        Files.createDirectories(modsFolder);
        String base = className(name);
        String className = base;
        for (int n = 2; ModProject.taken(modsFolder, className); n++) className = base + n;
        String shownName = name.isBlank() ? "My Mod" : name.trim();
        Path file = modsFolder.resolve(className + ".java");
        String code = starter != null ? starter.codeFor(shownName, className) : text(shownName, className);
        Files.writeString(file, code.replace("\n", System.lineSeparator()));
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
                //   title("Hello!", "smaller text");       big text in the middle of the screen
                //   boost(1.2);  dash(2);                  shoots you up, or forward the way you look
                //   particles("heart", 10);                particles around you ("flame", "note"...)
                //
                // When things happen:
                //   onJoin(() -> { ... });                 when you join a world
                //   onKey("H", () -> { ... });             when you press H (you can change it in Controls)
                //   every(10, () -> { ... });              every 10 seconds
                //   onTick(() -> { ... });                 20 times a second
                //   onHurt(() -> { ... });  onDeath(() -> { ... });   when you get hurt, or die
                //
                // Settings players can change in the game's Mods screen (ask for them whenever you need them):
                //   setting("Play a sound", true)          ON or OFF
                //   setting("Zoom", 4, 1, 10)              a number from 1 to 10, starting at 4
                //   setting("Corner", "Top left", "Top left", "Top right")   one of a few choices
                //
                // Things to know: x(), y(), z(), health(), playerName(), random(1, 6), biome(), isNight(),
                //   holding() (what's in your hand), lookingAt() (the block or mob you look at),
                //   nearby("creeper", 16) (how many are near you)

                public class %2$s extends EasyMod {
                    void start() {
                        say("%1$s is working!");

                        onKey("H", () -> {
                            say("You pressed H! You're at " + x() + ", " + y() + ", " + z());
                            if (setting("Play a sound", true)) playSound("entity.experience_orb.pickup");
                        });
                    }
                }
                """.formatted(name.replace("\\", "").replace("\"", "'"), className);
    }
}
