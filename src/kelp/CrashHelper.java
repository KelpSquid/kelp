package kelp;

import static kelp.Lang.t;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Says why the game crashed, in plain words, and what to do about it. It reads what the game printed (kelp-output.log)
 * and Minecraft's newest crash report, and looks for the usual reasons: running out of memory, a mod missing another
 * mod it needs, a mod made for a different Minecraft version, or the graphics driver.
 */
public final class CrashHelper {
    private CrashHelper() {
    }

    /**
     * What went wrong and what to try. culprit is the mod it seems to be (one of the instance's mods), or null.
     * details is the part of the log that matters, for copying when asking for help.
     */
    public record Diagnosis(String what, String fix, InstalledMod culprit, String details) {
    }

    /** Reads the game's log and newest crash report (made after startedAt, in milliseconds) and says what happened. */
    public static Diagnosis diagnose(Instance instance, int exitCode, long startedAt) {
        String log = tail(instance.folder().resolve("kelp-output.log"), 400_000);
        String report = newestCrashReport(instance.folder().resolve("crash-reports"), startedAt);
        return diagnose(log, report, InstalledMod.list(instance.mods()), exitCode, instance.version().id());
    }

    private static final Pattern FABRIC_MISSING = Pattern.compile(
            "Mod '([^']+)' \\(([\\w\\-]+)\\)[^\\n]*? requires [^\\n]*? of (?:mod )?'?([^',\\n]+?)'? ?(?:\\(([\\w\\-]+)\\))?,? which is missing");
    private static final Pattern FABRIC_CONFLICT = Pattern.compile("Mod '([^']+)' \\(([\\w\\-]+)\\)[^\\n]*? is incompatible with [^\\n]*?'([^']+)'");
    private static final Pattern FORGE_MISSING = Pattern.compile(
            "Mod ID: '([\\w\\-]+)', Requested by: '([\\w\\-]+)'[^\\n]*?Actual version: '\\[MISSING\\]'");
    private static final Pattern MIXIN_MOD = Pattern.compile("(?:Mixin apply for mod|from mod|mixin config [\\w.\\-]+ from mod) ([\\w\\-]+)");
    private static final Pattern SUSPECTED = Pattern.compile("Suspected Mods?: ([^\\n(]+?)(?: \\(([\\w\\-]+)\\))?\\s*$", Pattern.MULTILINE);
    private static final Pattern EXCEPTION = Pattern.compile("^(?:Caused by: |Exception in thread \"[^\"]*\" )?((?:[a-z]\\w*\\.)+\\w*(?:Exception|Error)[^\\n]*)",
            Pattern.MULTILINE);
    private static final Pattern DESCRIPTION = Pattern.compile("^Description: (.+)$", Pattern.MULTILINE);

    /** The reasoning itself, without files: log is what the game printed, report the crash report (or ""). */
    static Diagnosis diagnose(String log, String report, List<InstalledMod> mods, int exitCode, String minecraft) {
        String all = log + "\n" + report;
        String details = details(log, report);

        if (all.contains("java.lang.OutOfMemoryError") || all.contains("Java heap space")) {
            return new Diagnosis(t("Minecraft ran out of memory."),
                    t("Give it more in Kelp's Options > Memory, or play with fewer mods."), null, details);
        }
        Matcher missing = FABRIC_MISSING.matcher(all);
        if (missing.find()) {
            InstalledMod mod = find(mods, missing.group(2), missing.group(1));
            return new Diagnosis(t("{0} needs {1}, which isn't installed.", missing.group(1), missing.group(3).strip()),
                    t("Add {0} for Minecraft {1}, or turn {2} off.", missing.group(3).strip(), minecraft, missing.group(1)), mod, details);
        }
        Matcher forgeMissing = FORGE_MISSING.matcher(all);
        if (forgeMissing.find()) {
            InstalledMod mod = find(mods, forgeMissing.group(2), forgeMissing.group(2));
            String who = mod != null ? mod.name() : forgeMissing.group(2);
            return new Diagnosis(t("{0} needs {1}, which isn't installed.", who, forgeMissing.group(1)),
                    t("Add {0} for Minecraft {1}, or turn {2} off.", forgeMissing.group(1), minecraft, who), mod, details);
        }
        Matcher conflict = FABRIC_CONFLICT.matcher(all);
        if (conflict.find()) {
            InstalledMod mod = find(mods, conflict.group(2), conflict.group(1));
            return new Diagnosis(t("{0} doesn't work together with {1}.", conflict.group(1), conflict.group(3)),
                    t("Turn one of them off."), mod, details);
        }
        if (all.contains("UnsupportedClassVersionError")) {
            InstalledMod mod = culpritFromStack(all, mods);
            String who = mod != null ? mod.name() : t("A mod");
            return new Diagnosis(t("{0} needs a newer Java than this Minecraft uses.", who),
                    t("Get the version of it made for Minecraft {0}.", minecraft), mod, details);
        }
        Matcher mixin = MIXIN_MOD.matcher(all);
        if (mixin.find() && (all.contains("MixinApplyError") || all.contains("Mixin apply") || all.contains("InvalidMixinException")
                || all.contains("MixinTransformerError"))) {
            InstalledMod mod = find(mods, mixin.group(1), mixin.group(1));
            String who = mod != null ? mod.name() : mixin.group(1);
            return new Diagnosis(t("{0} doesn't work with this Minecraft version (or with another mod).", who),
                    t("Look for an update to it, or turn it off."), mod, details);
        }
        if (all.contains("NoSuchMethodError") || all.contains("NoSuchFieldError") || all.contains("NoClassDefFoundError")) {
            InstalledMod mod = culpritFromStack(all, mods);
            if (mod != null) {
                return new Diagnosis(t("{0} was made for a different Minecraft version.", mod.name()),
                        t("Look for its version for Minecraft {0}, or turn it off.", minecraft), mod, details);
            }
        }
        String lower = all.toLowerCase(Locale.ROOT);
        if (lower.contains("glfw error") || lower.contains("pixel format not accelerated") || lower.contains("opengl")
                && (lower.contains("not supported") || lower.contains("driver")) || exitCode == -1073741819 || exitCode == -805306369) {
            return new Diagnosis(t("Your graphics driver had a problem."),
                    t("Update your graphics driver (from your computer's maker, NVIDIA, AMD or Intel), then try again."), null, details);
        }
        Matcher suspected = SUSPECTED.matcher(report);
        if (suspected.find() && !suspected.group(1).strip().equalsIgnoreCase("none")) {
            InstalledMod mod = find(mods, suspected.group(2), suspected.group(1).strip());
            String who = mod != null ? mod.name() : suspected.group(1).strip();
            return new Diagnosis(t("It looks like {0} caused the crash.", who), t("Turn it off and try again."), mod, details);
        }
        InstalledMod fromStack = culpritFromStack(all, mods);
        if (fromStack != null) {
            return new Diagnosis(t("It looks like {0} caused the crash.", fromStack.name()), t("Turn it off and try again."), fromStack, details);
        }
        Matcher description = DESCRIPTION.matcher(report);
        Matcher exception = EXCEPTION.matcher(all);
        String why = description.find() ? description.group(1).strip() : exception.find() ? exception.group(1).strip() : null;
        return new Diagnosis(why != null ? t("Minecraft crashed: {0}", why) : t("Minecraft closed with an error (code {0}).", exitCode),
                mods.isEmpty() ? t("Try again. If it keeps happening, copy the report and ask for help.")
                        : t("Try turning mods off one at a time to find the one causing it."), null, details);
    }

    /** The mod whose id or name matches (ignoring case, spaces and dashes), if it's one of the instance's. */
    private static InstalledMod find(List<InstalledMod> mods, String id, String name) {
        for (InstalledMod mod : mods) {
            String file = simple(mod.file().getFileName().toString());
            String modName = simple(mod.name());
            for (String wanted : new String[] {id, name}) {
                if (wanted == null || wanted.isBlank()) continue;
                String w = simple(wanted);
                if (modName.equals(w) || file.startsWith(w)) return mod;
            }
        }
        return null;
    }

    private static String simple(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * The mod whose code shows up in the error's stack trace: the first line that isn't Java's or Minecraft's names a
     * package, and the mod whose jar has that package is the one.
     */
    private static InstalledMod culpritFromStack(String text, List<InstalledMod> mods) {
        Matcher at = Pattern.compile("\\bat (?:[\\w.\\-]+//)?([a-z][\\w]*(?:\\.[a-z][\\w]*)+)\\.[A-Z]").matcher(text);
        while (at.find()) {
            String pkg = at.group(1);
            if (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("jdk.") || pkg.startsWith("sun.")
                    || pkg.startsWith("net.minecraft") || pkg.startsWith("com.mojang") || pkg.startsWith("org.lwjgl")
                    || pkg.startsWith("net.fabricmc") || pkg.startsWith("org.quiltmc") || pkg.startsWith("net.neoforged")
                    || pkg.startsWith("net.minecraftforge") || pkg.startsWith("cpw.mods") || pkg.startsWith("org.spongepowered")
                    || pkg.startsWith("squid") || pkg.startsWith("com.google") || pkg.startsWith("it.unimi") || pkg.startsWith("io.netty")
                    || pkg.startsWith("org.apache") || pkg.startsWith("org.slf4j") || pkg.startsWith("org.objectweb")) {
                continue;
            }
            String folder = pkg.replace('.', '/') + "/";
            for (InstalledMod mod : mods) {
                if (!mod.enabled() || !mod.file().toString().endsWith(".jar")) continue;
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mod.file().toFile())) {
                    if (zip.stream().anyMatch(e -> e.getName().startsWith(folder))) return mod;
                } catch (IOException e) {
                    // not readable: not this one
                }
            }
        }
        return null;
    }

    /** The part worth copying: the crash report's top, or else the last lines the game printed. */
    private static String details(String log, String report) {
        if (!report.isBlank()) return report.length() > 6000 ? report.substring(0, 6000) : report;
        String[] lines = log.split("\\R");
        int from = Math.max(0, lines.length - 80);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, from, lines.length));
    }

    /** The end of a text file (big logs are only read from the end). Empty if it isn't there. */
    static String tail(Path file, int bytes) {
        if (!Files.exists(file)) return "";
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            long start = Math.max(0, in.length() - bytes);
            byte[] data = new byte[(int) (in.length() - start)];
            in.seek(start);
            in.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** Minecraft's newest crash report, if one was written after the game started. */
    private static String newestCrashReport(Path folder, long startedAt) {
        if (!Files.isDirectory(folder)) return "";
        try (Stream<Path> files = Files.list(folder)) {
            Path newest = files.filter(f -> f.toString().endsWith(".txt"))
                    .max(Comparator.comparingLong(CrashHelper::modified)).orElse(null);
            return newest != null && modified(newest) >= startedAt ? tail(newest, 200_000) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
