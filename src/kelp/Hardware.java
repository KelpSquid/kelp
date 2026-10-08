package kelp;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What the computer has, for picking settings that run well on it. */
final class Hardware {
    // Minecraft writes the graphics chip it used in its log: "Using graphics device: NVIDIA GeForce RTX 5050 Laptop GPU/..."
    private static final Pattern DEVICE = Pattern.compile("(?:Using graphics device: |GL info: )([^/\\r\\n]+)");
    // Graphics built into the processor, which share the computer's memory and run Minecraft slowly
    private static final Pattern BUILT_IN = Pattern.compile(
            "(?i)intel|\\buhd\\b|iris|hd graphics|radeon\\(tm\\) (?:vega \\d+ )?graphics|radeon graphics|vega \\d+ graphics|mali|adreno|llvmpipe|basic render");

    private Hardware() {
    }

    /** The graphics chip the game used the last time this instance played, or null if it hasn't said. */
    static String graphicsDevice(Instance instance) {
        Path log = instance.folder().resolve("logs").resolve("latest.log");
        try {
            if (!Files.exists(log)) return null;
            byte[] bytes = Files.readAllBytes(log);
            Matcher m = DEVICE.matcher(new String(bytes, 0, Math.min(bytes.length, 4_000_000), StandardCharsets.UTF_8));
            return m.find() ? m.group(1).strip() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** All the computer's memory, in gigabytes (0 if Java can't tell). */
    static double memoryGb() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getTotalMemorySize() / (1024.0 * 1024 * 1024);
        }
        return 0;
    }

    /**
     * Whether this is a weak computer for Minecraft: graphics built into the processor, or (when the game hasn't said
     * which graphics it used yet) 8 GB of memory or less, which usually comes with them.
     */
    static boolean weak(String graphicsDevice, double memoryGb) {
        if (graphicsDevice != null) {
            if (graphicsDevice.matches("(?i).*\\barc\\b.*")) return false; // Intel's own graphics cards
            return BUILT_IN.matcher(graphicsDevice).find();
        }
        return memoryGb > 0 && memoryGb <= 8.5;
    }
}
