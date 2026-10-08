package kelp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /** Squid's own achievements, for making things (Squid's Count keeps them as "squid:" plus these). */
    static final List<String> ACHIEVEMENTS = List.of("paint", "sound", "record", "mod", "emote", "song", "karaoke");

    /** How many of Squid's own achievements this player has (see {@link #ACHIEVEMENTS}). */
    public static int achievements(String playerId) {
        try {
            if (!Files.exists(file())) return 0;
            Map<String, Object> players = Json.object(Json.object(Json.parse(Files.readString(file()))).get("players"));
            Map<String, Object> player = players == null ? null : Json.object(players.get(playerId));
            if (player == null || player.get("earned") == null) return 0;
            int found = 0;
            for (Object id : Json.array(player.get("earned"))) {
                String text = String.valueOf(id);
                if (text.startsWith("squid:") && ACHIEVEMENTS.contains(text.substring("squid:".length()))) found++;
            }
            return found;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
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
