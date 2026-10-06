package kelp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Mojang's official list of every Minecraft version, newest first. */
public final class VersionManifest {
    private static final String URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    /**
     * One version from the list.
     *
     * @param type "release", "snapshot", "old_beta" or "old_alpha"
     * @param url  where that version's own details live (we'll need it to download the game)
     */
    public record Version(String id, String type, String url, String releaseTime) {
    }

    private VersionManifest() {
    }

    public static List<Version> download() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Mojang answered with error " + response.statusCode());

        Map<String, Object> manifest = Json.object(Json.parse(response.body()));
        List<Version> versions = new ArrayList<>();
        for (Object entry : Json.array(manifest.get("versions"))) {
            Map<String, Object> v = Json.object(entry);
            versions.add(new Version(
                    (String) v.get("id"),
                    (String) v.get("type"),
                    (String) v.get("url"),
                    (String) v.get("releaseTime")));
        }
        return versions;
    }
}
