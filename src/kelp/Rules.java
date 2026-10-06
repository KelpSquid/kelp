package kelp;

import java.util.Map;

/** Mojang's "rules", which say that some files are only for some operating systems. */
public final class Rules {
    private Rules() {
    }

    /** "windows", "osx" or "linux", the names Mojang uses. */
    public static String osName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    /** No rules means always allowed. Otherwise the last rule that matches this computer decides. */
    public static boolean allowed(Object rules) {
        if (rules == null) return true;
        boolean allowed = false;
        for (Object entry : Json.array(rules)) {
            Map<String, Object> rule = Json.object(entry);
            if (matches(rule)) allowed = "allow".equals(rule.get("action"));
        }
        return allowed;
    }

    private static boolean matches(Map<String, Object> rule) {
        Map<String, Object> os = Json.object(rule.get("os"));
        if (os != null) {
            if (os.get("name") != null && !os.get("name").equals(osName())) return false;
            if (os.get("arch") != null && !os.get("arch").equals(System.getProperty("os.arch"))) return false;
        }
        // "features" are things like demo mode, used by the game's settings. Kelp turns none of them on.
        return rule.get("features") == null;
    }
}
