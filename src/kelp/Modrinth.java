package kelp;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Downloads well-known mods from Modrinth, the mod site Fabric players use, for Kelp's ready-made setups
 * like the Shaders instance (Fabric + Sodium + Iris).
 */
public final class Modrinth {
    static String api = "https://api.modrinth.com/v2"; // tests point this at a pretend server

    private Modrinth() {
    }

    /**
     * The newest release of a project for this loader and Minecraft version, as a download into modsFolder.
     * If there's no release yet (just alphas and betas), the newest of those.
     */
    public static Downloader.Job latest(String project, String loader, String minecraft, Path modsFolder, Downloader downloader)
            throws IOException, InterruptedException {
        String url = api + "/project/" + project + "/version?loaders=" + list(loader) + "&game_versions=" + list(minecraft);
        Path cache = Folders.versions().resolve("loaders").resolve("modrinth-" + project + "-" + loader + "-" + minecraft + ".json");
        List<Object> versions = Json.array(Json.parse(downloader.fetchText(url, cache)));
        Map<String, Object> chosen = null;
        for (Object entry : versions) {
            Map<String, Object> version = Json.object(entry);
            if (chosen == null) chosen = version; // newest of all, in case there's no release
            if ("release".equals(version.get("version_type"))) {
                chosen = version;
                break;
            }
        }
        if (chosen == null) throw new IOException(project + " doesn't have a version for Minecraft " + minecraft + " yet.");

        Map<String, Object> file = null;
        for (Object entry : Json.array(chosen.get("files"))) {
            Map<String, Object> f = Json.object(entry);
            if (file == null || Boolean.TRUE.equals(f.get("primary"))) file = f;
        }
        if (file == null) throw new IOException(project + " has no file to download.");
        Map<String, Object> hashes = Json.object(file.get("hashes"));
        long size = file.get("size") instanceof Double d ? d.longValue() : -1;
        return new Downloader.Job((String) file.get("url"), modsFolder.resolve((String) file.get("filename")),
                hashes == null ? null : (String) hashes.get("sha1"), size);
    }

    /** ["value"], the way Modrinth's API wants lists in a link. */
    private static String list(String value) {
        return URLEncoder.encode("[\"" + value + "\"]", StandardCharsets.UTF_8);
    }
}
