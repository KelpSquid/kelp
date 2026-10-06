package kelp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mojang's "rules", which say that some files are only for some operating systems. */
public final class Rules {
    private static String osVersion; // worked out once, the first time it's needed

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
            // Old versions match the version with a pattern, like "^10\\." for Windows 10
            if (os.get("version") != null && !Pattern.compile((String) os.get("version")).matcher(osVersion()).find()) {
                return false;
            }
            // New versions give a range instead, like "at least 10.0.17134"
            Map<String, Object> range = Json.object(os.get("versionRange"));
            if (range != null) {
                if (range.get("min") != null && compareVersions(osVersion(), (String) range.get("min")) < 0) return false;
                if (range.get("max") != null && compareVersions(osVersion(), (String) range.get("max")) > 0) return false;
            }
        }
        // "features" are things like demo mode, used by the game's settings. Kelp turns none of them on.
        return rule.get("features") == null;
    }

    /**
     * The operating system's version, like "10.0.26200". On Windows, Java only says "10.0" (even on Windows 11),
     * so Kelp asks Windows itself with the "ver" command.
     */
    static String osVersion() {
        if (osVersion != null) return osVersion;
        osVersion = System.getProperty("os.version");
        if (osName().equals("windows")) {
            try {
                Process ver = new ProcessBuilder("cmd", "/c", "ver").redirectErrorStream(true).start();
                try (BufferedReader out = new BufferedReader(new InputStreamReader(ver.getInputStream()))) {
                    // It prints something like: Microsoft Windows [Version 10.0.26200.9457]
                    Matcher m = Pattern.compile("(\\d+\\.\\d+\\.\\d+)").matcher(out.lines().reduce("", String::concat));
                    if (m.find()) osVersion = m.group(1);
                }
            } catch (IOException e) {
                // keep Java's answer
            }
        }
        return osVersion;
    }

    /** Compares versions like "10.0.26200" and "10.0.17134" number by number. Missing numbers count as 0. */
    private static int compareVersions(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int left = i < x.length ? Integer.parseInt(x[i]) : 0;
            int right = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (left != right) return Integer.compare(left, right);
        }
        return 0;
    }
}
