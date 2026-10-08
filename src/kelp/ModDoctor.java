package kelp;

import static kelp.Lang.t;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Mod Doctor: finds what would make Squid skip a mod before the game even starts, says it in plain words, and fixes
 * it with one click when it can. It never deletes anything: a second copy of a mod is turned off, not removed.
 *
 * It checks for two copies of the same mod, and in your own projects: a squid.json with a mistake in it, a main class
 * that isn't there (when the class that should be is easy to find), an id with letters Squid can't use, keys that
 * look like typos ("author" for "authors"), and mods it needs that aren't in the folder.
 */
public final class ModDoctor {
    private ModDoctor() {
    }

    /** What's wrong with a mod, and how to fix it (fixLabel and fix are null when only a person can fix it). */
    public record Finding(String message, String fixLabel, Fix fix) {
    }

    /** A fix: does it, and says what it did. */
    public interface Fix {
        String apply() throws IOException;
    }

    /** Keys people often write by mistake in squid.json, and the one they meant (the same list Squid uses). */
    static final Map<String, String> DID_YOU_MEAN = Map.ofEntries(
            Map.entry("author", "authors"), Map.entry("dependencies", "depends"), Map.entry("depend", "depends"),
            Map.entry("requires", "depends"), Map.entry("mainclass", "main"), Map.entry("main_class", "main"),
            Map.entry("entrypoint", "main"), Map.entry("mc", "minecraft"), Map.entry("minecraft_version", "minecraft"),
            Map.entry("minecraftversion", "minecraft"), Map.entry("desc", "description"), Map.entry("title", "name"),
            Map.entry("modid", "id"), Map.entry("mod_id", "id"), Map.entry("environment", "side"),
            Map.entry("logo", "icon"), Map.entry("image", "icon"));
    static final Set<String> KEYS = Set.of("id", "name", "version", "description", "authors", "depends", "minecraft",
            "main", "side", "icon");

    /** Every mod with something wrong, and the most important thing wrong with it. */
    public static Map<Path, Finding> check(List<InstalledMod> mods) {
        return check(mods, null);
    }

    /**
     * The same, knowing the instance's Minecraft version (null for any), so it picks the copy Squid really loads:
     * the best copy that works. A copy with a mistake or for another Minecraft gets skipped, and the next one loads.
     */
    public static Map<Path, Finding> check(List<InstalledMod> mods, String minecraftVersion) {
        Map<Path, Finding> found = new HashMap<>();
        // Two copies of one mod: Squid loads the newest (the first, if they're the same), so the others get turned off
        Map<String, List<InstalledMod>> byId = new LinkedHashMap<>();
        Set<String> ids = new java.util.HashSet<>();
        for (InstalledMod mod : mods) {
            if (!mod.squidMod()) continue;
            String id = InstalledMod.squidId(mod.file());
            if (id == null) continue;
            if (mod.enabled()) {
                byId.computeIfAbsent(id, k -> new ArrayList<>()).add(mod);
                ids.add(id);
            }
        }
        for (List<InstalledMod> copies : byId.values()) {
            if (copies.size() < 2) continue;
            // The same pick Squid makes: the newest version; if they're the same, your own code over a packed copy,
            // then the first by file name (sorted the way Squid sorts them)
            List<InstalledMod> sorted = new ArrayList<>(copies);
            sorted.sort(java.util.Comparator.comparing(InstalledMod::file));
            sorted.sort((a, b) -> {
                int c = compareVersions(version(b), version(a));
                if (c != 0) return c;
                if (a.source() != b.source()) return a.source() ? -1 : 1;
                return a.file().compareTo(b.file());
            });
            InstalledMod keep = sorted.getFirst();
            for (InstalledMod copy : sorted) {
                boolean broken = copy.project() && checkProject(copy.file(), ALL_IDS) != null; // a missing mod it needs doesn't count here
                boolean wrongVersion = minecraftVersion != null && !copy.worksOn(minecraftVersion);
                if (!broken && !wrongVersion) {
                    keep = copy;
                    break;
                }
            }
            for (InstalledMod copy : copies) {
                if (copy == keep) continue;
                // A copy that has its own mistake gets that pointed out instead (with its own Fix)
                if (copy.project() && checkProject(copy.file(), ALL_IDS) != null) continue;
                InstalledMod kept = keep;
                found.put(copy.file(), new Finding(
                        t("{0} is another copy of this mod, so Squid skips this one.", kept.file().getFileName()),
                        t("Turn Off"), () -> {
                            copy.toggle();
                            return t("Turned off the extra copy, {0}.", copy.file().getFileName());
                        }));
            }
        }
        for (InstalledMod mod : mods) {
            if (!mod.project() || !mod.enabled() || found.containsKey(mod.file())) continue;
            Finding finding = checkProject(mod.file(), ids);
            if (finding != null) found.put(mod.file(), finding);
        }
        return found;
    }

    /** A set that has every id, for checking a project as if every mod it needs were there. */
    private static final Set<String> ALL_IDS = new java.util.AbstractSet<>() {
        @Override
        public boolean contains(Object o) {
            return true;
        }

        @Override
        public java.util.Iterator<String> iterator() {
            return java.util.Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }
    };

    /** A mod's version as Squid sees it: your own code without one is "1.0". */
    static String version(InstalledMod mod) {
        return mod.version().isBlank() && mod.source() ? "1.0" : mod.version();
    }

    /** Compares versions like "1.2.0" and "1.10" number by number, a beta before its release: the same as Squid. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("[.+-]");
        String[] y = b.split("[.+-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : null;
            String q = i < y.length ? y[i] : null;
            // A missing part counts as 0 ("1.0" is "1.0.0"); a word after it ("1.0.0-beta") comes before the release
            if (p == null) {
                if (number(q) && Long.parseLong(q) == 0) continue;
                return number(q) ? -1 : 1;
            }
            if (q == null) {
                if (number(p) && Long.parseLong(p) == 0) continue;
                return number(p) ? 1 : -1;
            }
            int c = number(p) && number(q) ? Long.compare(Long.parseLong(p), Long.parseLong(q))
                    : number(p) ? 1 : number(q) ? -1 : p.compareTo(q);
            if (c != 0) return c;
        }
        return 0;
    }

    private static boolean number(String part) {
        return part.matches("\\d{1,18}");
    }

    /** What's wrong with a project folder, or null if nothing is. */
    static Finding checkProject(Path folder, Set<String> ids) {
        Path file = folder.resolve("squid.json");
        if (!Files.exists(file)) return null; // Kelp's own projects always have one; Squid explains the rest
        Map<String, Object> json;
        try {
            Object parsed = Json.parse(ModProject.text(Files.readAllBytes(file)));
            if (!(parsed instanceof Map<?, ?>)) {
                return new Finding(t("Its squid.json has a mistake: {0}", t("it has to start with { and end with }")), null, null);
            }
            json = new LinkedHashMap<>(Json.object(parsed));
        } catch (IOException | RuntimeException e) {
            return new Finding(t("Its squid.json has a mistake: {0}", e.getMessage()), null, null);
        }
        String className = folder.getFileName().toString().replaceAll("[^A-Za-z0-9_]", "");

        // The main class: the one Squid starts. If it isn't there but exactly one class is a mod, that's the one.
        String main = json.get("main") instanceof String m && !m.isBlank() ? m : className;
        Path src = folder.resolve("src");
        if (Files.isDirectory(src) && !ModProject.classesIn(src).contains(main)) {
            List<String> mains = modClasses(src);
            if (mains.size() == 1) {
                String right = mains.getFirst();
                return new Finding(t("Squid looks for the class {0}, but it isn't in src.", main), t("Fix"), () -> {
                    Map<String, Object> now = reread(file);
                    now.put("main", right);
                    write(file, now);
                    return t("Squid will start {0} now.", right);
                });
            }
            return new Finding(mains.isEmpty() ? t("None of its classes say \"extends EasyMod\", so Squid has nothing to start.")
                    : t("Squid looks for the class {0}, but it isn't in src. Put \"main\": \"YourClass\" in squid.json.", main), null, null);
        }

        // An id Squid can't use
        if (json.get("id") instanceof String id && !id.isBlank() && !id.matches("[a-z0-9_-]+")) {
            String fixed = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
            String better = fixed.isEmpty() ? ModProject.idFor(className) : fixed;
            return new Finding(t("Its id \"{0}\" can only use a-z, 0-9, _ and -.", id), t("Fix"), () -> {
                Map<String, Object> now = reread(file);
                now.put("id", better);
                write(file, now);
                return t("Its id is {0} now.", better);
            });
        }

        // Keys that look like typos
        for (String key : List.copyOf(json.keySet())) {
            if (KEYS.contains(key)) continue;
            String meant = DID_YOU_MEAN.get(key.toLowerCase(Locale.ROOT));
            if (meant == null && KEYS.contains(key.toLowerCase(Locale.ROOT))) meant = key.toLowerCase(Locale.ROOT);
            if (meant == null || json.containsKey(meant)) continue;
            String right = meant;
            return new Finding(t("squid.json has \"{0}\". Did you mean \"{1}\"?", key, right), t("Fix"), () -> {
                // Same place in the file, new name
                Map<String, Object> renamed = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : reread(file).entrySet()) renamed.put(e.getKey().equals(key) ? right : e.getKey(), e.getValue());
                write(file, renamed);
                return t("Changed \"{0}\" to \"{1}\".", key, right);
            });
        }

        // Mods it needs that aren't here (Squid's own parts are always there)
        Object depends = json.get("depends");
        List<?> needs = depends instanceof List<?> list ? list : depends instanceof String one ? List.of(one) : List.of();
        String ownId = json.get("id") instanceof String id && !id.isBlank() ? id : ModProject.idFor(className);
        for (Object need : needs) {
            String needed = String.valueOf(need).strip();
            if (needed.isEmpty() || needed.equals(ownId) || needed.startsWith("squid-") || ids.contains(needed)) continue;
            return new Finding(t("It needs the mod \"{0}\", which isn't in the mods folder.", needed), null, null);
        }
        return null;
    }

    /** The classes in src that are mods (they say "extends EasyMod" or "implements SquidMod"), by full name. */
    static List<String> modClasses(Path src) {
        List<String> found = new ArrayList<>();
        java.util.regex.Pattern pkg = java.util.regex.Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", java.util.regex.Pattern.MULTILINE);
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String code = ModProject.text(Files.readAllBytes(file));
                if (!code.matches("(?s).*\\b(extends\\s+EasyMod|implements\\s+([\\w.]+\\s*,\\s*)*SquidMod)\\b.*")) continue;
                String fileName = file.getFileName().toString();
                String className = fileName.substring(0, fileName.length() - ".java".length());
                var m = pkg.matcher(code);
                found.add(m.find() ? m.group(1) + "." + className : className);
            }
        } catch (IOException | RuntimeException e) {
            // can't look: nothing found
        }
        return found;
    }

    /** squid.json as it is right now: a fix starts from the newest version, never from what was read earlier. */
    private static Map<String, Object> reread(Path file) throws IOException {
        Object parsed;
        try {
            parsed = Json.parse(ModProject.text(Files.readAllBytes(file)));
        } catch (RuntimeException e) {
            throw new IOException(t("Its squid.json has a mistake: {0}", e.getMessage()));
        }
        if (!(parsed instanceof Map<?, ?>)) throw new IOException(t("Its squid.json has a mistake: {0}", t("it has to start with { and end with }")));
        return new LinkedHashMap<>(Json.object(parsed));
    }

    private static void write(Path file, Map<String, Object> json) throws IOException {
        Files.writeString(file, ModProject.toJson(json));
    }
}
