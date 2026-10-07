package kelp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Totals from Minecraft's own statistics, added up over every world in an instance. Minecraft keeps them in each
 * world's stats folder, one file per player who played there.
 *
 * @param ticksPlayed  time spent in worlds, in game ticks (20 a second)
 * @param walkedCm     how far players walked, sprinted, swam and flew, in centimeters
 */
public record GameStats(long ticksPlayed, long blocksMined, long itemsCrafted, long mobsKilled, long deaths, long jumps,
                        long walkedCm) {
    static final GameStats NONE = new GameStats(0, 0, 0, 0, 0, 0, 0);

    /** Every world's statistics in an instance, added up. */
    public static GameStats read(Instance instance) {
        GameStats total = NONE;
        for (Worlds.World world : Worlds.list(Worlds.saves(instance))) {
            Path stats = world.folder().resolve("stats");
            if (!Files.isDirectory(stats)) continue;
            try (Stream<Path> files = Files.list(stats)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    try {
                        total = total.plus(parse(Files.readString(file)));
                    } catch (IOException | RuntimeException e) {
                        // a broken stats file: the others still count
                    }
                }
            } catch (IOException e) {
                // can't look in this world: the others still count
            }
        }
        return total;
    }

    /** One stats file (Minecraft's JSON: {"stats": {"minecraft:custom": {...}, "minecraft:mined": {...}, ...}}). */
    static GameStats parse(String json) {
        Map<String, Object> stats = Json.object(Json.object(Json.parse(json)).get("stats"));
        if (stats == null) return NONE;
        Map<String, Object> custom = Json.object(stats.get("minecraft:custom"));
        long walked = 0;
        for (String kind : new String[] {"walk_one_cm", "sprint_one_cm", "crouch_one_cm", "swim_one_cm", "walk_on_water_one_cm",
                "walk_under_water_one_cm", "fly_one_cm", "aviate_one_cm", "climb_one_cm", "fall_one_cm"}) {
            walked += number(custom, "minecraft:" + kind);
        }
        return new GameStats(number(custom, "minecraft:play_time"), sum(stats.get("minecraft:mined")), sum(stats.get("minecraft:crafted")),
                number(custom, "minecraft:mob_kills"), number(custom, "minecraft:deaths"), number(custom, "minecraft:jump"), walked);
    }

    GameStats plus(GameStats other) {
        return new GameStats(ticksPlayed + other.ticksPlayed, blocksMined + other.blocksMined, itemsCrafted + other.itemsCrafted,
                mobsKilled + other.mobsKilled, deaths + other.deaths, jumps + other.jumps, walkedCm + other.walkedCm);
    }

    private static long number(Map<String, Object> values, String key) {
        return values != null && values.get(key) instanceof Number n ? n.longValue() : 0;
    }

    private static long sum(Object group) {
        Map<String, Object> values = Json.object(group);
        if (values == null) return 0;
        long total = 0;
        for (Object value : values.values()) {
            if (value instanceof Number n) total += n.longValue();
        }
        return total;
    }

    /** A length of time like "3h 25m", or "12m". */
    public static String duration(long millis) {
        long minutes = millis / 60_000;
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }
}
