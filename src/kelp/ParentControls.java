package kelp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Parent Controls: switches a parent locks with a PIN. Multiplayer and Chat use Minecraft's own launch switches
 * (--disableMultiplayer and --disableChat), so they hold even with mods. Voice chat is Squid's, and Squid is told. The PIN is kept salted and hashed, never as
 * itself. It's a friendly lock, not a vault: for a Microsoft child account, Microsoft's Family settings are the real one.
 */
public final class ParentControls {
    private ParentControls() {
    }

    public static boolean hasPin() {
        return Settings.get("parentPin", null) != null;
    }

    /** A PIN is 4 to 8 digits. */
    public static boolean validPin(String pin) {
        return pin != null && pin.matches("\\d{4,8}");
    }

    public static void setPin(String pin) {
        if (!validPin(pin)) throw new IllegalArgumentException("a PIN is 4 to 8 digits");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        Settings.put("parentPin", saltHex + ":" + hash(saltHex, pin));
    }

    public static boolean checkPin(String pin) {
        String saved = Settings.get("parentPin", null);
        if (saved == null || pin == null) return false;
        int colon = saved.indexOf(':');
        if (colon < 0) return false;
        return MessageDigest.isEqual(saved.substring(colon + 1).getBytes(StandardCharsets.US_ASCII),
                hash(saved.substring(0, colon), pin).getBytes(StandardCharsets.US_ASCII));
    }

    /** Takes Parent Controls off: no PIN, everything allowed again. */
    public static void remove() {
        Settings.put("parentPin", null);
        Settings.put("parentMultiplayer", null);
        Settings.put("parentChat", null);
        Settings.put("parentVoice", null);
    }

    public static boolean multiplayerAllowed() {
        return !hasPin() || !"false".equals(Settings.get("parentMultiplayer", "true"));
    }

    public static boolean chatAllowed() {
        return !hasPin() || !"false".equals(Settings.get("parentChat", "true"));
    }

    /** Squid's voice chat. Squid is told with -Dsquid.voice=off, and then never opens the microphone. */
    public static boolean voiceAllowed() {
        return !hasPin() || !"false".equals(Settings.get("parentVoice", "true"));
    }

    public static void setVoiceAllowed(boolean allowed) {
        Settings.put("parentVoice", String.valueOf(allowed));
    }

    public static void setMultiplayerAllowed(boolean allowed) {
        Settings.put("parentMultiplayer", String.valueOf(allowed));
    }

    public static void setChatAllowed(boolean allowed) {
        Settings.put("parentChat", String.valueOf(allowed));
    }

    /** What Kelp adds to Minecraft's own settings when it starts the game. */
    public static List<String> gameArguments() {
        List<String> args = new java.util.ArrayList<>();
        if (!multiplayerAllowed()) args.add("--disableMultiplayer");
        if (!chatAllowed()) args.add("--disableChat");
        return args;
    }

    private static String hash(String saltHex, String pin) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] out = (saltHex + ":" + pin).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 100_000; i++) out = sha.digest(out); // slow on purpose, so guessing every PIN takes a while
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
