package kelp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deletes everything Kelp and Squid keep about one player on this computer: their sign-in, their emblem and badges, their Squid
 * Count, and the skin, cape and effects they picked in Squid's wardrobe. Worlds, mods, screenshots and the skin and
 * cape pictures themselves stay, since they're things made in the game rather than about the player.
 */
public final class PlayerData {
    private PlayerData() {
    }

    public static void delete(Account account) throws IOException {
        String id = account.id();
        String plain = id.replace("-", "");
        Files.deleteIfExists(Emblem.file(id));
        Files.deleteIfExists(Emblem.file(plain));
        Files.deleteIfExists(Badges.file(id));
        Emblem.forget(id);
        Emblem.forget(plain);
        removePlayer(SquidCount.file(), true, id, plain);
        removePlayer(Folders.home().resolve("squid-skins.json"), false, id, plain);
        Accounts.remove(account);
    }

    /** Takes a player out of one of Squid's JSON files (under "players", or at the top). */
    @SuppressWarnings("unchecked")
    static void removePlayer(Path file, boolean underPlayers, String... ids) throws IOException {
        if (!Files.exists(file)) return;
        Object parsed;
        try {
            parsed = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return; // not something we can read: leave it alone rather than break it
        }
        Map<String, Object> all = Json.object(parsed);
        if (all == null) return;
        all = new LinkedHashMap<>(all);
        Map<String, Object> players = all;
        if (underPlayers) {
            Map<String, Object> inside = Json.object(all.get("players"));
            if (inside == null) return;
            players = new LinkedHashMap<>(inside);
            all.put("players", players);
        }
        boolean changed = false;
        for (String id : ids) changed |= players.remove(id) != null;
        if (!changed) return;
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.writeString(part, write(all, "") + "\n", StandardCharsets.UTF_8);
        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** JSON text for maps, lists, text, numbers, true/false and null, indented by 4. */
    static String write(Object value, String indent) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) return value.toString();
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
        }
        String inner = indent + "    ";
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{}";
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.append(first ? "\n" : ",\n").append(inner).append(quote(String.valueOf(entry.getKey()))).append(": ")
                        .append(write(entry.getValue(), inner));
                first = false;
            }
            return out.append("\n").append(indent).append("}").toString();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "[]";
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) out.append(i == 0 ? "" : ", ").append(write(list.get(i), inner));
            return out.append("]").toString();
        }
        return quote(value.toString());
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
