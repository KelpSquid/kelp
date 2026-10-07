package kelp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The Squid Count: points Squid gives for every advancement earned while playing with it, like gamerscore.
 * Squid keeps everyone's count in squid-count.json in Kelp's folder; Kelp only reads it, to show it.
 */
public final class SquidCount {
    private SquidCount() {
    }

    public static Path file() {
        return Folders.home().resolve("squid-count.json");
    }

    /** This player's points (by their ID without dashes), or 0 if they haven't earned any yet. */
    public static int points(String playerId) {
        try {
            if (!Files.exists(file())) return 0;
            Map<String, Object> players = Json.object(Json.object(Json.parse(Files.readString(file()))).get("players"));
            Map<String, Object> player = players == null ? null : Json.object(players.get(playerId));
            return player != null && player.get("points") instanceof Double d ? d.intValue() : 0;
        } catch (IOException | RuntimeException e) {
            return 0; // not written yet, or caught halfway through being written
        }
    }
}
