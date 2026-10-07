package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Themes from the Store (the same approved list Squid's Store uses, items of type "theme"). Get puts one with your
 * themes and switches to it. Every download is checked against its fingerprint before it's used.
 */
public class ThemeStoreScreen extends Screen {
    /** The Store's list. Tests point it at a pretend server. */
    static String storeUrl = "https://raw.githubusercontent.com/SamuelArther/squid-store/main/store.json";

    /** One theme in the Store. */
    record Item(String id, String name, String author, String url, String sha256) {
    }

    private final Screen parent;
    private final McList<Item> list = new McList<>(220);
    private final McButton getButton = new McButton(t("Get"), this::get);
    private final McButton doneButton = new McButton(t("Done"), this::done);
    private volatile String status = t("Loading the Store...");
    private volatile int statusColor = 0xA0A0A0;
    private volatile boolean working;

    public ThemeStoreScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(getButton);
        buttons.add(doneButton);
        Thread loader = new Thread(() -> {
            try {
                List<Item> items = load();
                list.setItems(items);
                status = items.isEmpty() ? t("No themes in the Store yet. Make your own!") : null;
            } catch (Exception e) {
                status = t("Couldn't reach the Store. Check your internet and try again.");
                statusColor = 0xFF5555;
            }
        }, "theme store");
        loader.setDaemon(true);
        loader.start();
    }

    /** The Store's themes. */
    static List<Item> load() throws IOException, InterruptedException {
        HttpResponse<String> response = client().send(HttpRequest.newBuilder(URI.create(storeUrl)).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("the Store answered with error " + response.statusCode());
        return parse(response.body());
    }

    static List<Item> parse(String json) {
        List<Item> items = new ArrayList<>();
        for (Object entry : Json.array(Json.object(Json.parse(json)).get("items"))) {
            Map<String, Object> item = Json.object(entry);
            if (item == null || !"theme".equals(item.get("type"))) continue;
            if (!(item.get("id") instanceof String id) || !id.matches("[a-z0-9_-]+")) continue;
            if (!(item.get("url") instanceof String url) || !(item.get("sha256") instanceof String sha)) continue;
            items.add(new Item(id, String.valueOf(item.getOrDefault("name", id)), String.valueOf(item.getOrDefault("author", "")), url, sha.toLowerCase()));
        }
        return items;
    }

    /** Downloads a theme, checks it, and unpacks it into the themes folder. Gives back the theme. */
    static Theme install(Item item) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = client().send(HttpRequest.newBuilder(URI.create(item.url())).timeout(Duration.ofMinutes(2)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException(t("the download answered with error {0}", response.statusCode()));
        byte[] zip = response.body();
        String sha;
        try {
            sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (!sha.equals(item.sha256())) throw new IOException(t("it arrived damaged, so it wasn't installed"));
        Path dir = Theme.folder().resolve(item.id()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        try (InputStream bytes = new java.io.ByteArrayInputStream(zip); ZipInputStream in = new ZipInputStream(bytes)) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                if (entry.isDirectory()) continue;
                Path target = dir.resolve(entry.getName()).normalize();
                if (!target.startsWith(dir)) throw new IOException(t("it arrived damaged, so it wasn't installed"));
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Theme theme = Theme.read(dir);
        if (theme == null) throw new IOException(t("it arrived damaged, so it wasn't installed"));
        return theme;
    }

    private void get() {
        Item item = list.getSelected();
        if (item == null || working) return;
        working = true;
        status = t("Getting {0}...", item.name());
        statusColor = 0xA0A0A0;
        Thread worker = new Thread(() -> {
            try {
                Theme.use(install(item));
                status = t("Got {0}! It's on now.", item.name());
                statusColor = 0x55FF55;
            } catch (Exception e) {
                status = t("Couldn't get it: {0}", e.getMessage());
                statusColor = 0xFF5555;
            } finally {
                working = false;
            }
        }, "get theme");
        worker.setDaemon(true);
        worker.start();
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Get Themes"), w, 12 * GUI, 0xFFFFFF);
        list.draw(g, font, w, 32 * GUI, h - 60 * GUI, null, (gg, item, x, y, width) -> {
            font.draw(gg, item.name(), x, y, GUI, 0xFFFFFF);
            boolean have = Files.exists(Theme.folder().resolve(item.id()).resolve("theme.properties"));
            String note = have ? t("Installed") : item.author().isEmpty() ? "" : t("By {0}", item.author());
            font.draw(gg, note, x + width - font.width(note, GUI), y, GUI, have ? 0x55FF55 : 0xA0A0A0);
        });
        if (status != null) centered(g, status, w, h - 52 * GUI, statusColor);
        getButton.setActive(list.getSelected() != null && !working);
        getButton.setBounds(w / 2 - 100 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        doneButton.setBounds(w / 2 + 2 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        list.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        if (list.mousePressed(x, y) == null) super.mousePressed(x, y);
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        list.mouseWheel(notches);
    }

    private void done() {
        panel.setScreen(parent);
    }
}
