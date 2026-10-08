package kelp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * What the title screen's Continue button goes back to: the world or server played last in an instance. Squid notes
 * which one it was each time you join (squid-last-played.txt in the instance's folder); without that (a Vanilla
 * instance, or before playing with this Squid), it's the world played most recently.
 */
public final class Continue {
    /** A world (id is its folder name) or a server (id is its address), and the name to show for it. */
    public record Target(boolean server, String id, String shown) {
    }

    private Continue() {
    }

    /** What to go back to in this instance, or null if there's nothing (no worlds yet, or a version too old). */
    public static Target find(Instance instance) {
        if (instance == null || !Launcher.canOpenWorld(instance.version().id())) return null;
        return find(instance.folder());
    }

    static Target find(Path folder) {
        Path note = folder.resolve("squid-last-played.txt");
        try {
            if (Files.isRegularFile(note) && Files.size(note) < 4096) {
                List<String> lines = Files.readAllLines(note, StandardCharsets.UTF_8);
                String kind = lines.isEmpty() ? "" : lines.get(0).strip();
                String id = lines.size() < 2 ? "" : lines.get(1).strip();
                // A server only when a parent hasn't turned multiplayer off, and only a plain address (letters,
                // numbers, dots, dashes, a :port), so nothing odd can sneak into the game's command line
                if (kind.equals("server") && ParentControls.multiplayerAllowed() && Servers.validAddress(id)) {
                    String name = lines.size() >= 3 && !lines.get(2).isBlank() ? lines.get(2).strip() : id;
                    return new Target(true, id, name);
                }
                // A world's folder name, which has to be one of this instance's worlds (and nothing like "..")
                if (kind.equals("world") && !id.isEmpty() && !id.contains("/") && !id.contains("\\") && !id.startsWith(".")
                        && Files.exists(folder.resolve("saves").resolve(id).resolve("level.dat"))) {
                    return new Target(false, id, id);
                }
            }
        } catch (IOException | RuntimeException e) {
            // the newest world, below
        }
        List<Worlds.World> worlds = Worlds.list(folder.resolve("saves"));
        return worlds.isEmpty() ? null : new Target(false, worlds.getFirst().name(), worlds.getFirst().name());
    }
}
