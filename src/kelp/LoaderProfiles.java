package kelp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fabric and Quilt describe how to start the game the same way Mojang does, in a version file that
 * "inheritsFrom" a Minecraft version. Kelp downloads that file and its libraries, then merges it into the
 * Minecraft version when the game starts.
 */
public final class LoaderProfiles {
    // Where Fabric and Quilt list their versions. Tests point these at a pretend server.
    static String fabricMeta = "https://meta.fabricmc.net/v2";
    static String quiltMeta = "https://meta.quiltmc.org/v3";

    private LoaderProfiles() {
    }

    /**
     * Picks the newest loader for this Minecraft version, saves its version file, and adds its libraries to jobs.
     * Gives back the loader's version id, like "fabric-loader-0.19.5-26.3", which is what the game starts from.
     */
    public static String install(Loader loader, String minecraft, Downloader downloader, Map<Path, Downloader.Job> jobs)
            throws IOException, InterruptedException {
        loader = loader.runtime();
        String meta = switch (loader) {
            case FABRIC -> fabricMeta;
            case QUILT -> quiltMeta;
            default -> throw new IllegalArgumentException(loader.label() + " isn't installed this way");
        };
        Path cache = Folders.versions().resolve("loaders");
        String list = downloader.fetchText(meta + "/versions/loader/" + minecraft,
                cache.resolve(loader.name().toLowerCase() + "-" + minecraft + ".json"));
        String version = pick(Json.array(Json.parse(list)));
        if (version == null) throw new IOException(loader.label() + " doesn't support Minecraft " + minecraft + " yet.");

        // The id is only known after downloading, so save it under a name of our own first, then under its id
        Path temp = cache.resolve(loader.name().toLowerCase() + "-" + minecraft + "-profile.json");
        String text = downloader.fetchText(meta + "/versions/loader/" + minecraft + "/" + version + "/profile/json", temp);
        Map<String, Object> profile = Json.object(Json.parse(text));
        String id = (String) profile.get("id");
        Path saved = Folders.versions().resolve(id).resolve(id + ".json");
        Files.createDirectories(saved.getParent());
        Files.writeString(saved, text);

        for (Object entry : Json.array(profile.get("libraries"))) {
            Map<String, Object> library = Json.object(entry);
            String path = mavenPath((String) library.get("name"));
            Path file = Folders.libraries().resolve(path);
            String url = library.get("url") instanceof String base ? base : "https://maven.fabricmc.net/";
            if (!url.endsWith("/")) url += "/";
            long size = library.get("size") instanceof Double d ? d.longValue() : -1; // -1: not listed
            jobs.put(file, new Downloader.Job(url + path, file, (String) library.get("sha1"), size));
        }
        return id;
    }

    /**
     * The newest stable loader, or the newest one if none are stable yet (new Minecraft versions start that way).
     * Compared by number, because Quilt's list isn't in order.
     */
    static String pick(List<Object> versions) {
        String newest = null;
        String newestStable = null;
        for (Object entry : versions) {
            Map<String, Object> loader = Json.object(Json.object(entry).get("loader"));
            String version = (String) loader.get("version");
            boolean stable = loader.get("stable") instanceof Boolean b ? b : !version.matches(".*-(beta|pre|rc|alpha).*");
            if (newest == null || compare(version, newest) > 0) newest = version;
            if (stable && (newestStable == null || compare(version, newestStable) > 0)) newestStable = version;
        }
        return newestStable != null ? newestStable : newest;
    }

    /** Compares versions like "0.30.1" and "0.20.0-beta.9" number by number. A beta comes before its release. */
    static int compare(String a, String b) {
        String[] x = a.split("[.+-]");
        String[] y = b.split("[.+-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : null;
            String q = i < y.length ? y[i] : null;
            if (p == null) return q.matches("\\d+") ? -1 : 1; // "1.0" is newer than "1.0-beta"
            if (q == null) return p.matches("\\d+") ? 1 : -1;
            int c = p.matches("\\d+") && q.matches("\\d+") ? Long.compare(Long.parseLong(p), Long.parseLong(q))
                    : p.matches("\\d+") ? 1 : q.matches("\\d+") ? -1 : p.compareTo(q);
            if (c != 0) return c;
        }
        return 0;
    }

    /** Where a library goes, from its Maven name: "net.fabricmc:fabric-loader:0.19.5" becomes net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar */
    public static String mavenPath(String name) {
        String extension = "jar";
        int at = name.indexOf('@');
        if (at >= 0) {
            extension = name.substring(at + 1);
            name = name.substring(0, at);
        }
        String[] parts = name.split(":");
        String file = parts[1] + "-" + parts[2] + (parts.length > 3 ? "-" + parts[3] : "") + "." + extension;
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + file;
    }

    /**
     * Minecraft's version file with the loader's merged in: the loader's main class, its libraries first
     * (replacing Minecraft's copy of the same library), and its arguments after Minecraft's.
     */
    public static Map<String, Object> merge(Map<String, Object> minecraft, Map<String, Object> loader) {
        Map<String, Object> merged = new LinkedHashMap<>(minecraft);
        if (loader.get("mainClass") != null) merged.put("mainClass", loader.get("mainClass"));

        List<Object> libraries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Object entry : Json.array(loader.get("libraries"))) {
            Map<String, Object> library = new LinkedHashMap<>(Json.object(entry));
            String name = (String) library.get("name");
            names.add(withoutVersion(name));
            if (library.get("downloads") == null) {
                // Fabric lists libraries by Maven name only; give them the "downloads" Mojang's own libraries have
                library.put("downloads", Map.of("artifact", Map.of("path", mavenPath(name))));
            }
            libraries.add(library);
        }
        if (minecraft.get("libraries") != null) {
            for (Object entry : Json.array(minecraft.get("libraries"))) {
                String name = (String) Json.object(entry).get("name");
                if (name == null || !names.contains(withoutVersion(name))) libraries.add(entry);
            }
        }
        merged.put("libraries", libraries);

        Map<String, Object> loaderArguments = Json.object(loader.get("arguments"));
        Map<String, Object> arguments = Json.object(minecraft.get("arguments"));
        if (arguments != null && loaderArguments != null) {
            Map<String, Object> both = new LinkedHashMap<>(arguments);
            for (String kind : new String[] {"jvm", "game"}) {
                List<Object> list = new ArrayList<>();
                if (arguments.get(kind) != null) list.addAll(Json.array(arguments.get(kind)));
                if (loaderArguments.get(kind) != null) list.addAll(Json.array(loaderArguments.get(kind)));
                both.put(kind, list);
            }
            merged.put("arguments", both);
        }
        if (loader.get("minecraftArguments") != null) merged.put("minecraftArguments", loader.get("minecraftArguments"));
        return merged;
    }

    /** "group:artifact:version:classifier" without the version, so two versions of one library count as the same. */
    private static String withoutVersion(String name) {
        String[] parts = name.split(":");
        return parts.length > 3 ? parts[0] + ":" + parts[1] + ":" + parts[3] : parts.length > 1 ? parts[0] + ":" + parts[1] : name;
    }
}
