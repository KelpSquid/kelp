package kelp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * What Squid says about a game it started, read from the squid-report.json it writes in the game's folder.
 *
 * @param status   "loading" while mods start, "running" once Minecraft starts, or "failed"
 * @param modCount how many mods Squid loaded
 * @param mod      the mod that broke, or null
 * @param error    what went wrong, or null
 */
public record SquidReport(String status, int modCount, String mod, String error) {
    public static Path file(String versionId) {
        return Folders.instances().resolve(versionId).resolve("squid-report.json");
    }

    /** Squid's latest report for this version, or null if there isn't one (yet). */
    public static SquidReport read(String versionId) {
        try {
            Map<String, Object> json = Json.object(Json.parse(Files.readString(file(versionId))));
            return new SquidReport((String) json.get("status"), Json.array(json.get("mods")).size(),
                    (String) json.get("mod"), (String) json.get("error"));
        } catch (IOException | RuntimeException e) {
            return null; // not written yet, or caught halfway through being written
        }
    }
}
