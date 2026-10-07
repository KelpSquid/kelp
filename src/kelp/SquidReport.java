package kelp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What Squid says about a game it started, read from the squid-report.json it writes in the game's folder.
 *
 * @param status   "loading" while mods start, "running" once Minecraft starts, or "failed"
 * @param modCount how many mods Squid loaded
 * @param mod      the mod that broke, or null
 * @param error    what went wrong, or null
 * @param skipped  mods Squid didn't load, and why
 * @param problems names of mods Squid had to partly turn off while the game was running
 */
public record SquidReport(String status, int modCount, String mod, String error, List<Skipped> skipped,
                          List<String> problems) {
    /** A mod Squid didn't load, and why, like "it was made for Minecraft 26.2, not 26.3." */
    public record Skipped(String mod, String reason) {
    }

    public static Path file(Path gameFolder) {
        return gameFolder.resolve("squid-report.json");
    }

    /** Squid's latest report for the game in this folder, or null if there isn't one (yet). */
    public static SquidReport read(Path gameFolder) {
        try {
            Map<String, Object> json = Json.object(Json.parse(Files.readString(file(gameFolder))));
            List<Skipped> skipped = new ArrayList<>();
            if (json.get("skipped") != null) { // older Squids don't write this or "problems"
                for (Object s : Json.array(json.get("skipped"))) {
                    Map<String, Object> one = Json.object(s);
                    skipped.add(new Skipped((String) one.get("mod"), (String) one.get("reason")));
                }
            }
            List<String> problems = new ArrayList<>();
            if (json.get("problems") != null) {
                for (Object problem : Json.array(json.get("problems"))) problems.add((String) Json.object(problem).get("mod"));
            }
            return new SquidReport((String) json.get("status"), Json.array(json.get("mods")).size(),
                    (String) json.get("mod"), (String) json.get("error"), List.copyOf(skipped), List.copyOf(problems));
        } catch (IOException | RuntimeException e) {
            return null; // not written yet, or caught halfway through being written
        }
    }
}
