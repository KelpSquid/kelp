package kelp;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The little badges next to a player's name: Dev (a wrench), Beta Tester, Early Player, GitHub Contributor, Discord
 * Member, and a cake on their Kelp birthday. Badges are handed out by Kelp's server, which proves who earned them;
 * Kelp keeps the latest list it got in the badges folder, one badge per line, and draws them in this order.
 */
public final class Badges {
    /** Every badge Kelp can draw, in the order they're shown. */
    public static final List<String> ALL = List.of("dev", "beta-tester", "early-player", "github-contributor", "discord-member", "birthday");

    private static final Map<String, BufferedImage> pictures = new ConcurrentHashMap<>();

    private Badges() {
    }

    static Path file(String accountId) {
        return Folders.home().resolve("badges").resolve(accountId.replaceAll("[^A-Za-z0-9_-]", "") + ".txt");
    }

    /** A player's badges, in the order they're shown (none until the server has handed some out). */
    public static List<String> of(String accountId) {
        Path file = file(accountId);
        if (!Files.exists(file)) return List.of();
        List<String> have = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String badge : ALL) {
                if (lines.stream().anyMatch(l -> l.strip().equals(badge))) have.add(badge);
            }
        } catch (IOException e) {
            return List.of();
        }
        return have;
    }

    /** Draws a player's badges in a row, each size by size, and says how wide the row came out. */
    public static int draw(Graphics2D g, String accountId, int x, int y, int size) {
        int at = x;
        for (String badge : of(accountId)) {
            BufferedImage picture = pictures.computeIfAbsent(badge, b -> Textures.load("badges/" + b + ".png"));
            g.drawImage(picture, at, y, size, size, null);
            at += size + size / 4;
        }
        return at - x;
    }
}
