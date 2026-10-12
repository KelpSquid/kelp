package kelp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Parent Controls: switches a parent locks with a PIN. Multiplayer and Chat use Minecraft's own launch switches
 * (--disableMultiplayer and --disableChat). Voice chat is Squid's, and Squid is told. The PIN is kept salted and hashed,
 * never as itself.
 *
 * Everything is kept twice: in settings.properties and in Java's per-user preferences (the registry on Windows). Either
 * copy brings the other back, and if they disagree the preferences win, so deleting or editing the settings file doesn't
 * turn the controls off. A switch that's missing while a PIN is set counts as off: it fails closed.
 *
 * It's a friendly lock, not a vault: mods, or someone who can edit the registry, can get around it. For a Microsoft
 * child account, Microsoft's Family settings are the real one.
 */
public final class ParentControls {
    private static final String[] SWITCHES = {"Multiplayer", "Chat", "Voice"};

    private ParentControls() {
    }

    /** The second copy, one per Kelp folder (so tests with their own folder never touch the real one). */
    private static Preferences backup() {
        try {
            byte[] id = MessageDigest.getInstance("SHA-256")
                    .digest(Folders.home().toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
            return Preferences.userRoot().node("kelp/parent-controls/" + HexFormat.of().formatHex(id, 0, 8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String backupGet(String key) {
        try {
            return backup().get(key, null);
        } catch (RuntimeException e) {
            return null; // preferences unavailable: the settings copy still counts
        }
    }

    private static void backupPut(String key, String value) {
        try {
            Preferences prefs = backup();
            if (value == null) prefs.remove(key);
            else prefs.put(key, value);
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            System.err.println("Couldn't save Parent Controls' second copy: " + e.getMessage());
        }
    }

    /** Puts any copy that went missing back, from the other one. Preferences win when both have a PIN. */
    private static String pinRecord() {
        String kept = backupGet("pin");
        String file = Settings.get("parentPin", null);
        if (kept != null && kept.indexOf(':') > 0) {
            if (!kept.equals(file)) { // the settings file was deleted or edited: put it back the way the parent left it
                Settings.put("parentPin", kept);
                for (String name : SWITCHES) Settings.put("parent" + name, backupGet(name.toLowerCase()));
            }
            return kept;
        }
        if (file != null && file.indexOf(':') > 0) {
            // Set before there was a second copy (or the preferences were cleared): copy it over. Back then a missing
            // switch meant allowed, so it's written out as allowed now that missing means off.
            backupPut("pin", file);
            for (String name : SWITCHES) {
                String value = Settings.get("parent" + name, "true");
                Settings.put("parent" + name, value);
                backupPut(name.toLowerCase(), value);
            }
            return file;
        }
        return null;
    }

    public static boolean hasPin() {
        return pinRecord() != null;
    }

    /** Whether a switch is on (allowed). With a PIN set, only an explicit "true" in the copy that counts allows it. */
    private static boolean allowed(String name) {
        if (pinRecord() == null) return true;
        String kept = backupGet("pin") != null ? backupGet(name.toLowerCase()) : Settings.get("parent" + name, null);
        return "true".equals(kept);
    }

    private static void setAllowed(String name, boolean allowed) {
        Settings.put("parent" + name, String.valueOf(allowed));
        backupPut(name.toLowerCase(), String.valueOf(allowed));
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
        boolean first = pinRecord() == null;
        String record = saltHex + ":" + hash(saltHex, pin);
        Settings.put("parentPin", record);
        backupPut("pin", record);
        if (first) for (String name : SWITCHES) setAllowed(name, true); // nothing is locked until the parent picks
    }

    public static boolean checkPin(String pin) {
        String saved = pinRecord();
        if (saved == null || pin == null) return false;
        int colon = saved.indexOf(':');
        if (colon < 0) return false;
        return MessageDigest.isEqual(saved.substring(colon + 1).getBytes(StandardCharsets.US_ASCII),
                hash(saved.substring(0, colon), pin).getBytes(StandardCharsets.US_ASCII));
    }

    /** Takes Parent Controls off: no PIN, everything allowed again. */
    public static void remove() {
        Settings.put("parentPin", null);
        backupPut("pin", null);
        for (String name : SWITCHES) {
            Settings.put("parent" + name, null);
            backupPut(name.toLowerCase(), null);
        }
    }

    public static boolean multiplayerAllowed() {
        return allowed("Multiplayer");
    }

    public static boolean chatAllowed() {
        return allowed("Chat");
    }

    /** Squid's voice chat. Squid is told with -Dsquid.voice=off, and then never opens the microphone. */
    public static boolean voiceAllowed() {
        return allowed("Voice");
    }

    public static void setVoiceAllowed(boolean allowed) {
        setAllowed("Voice", allowed);
    }

    public static void setMultiplayerAllowed(boolean allowed) {
        setAllowed("Multiplayer", allowed);
    }

    public static void setChatAllowed(boolean allowed) {
        setAllowed("Chat", allowed);
    }

    /** Removes the second copy for this Kelp folder (tests clean up with this). */
    static void forgetBackup() {
        try {
            backup().removeNode();
        } catch (BackingStoreException | RuntimeException e) {
            // nothing to clean up
        }
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
