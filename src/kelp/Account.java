package kelp;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Someone you can play as: a Microsoft account that owns Minecraft, or an offline name.
 *
 * @param id           the player's UUID without dashes, the same one Minecraft uses
 * @param refreshToken lets Kelp sign in again without asking (Microsoft accounts only, else null)
 * @param accessToken  what the game uses to prove who you are, or null for offline names
 * @param expiresAt    when the access token stops working, in milliseconds since 1970
 */
public record Account(String id, String name, boolean microsoft, String refreshToken, String accessToken, long expiresAt) {
    /** An offline name, with the same made-up ID Minecraft itself would give it. */
    public static Account offline(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new Account(uuid.toString().replace("-", ""), name, false, null, null, 0);
    }

    /** Whether the access token still works for a while, so the game won't get signed out in the middle. */
    public boolean signedIn(long now) {
        return accessToken != null && now < expiresAt - 10 * 60 * 1000;
    }
}
