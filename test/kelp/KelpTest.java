package kelp;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Kelp's tests. They run in a temporary folder, so real instances and settings are never touched.
 * Run them with test.bat.
 */
public class KelpTest {
    static int failures = 0;
    static Path home;

    static void check(String what, Object got, Object expected) {
        boolean ok = Objects.equals(got, expected);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + what + " -> " + got + (ok ? "" : "   (expected " + expected + ")"));
    }

    /** What went wrong when running something, or "no problem". */
    static String problem(Action action) {
        try {
            action.run();
            return "no problem";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    interface Action {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        home = Files.createTempDirectory("kelp-test");
        System.setProperty("kelp.home", home.toString()); // before anything reads Kelp's folders

        json();
        rules();
        folders();
        settings();
        languages();
        instances();
        gameOptions();
        mods();
        downloader();
        launcher();
        loaders();
        defaultInstance();
        shaders();
        forge();
        worlds();
        addMods();
        projects();
        updates();
        crashHelper();
        backups();
        stats();
        modpacks();
        themes();
        squidReport();
        accounts();
        microsoftLogin();

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        deleteFolder(home); // the temporary folder isn't needed anymore
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- Reading Mojang's lists ----

    static void json() {
        Map<String, Object> o = Json.object(Json.parse("{\"a\": [1, 2.5, -3e2], \"b\": {\"c\": true, \"d\": null}, \"e\": \"x\\n\\\"y\\\" \\u00e9\"}"));
        check("JSON numbers", Json.array(o.get("a")).toString(), "[1.0, 2.5, -300.0]");
        check("JSON true and null", Json.object(o.get("b")).toString(), "{c=true, d=null}");
        check("JSON escapes", o.get("e"), "x\n\"y\" \u00e9");
        check("JSON empty object and list", Json.parse("{\"x\": {}, \"y\": []}").toString(), "{x={}, y=[]}");
        check("broken JSON is explained", problem(() -> Json.parse("{\"a\": }")), "Bad JSON at character 6: unexpected character");
        check("JSON with junk after the end", problem(() -> Json.parse("[1] 2")), "Bad JSON at character 4: extra text after the end");
    }

    // ---- Which files are for which computer ----

    static void rules() {
        String os = Rules.osName();
        Object onlyHere = Json.parse("[{\"action\": \"allow\", \"os\": {\"name\": \"" + os + "\"}}]");
        Object notHere = Json.parse("[{\"action\": \"allow\"}, {\"action\": \"disallow\", \"os\": {\"name\": \"" + os + "\"}}]");
        Object feature = Json.parse("[{\"action\": \"allow\", \"features\": {\"is_demo_user\": true}}]");
        check("no rules means allowed", Rules.allowed(null), true);
        check("allowed only on this computer's system", Rules.allowed(onlyHere), true);
        check("the last rule that matches wins", Rules.allowed(notHere), false);
        check("game features stay off", Rules.allowed(feature), false);
        Object tooNew = Json.parse("[{\"action\": \"allow\", \"os\": {\"name\": \"" + os + "\", \"versionRange\": {\"min\": \"999.0\"}}}]");
        check("a version range this computer is below", Rules.allowed(tooNew), false);
    }

    // ---- Where Kelp keeps its files on each system ----

    static void folders() {
        String realOs = System.getProperty("os.name");
        String custom = System.getProperty("kelp.home");
        System.clearProperty("kelp.home");
        try {
            String user = System.getProperty("user.home");
            System.setProperty("os.name", "Mac OS X");
            check("Mac folder", Folders.home().toString(), Path.of(user, "Library", "Application Support", "Kelp").toString());
            System.setProperty("os.name", "Linux");
            String xdg = System.getenv("XDG_DATA_HOME");
            Path linux = xdg != null && !xdg.isBlank() ? Path.of(xdg, "kelp") : Path.of(user, ".local", "share", "kelp");
            check("Linux folder", Folders.home().toString(), linux.toString());
        } finally {
            System.setProperty("os.name", realOs);
            System.setProperty("kelp.home", custom);
        }
    }

    // ---- Settings ----

    static void settings() {
        check("good player name", Settings.isValidName("Samuel_A"), true);
        check("too short", Settings.isValidName("ab"), false);
        check("too long", Settings.isValidName("abcdefghijklmnopq"), false);
        check("spaces aren't allowed", Settings.isValidName("Sam A"), false);
        Settings.setMemoryGb(4);
        check("memory is remembered", Settings.memoryGb(), 4);
        Settings.setMemoryGb(0);
    }

    // ---- Languages ----

    static void languages() throws Exception {
        check("a language line is read", Lang.parse("# a note\nPlay => Jugar\nDone => \n").toString(), "{Play=Jugar}");
        Settings.setLanguage("es_es");
        Lang.reload();
        check("text is translated", Lang.t("Play"), "Jugar");
        check("values are filled in", Lang.t("Added {0}!", "X-Ray"), "¡Añadido X-Ray!");
        check("missing text stays English", Lang.t("Not in any file"), "Not in any file");
        Settings.setLanguage("es");
        check("an older Kelp's language code still works", Lang.current().code(), "es_es");
        Settings.setLanguage("en_us");
        Lang.reload();
        check("English is English", Lang.t("Play"), "Play");
        check("English comes first, and isn't beta", Lang.ALL.get(0).code() + " " + Lang.ALL.get(0).beta(), "en_us false");

        // Every language file has every text, with the same {0}s, and is in the list (and the other way around)
        List<String> english = Files.readAllLines(Path.of("lang/es_es.txt")).stream()
                .filter(line -> !line.startsWith("#") && line.contains(" => ")).map(line -> line.substring(0, line.indexOf(" => "))).toList();
        List<String> files;
        try (java.util.stream.Stream<Path> list = Files.list(Path.of("lang"))) {
            files = list.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".txt") && !n.equals("languages.txt"))
                    .map(n -> n.substring(0, n.length() - 4)).sorted().toList();
        }
        check("every language file is in the list", Lang.ALL.stream().map(Lang.Language::code).filter(c -> !c.equals("en_us")).sorted().toList(), files);
        List<String> problems = new java.util.ArrayList<>();
        for (Lang.Language language : Lang.ALL) {
            if (!language.beta()) continue;
            String text = Files.readString(Path.of("lang/" + language.code() + ".txt"));
            if (!text.contains("BETA, not checked")) problems.add(language.code() + " isn't marked BETA");
            Map<String, String> table = Lang.parse(text);
            for (String key : english) {
                if (!table.containsKey(key)) problems.add(language.code() + " is missing: " + key);
                else if (!placeholders(key).equals(placeholders(table.get(key)))) problems.add(language.code() + " changes the {0}s in: " + key);
            }
        }
        check("all " + Lang.ALL.size() + " languages have every text, marked BETA", problems, List.of());

        // Letters Kelp's pixel font doesn't have are drawn with the computer's font, as 16-pixel letters at half size
        McFont font = new McFont(Textures.load("font.png"), Textures.load("font-extra.png"));
        check("Kelp's own letters use the pixel font", font.pixelOnly("Añadir Ö") + " " + font.pixelOnly("単一"), "true false");
        java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(200, 40, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = canvas.createGraphics();
        font.draw(g, "シングルプレイ", 4, 12, 2, 0xFFFFFF);
        g.dispose();
        long white = 0;
        for (int y = 0; y < 40; y++) for (int x = 0; x < 200; x++) if (canvas.getRGB(x, y) == 0xFFFFFFFF) white++;
        check("Japanese gets drawn (and its width measured)", white > 50 && font.width("シングルプレイ", 2) > 40, true);
    }

    /** The {0}, {1}... in a text, sorted. */
    static List<String> placeholders(String text) {
        return java.util.regex.Pattern.compile("\\{\\d}").matcher(text).results().map(r -> r.group()).sorted().distinct().toList();
    }

    // ---- Instances ----

    static void instances() throws Exception {
        VersionManifest.Version v = new VersionManifest.Version("26.3", "release", "https://example.com/26.3.json", "");
        Instance a = Instance.create("My World", v, true);
        Instance b = Instance.create("My World", v, false);
        check("a second instance with the same name gets its own folder", b.id(), "My World 2");
        Instance odd = Instance.create("  W<o>r:l*d?. ", v, false);
        check("names are made safe for folders", odd.id(), "World");
        Instance reserved = Instance.create("CON", v, false);
        check("names Windows won't allow are changed", reserved.id(), "CON instance");
        check("an instance is found again by its folder", Instance.find(a.id()).name(), "My World");
        check("Squid setting is remembered", Instance.find(a.id()).squid(), true);
        a.setSquid(false);
        check("Squid can be turned off", Instance.find(a.id()).squid(), false);

        Path old = Folders.instances().resolve("1.20.1");
        Files.createDirectories(old.resolve("saves"));
        Instance migrated = Instance.find("1.20.1");
        check("a folder from before instances becomes one", migrated.name() + " / " + migrated.version().id(), "1.20.1 / 1.20.1");

        Files.writeString(a.folder().resolve("saves.txt"), "a world");
        a.delete();
        check("deleting removes the folder", Files.exists(a.folder()), false);
        check("the other instances are still there", Instance.all().size(), 4);
    }

    // ---- Game options ----

    static void gameOptions() throws Exception {
        VersionManifest.Version v = new VersionManifest.Version("26.3", "release", "", "");
        Instance instance = Instance.create("Options Test", v, false);
        Path file = instance.folder().resolve("options.txt");
        Files.writeString(file, "version:5023\r\nrenderDistance:12\r\nkey_key.jump:key.keyboard.space\r\n");
        GameOptions options = GameOptions.load(instance);
        check("reads a number", options.getDouble("renderDistance", 0), 12.0);
        check("missing settings use the fallback", options.getBoolean("fullscreen", false), false);
        options.set("renderDistance", "20");
        options.set("fullscreen", "true");
        check("changes one line, adds a new one, keeps the rest and Windows line endings",
                Files.readString(file).replace("\r\n", "|"),
                "version:5023|renderDistance:20|key_key.jump:key.keyboard.space|graphicsPreset:\"custom\"|fullscreen:true|");
        check("a graphics setting switches the preset to Custom, so Minecraft doesn't undo it", options.get("graphicsPreset", ""), "\"custom\"");
        options.useFastPreset();
        check("Make It Faster picks Minecraft's own Fast preset", options.get("graphicsPreset", "") + " " + options.get("renderDistance", "")
                + " " + options.get("simulationDistance", ""), "\"fast\" 8 6");

        Instance fresh = Instance.create("Never Played", v, false);
        check("an unplayed, undownloaded instance explains itself", problem(() -> GameOptions.load(fresh)),
                "Play this instance once first, then its game options can be changed here.");
    }

    // ---- Mods ----

    static void mods() throws Exception {
        Path folder = home.resolve("mods-test");
        Files.createDirectories(folder);
        jar(folder.resolve("zoom.jar"), "squid.json", "{\"id\": \"zoom\", \"name\": \"Zoom\", \"version\": \"1.0.0\", \"authors\": [\"Samuel\"], \"minecraft\": \"26.3.x\"}");
        jar(folder.resolve("fabric-thing.jar"), "fabric.mod.json", "{}");
        Files.writeString(folder.resolve("broken.jar"), "not really a jar");
        Files.writeString(folder.resolve("notes.txt"), "not a mod");

        List<InstalledMod> mods = InstalledMod.list(folder);
        check("lists jars only, sorted by name", mods.stream().map(InstalledMod::name).toList().toString(), "[broken, fabric-thing, Zoom]");
        InstalledMod zoom = mods.get(2);
        check("reads the squid.json", zoom.squidMod() + " " + zoom.version() + " " + zoom.authors(), "true 1.0.0 [Samuel]");
        check("a jar without squid.json isn't a Squid mod", mods.get(1).squidMod(), false);
        check("knows which Minecraft versions it works on", zoom.worksOn("26.3") + " " + zoom.worksOn("26.3.2") + " "
                + zoom.worksOn("26.4") + " " + mods.get(1).worksOn("26.4"), "true true false true");
        check("a broken jar is still listed, with what went wrong", mods.get(0).description().startsWith("Couldn't read this mod"), true);

        InstalledMod off = zoom.toggle();
        check("turning a mod off renames it", Files.exists(folder.resolve("zoom.jar.disabled")) && !off.enabled(), true);
        InstalledMod on = off.toggle();
        check("turning it back on renames it back", Files.exists(folder.resolve("zoom.jar")) && on.enabled(), true);

        // Your own mods: New Mod writes a working .java file
        check("mod names become class names", ModTemplate.className("my cool mod!") + " " + ModTemplate.className("  ")
                + " " + ModTemplate.className("3D Arrows"), "MyCoolMod MyMod Mod3DArrows");
        Path first = ModTemplate.create(folder, "Rainbow Sheep");
        Path second = ModTemplate.create(folder, "Rainbow Sheep");
        check("a taken name gets a number", first.getFileName() + " " + second.getFileName(), "RainbowSheep.java RainbowSheep2.java");
        String code = Files.readString(first);
        check("the new mod is ready to play", code.contains("public class RainbowSheep extends EasyMod")
                && code.contains("say(\"Rainbow Sheep is working!\");") && code.contains("onKey(\"H\""), true);
        check("a quote in the name can't break the code", ModTemplate.text("Bob's \"Mod\"", "BobSMod").contains("say(\"Bob's 'Mod' is working!\");"), true);
        InstalledMod sheep = InstalledMod.list(folder).stream().filter(m -> m.file().equals(first)).findFirst().orElseThrow();
        check("a .java mod is listed as your own Squid mod", sheep.name() + " | " + sheep.squidMod() + " | " + sheep.source(),
                "Rainbow Sheep | true | true");
        InstalledMod sheepOff = sheep.toggle();
        jar(folder.resolve("quilty.jar"), "quilt.mod.json", "{\"quilt_loader\": {\"version\": \"2.0\", \"metadata\": {\"name\": \"Quilty\"}}}");
        jar(folder.resolve("neo.jar"), "META-INF/neoforge.mods.toml", "[[mods]]\nmodId=\"neo\"\nversion=\"${file.jarVersion}\"\ndisplayName=\"Neo Thing\"\n");
        Map<String, String> kinds = new java.util.TreeMap<>();
        for (InstalledMod m : InstalledMod.list(folder)) kinds.put(m.name(), m.kind() == null ? "none" : m.kind().label() + " " + m.version());
        check("Kelp tells which loader each mod is for", kinds.toString(),
                "{Neo Thing=NeoForge , Quilty=Quilt 2.0, Rainbow Sheep=Squid , Rainbow Sheep 2=Squid , Zoom=Squid 1.0.0, broken=none, fabric-thing=Fabric }");
        check("Quilt runs Fabric mods, Fabric doesn't run Quilt mods", Loader.QUILT.runs(Loader.FABRIC) + " " + Loader.FABRIC.runs(Loader.QUILT), "true false");
        check("a .java mod can be turned off", Files.exists(folder.resolve("RainbowSheep.java.disabled")) && !sheepOff.enabled()
                && InstalledMod.list(folder).stream().anyMatch(m -> m.name().equals("Rainbow Sheep") && !m.enabled()), true);
        check("jars aren't .java mods", zoom.source(), false);
    }

    // ---- Accounts ----

    static void accounts() {
        check("the first time, the old player name becomes an offline account",
                Accounts.all().stream().map(a -> a.name() + (a.microsoft() ? " (Microsoft)" : " (offline)")).toList().toString(),
                "[Player (offline)]");
        Accounts.add(Account.offline("Sam_2"));
        check("a new account is the one you play as", Accounts.active().name(), "Sam_2");
        Accounts.setActive(Accounts.all().get(0));
        check("switching accounts", Accounts.active().name(), "Player");
        Accounts.reload();
        check("accounts are remembered", Accounts.all().size() + " " + Accounts.active().name(), "2 Player");
        Accounts.add(Account.offline("Sam_2"));
        check("adding the same one again doesn't double it", Accounts.all().size(), 2);
        check("no Microsoft account yet", Accounts.hasMicrosoft(), false);
        Accounts.add(new Account("0123456789abcdef0123456789abcdef", "Samuel", true, "refresh", "token", 5));
        Accounts.reload();
        Account ms = Accounts.active();
        check("a Microsoft account keeps its keys", ms.microsoft() + " " + ms.refreshToken() + " " + ms.accessToken() + " " + ms.expiresAt(),
                "true refresh token 5");
        check("an old token counts as signed out", ms.signedIn(System.currentTimeMillis()), false);
        Accounts.remove(ms);
        check("removing the account you play as switches to another", Accounts.active().name(), "Player");
        Accounts.remove(Accounts.active());
        Accounts.remove(Accounts.active());
        check("with every account removed, you play as Player", Accounts.active().name(), "Player");
    }

    /** Signs in against a pretend Microsoft, Xbox and Minecraft, so every step and problem can be checked. */
    static void microsoftLogin() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> mode = new java.util.concurrent.atomic.AtomicReference<>("ok");
        AtomicInteger polls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String auth = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = 200;
            String reply;
            if (path.equals("/ms/devicecode")) {
                reply = "{\"user_code\": \"ABCD1234\", \"device_code\": \"dev\", \"verification_uri\": \"https://www.microsoft.com/link\", "
                        + "\"interval\": 0, \"expires_in\": 900}";
            } else if (path.equals("/ms/token") && body.contains("device_code")) {
                if (polls.incrementAndGet() < 3) { // the code isn't typed in yet
                    status = 400;
                    reply = "{\"error\": \"authorization_pending\"}";
                } else {
                    reply = "{\"access_token\": \"ms-access\", \"refresh_token\": \"ms-refresh\"}";
                }
            } else if (path.equals("/ms/token")) {
                boolean good = body.contains("refresh_token=ms-refresh");
                status = good ? 200 : 400;
                reply = good ? "{\"access_token\": \"ms-access\", \"refresh_token\": \"ms-refresh-2\"}" : "{\"error\": \"invalid_grant\"}";
            } else if (path.equals("/xbox/user/authenticate") && body.contains("\"d=ms-access\"")) {
                reply = "{\"Token\": \"xbl\", \"DisplayClaims\": {\"xui\": [{\"uhs\": \"hash\"}]}}";
            } else if (path.equals("/xsts/xsts/authorize") && body.contains("[\"xbl\"]")) {
                if (mode.get().equals("kid")) {
                    status = 401;
                    reply = "{\"XErr\": 2148916238, \"Message\": \"\"}";
                } else {
                    reply = "{\"Token\": \"xsts\", \"DisplayClaims\": {\"xui\": [{\"uhs\": \"hash\"}]}}";
                }
            } else if (path.equals("/mc/authentication/login_with_xbox") && body.contains("XBL3.0 x=hash;xsts")) {
                reply = "{\"access_token\": \"mc-token\", \"expires_in\": 86400}";
            } else if (path.equals("/mc/minecraft/profile") && auth.equals("Bearer mc-token") && !mode.get().equals("no game")) {
                reply = "{\"id\": \"0123456789abcdef0123456789abcdef\", \"name\": \"Samuel\"}";
            } else {
                status = 404;
                reply = "{\"error\": \"NOT_FOUND\"}";
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        MicrosoftLogin login = new MicrosoftLogin("test-client",
                new MicrosoftLogin.Servers(base + "/ms", base + "/xbox", base + "/xsts", base + "/mc"));
        try {
            MicrosoftLogin.Code code = login.start();
            check("Microsoft gives a code to type", code.userCode() + " at " + code.page(), "ABCD1234 at https://www.microsoft.com/link");
            Account account = login.waitForSignIn(code, () -> false);
            check("keeps waiting until you've signed in", polls.get(), 3);
            check("signs all the way in to Minecraft", account.name() + " " + account.id() + " " + account.accessToken() + " "
                    + account.refreshToken(), "Samuel 0123456789abcdef0123456789abcdef mc-token ms-refresh");
            check("the token is good for a while", account.signedIn(System.currentTimeMillis()), true);
            check("signing in again later doesn't need the code", login.refresh(account).refreshToken(), "ms-refresh-2");
            Account broken = new Account(account.id(), account.name(), true, "revoked", null, 0);
            check("a sign-in that stopped working asks you to sign in again", problem(() -> login.refresh(broken)),
                    "Your sign-in for Samuel stopped working. Sign in again in Options > Accounts.");
            check("Cancel stops the waiting", login.waitForSignIn(code, () -> true), null);

            mode.set("kid");
            check("a kid's account explains what a grown-up needs to do", problem(() -> login.refresh(account)),
                    "This is a kid's account. A grown-up needs to add it to their Microsoft Family (family.microsoft.com), then try again.");
            mode.set("no game");
            check("an account without Minecraft is explained",
                    problem(() -> login.refresh(account)).startsWith("This Microsoft account doesn't own"), true);
        } finally {
            server.stop(0);
        }
        MicrosoftLogin offline = new MicrosoftLogin("test-client", new MicrosoftLogin.Servers("http://127.0.0.1:1/ms", "", "", ""));
        check("no internet is explained", problem(offline::start), "Couldn't sign in: no internet connection");
        check("sign-in waits for Mojang's approval", MicrosoftLogin.ready(), false);
    }

    static void squidReport() throws IOException {
        Path folder = Files.createDirectories(home.resolve("report-test"));
        check("no report yet", SquidReport.read(folder), null);
        Files.writeString(SquidReport.file(folder), "{\"status\": \"running\", \"mods\": [{\"id\": \"a\"}]}");
        SquidReport old = SquidReport.read(folder);
        check("a report from an older Squid still reads", old.status() + " " + old.modCount() + " " + old.problems(), "running 1 []");
        Files.writeString(SquidReport.file(folder), "{\"status\": \"running\", \"mods\": [], "
                + "\"problems\": [{\"mod\": \"Minimap\", \"error\": \"NoSuchMethodError: x\"}]}");
        check("problems are read", SquidReport.read(folder).problems().toString(), "[Minimap]");
        Files.writeString(SquidReport.file(folder), "{\"status\": \"running\", \"mods\": [], "
                + "\"skipped\": [{\"mod\": \"Compass\", \"reason\": \"it was made for Minecraft 26.2, not 26.3.\"}]}");
        check("skipped mods are read", SquidReport.read(folder).skipped().toString(),
                "[Skipped[mod=Compass, reason=it was made for Minecraft 26.2, not 26.3.]]");
        Files.writeString(SquidReport.file(folder), "{\"status\": \"runn");
        check("a half-written report is ignored", SquidReport.read(folder), null);
    }

    static void jar(Path file, String entry, String text) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(text.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    // ---- Downloading, with a tiny download server on this computer ----

    static void downloader() throws Exception {
        byte[] good = "hello from the test server".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = exchange.getRequestURI().getPath().equals("/missing") ? new byte[0] : good;
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().equals("/missing") ? 404 : 200, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            Path folder = home.resolve("downloads");
            Downloader downloader = new Downloader();
            Downloader.Job job = new Downloader.Job(base + "/file", folder.resolve("a/file.bin"), sha1(good), good.length);
            downloader.downloadAll(List.of(job));
            check("downloads a file and checks its fingerprint", Files.readString(job.file()), "hello from the test server");
            check("counts it", downloader.getFilesDone() + " file, " + downloader.getBytesDone() + " bytes", "1 file, " + good.length + " bytes");

            int before = requests.get();
            new Downloader().downloadAll(List.of(job));
            check("a file that's already there isn't downloaded again", requests.get() - before, 0);

            Downloader cancelled = new Downloader();
            cancelled.cancel();
            before = requests.get();
            check("after Cancel, nothing more downloads", problem(() -> cancelled.downloadAll(List.of(job))) + " "
                    + problem(() -> cancelled.fetchText(base + "/file", folder.resolve("x.txt"))) + " " + (requests.get() - before), "Cancelled. Cancelled. 0");

            Downloader.Job damaged = new Downloader.Job(base + "/file", folder.resolve("damaged.bin"), "0".repeat(40), good.length);
            before = requests.get();
            check("a damaged download is explained", problem(() -> new Downloader().downloadAll(List.of(damaged))),
                    "Couldn't download damaged.bin: it arrived damaged");
            check("it was tried 3 times", requests.get() - before, 3);
            check("and no half file was left behind", Files.exists(folder.resolve("damaged.bin.part")), false);

            Downloader.Job missing = new Downloader.Job(base + "/missing", folder.resolve("missing.bin"), null, 1);
            check("a missing file is explained", problem(() -> new Downloader().downloadAll(List.of(missing))),
                    "Couldn't download missing.bin: got error 404");

            Path saved = folder.resolve("list.json");
            check("downloads a small text file", downloader.fetchText(base + "/list", saved), "hello from the test server");
            server.stop(0);
            check("without internet, it uses the copy from last time", downloader.fetchText(base + "/list", saved), "hello from the test server");
            check("without internet and no copy, it explains", problem(() -> downloader.fetchText(base + "/list", folder.resolve("other.json"))),
                    "Couldn't download other.json: no internet connection");
        } finally {
            server.stop(0);
        }
    }

    static String sha1(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    }

    static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    // ---- Building the command that starts the game, from a made-up version ----

    static void launcher() throws Exception {
        Path lib = Folders.libraries().resolve("com/example/lib/1.0/lib-1.0.jar");
        Path nativesJar = Folders.libraries().resolve("com/example/natives/1.0/natives-1.0-natives-windows.jar");
        Files.createDirectories(lib.getParent());
        Files.writeString(lib, "pretend library");
        Files.createDirectories(nativesJar.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(nativesJar))) {
            zip.putNextEntry(new ZipEntry("thing.dll"));
            zip.write(1);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.closeEntry();
        }
        Files.createDirectories(Folders.assets().resolve("indexes"));
        Files.writeString(Folders.assets().resolve("indexes/7.json"), "{\"objects\": {}}");

        // A modern version, with Mojang's newer way of listing arguments
        String modern = """
                {"id": "test-new", "type": "release", "mainClass": "com.example.Main",
                 "javaVersion": {"component": "java-runtime-epsilon", "majorVersion": 25},
                 "assetIndex": {"id": "7"},
                 "arguments": {
                   "default-user-jvm": [{"value": ["-Xms2G", "-Xmx4G"]}],
                   "jvm": ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"],
                   "game": ["--username", "${auth_player_name}", "--version", "${version_name}",
                            {"rules": [{"action": "allow", "features": {"is_demo_user": true}}], "value": "--demo"}]},
                 "libraries": [
                   {"name": "com.example:lib:1.0", "downloads": {"artifact": {"path": "com/example/lib/1.0/lib-1.0.jar"}}},
                   {"name": "com.example:mac-only:1.0", "rules": [{"action": "allow", "os": {"name": "nothing-real"}}],
                    "downloads": {"artifact": {"path": "com/example/mac-only/1.0/mac-only-1.0.jar"}}}]}
                """;
        version("test-new", modern);
        Path game = home.resolve("game-new");
        List<String> cmd = Launcher.buildCommand("test-new", game, Account.offline("Samuel_A"), false, 0);
        String classpath = cmd.get(cmd.indexOf("-cp") + 1);
        check("uses the version's own Java", cmd.get(0).contains("java-runtime-epsilon"), true);
        check("Mojang's memory settings", cmd.subList(1, 3).toString(), "[-Xms2G, -Xmx4G]");
        check("classpath has the library and the game, but not the other system's library",
                classpath.contains("lib-1.0.jar") + " " + classpath.contains("test-new.jar") + " " + classpath.contains("mac-only"), "true true false");
        check("main class, then the game's settings", String.join(" ", cmd.subList(cmd.indexOf("com.example.Main"), cmd.size())),
                "com.example.Main --username Samuel_A --version test-new");
        check("game features like demo mode stay off", cmd.contains("--demo"), false);

        List<String> more = Launcher.buildCommand("test-new", game, Account.offline("Samuel_A"), false, 8);
        check("the memory setting replaces Mojang's", more.stream().filter(s -> s.startsWith("-Xm")).toList().toString(), "[-Xms2G, -Xmx8G]");

        // An old version, with natives packed in a jar and the classic argument list
        String old = """
                {"id": "test-old", "type": "release", "mainClass": "com.example.OldMain",
                 "assetIndex": {"id": "7"},
                 "minecraftArguments": "--username ${auth_player_name} --uuid ${auth_uuid}",
                 "libraries": [
                   {"name": "com.example:natives:1.0", "natives": {"windows": "natives-windows", "linux": "natives-windows", "osx": "natives-windows"},
                    "downloads": {"classifiers": {"natives-windows": {"path": "com/example/natives/1.0/natives-1.0-natives-windows.jar"}}}}]}
                """;
        version("test-old", old);
        List<String> oldCmd = Launcher.buildCommand("test-old", home.resolve("game-old"), Account.offline("Player"), false, 0);
        check("old versions run on Java 8", oldCmd.get(0).contains("jre-legacy"), true);
        check("old versions get 2 GB", oldCmd.contains("-Xmx2G"), true);
        check("their packed DLLs are unpacked, without the jar's own folder",
                Files.exists(Folders.versions().resolve("test-old/natives/thing.dll")) + " "
                        + Files.exists(Folders.versions().resolve("test-old/natives/META-INF")), "true false");
        check("offline players get Minecraft's own made-up ID",
                oldCmd.get(oldCmd.indexOf("--uuid") + 1), "a01e3843e5213998958af459800e4d11");
        check("Squid can't start versions older than Java 21",
                problem(() -> Launcher.buildCommand("test-old", home.resolve("game-old"), Account.offline("Player"), true, 0)),
                "Squid needs Java 21, but Minecraft test-old runs on Java 8. Pick the Vanilla loader to play it.");
        Account signedIn = new Account("0123456789abcdef0123456789abcdef", "Samuel", true, "refresh", "mc-token", Long.MAX_VALUE);
        List<String> msCmd = Launcher.buildCommand("test-old", home.resolve("game-old"), signedIn, false, 0);
        check("a Microsoft account plays with its own name and ID", msCmd.get(msCmd.indexOf("--username") + 1) + " "
                + msCmd.get(msCmd.indexOf("--uuid") + 1), "Samuel 0123456789abcdef0123456789abcdef");
    }

    static void deleteFolder(Path folder) throws IOException {
        try (var walk = Files.walk(folder)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    /** The Shaders setup: Fabric, plus Sodium and Iris from a pretend Modrinth. */
    static void shaders() throws Exception {
        check("Shaders runs on Fabric and runs Fabric mods", Loader.SHADERS.runtime() + " " + Loader.SHADERS.runs(Loader.FABRIC)
                + " " + Loader.SHADERS.runs(Loader.SQUID), "FABRIC true false");
        byte[] sodiumJar = "pretend sodium".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        List<String> agents = new java.util.ArrayList<>();
        server.createContext("/", exchange -> {
            agents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            if (path.equals("/project/sodium/version")) {
                try {
                    body = ("[{\"version_type\": \"alpha\", \"files\": [{\"primary\": true, \"filename\": \"sodium-alpha.jar\", \"url\": \"" + base + "/nope\"}]},"
                            + " {\"version_type\": \"release\", \"files\": [{\"primary\": false, \"filename\": \"sodium-sources.jar\", \"url\": \"" + base + "/nope\"},"
                            + " {\"primary\": true, \"filename\": \"sodium-fabric-0.9.2+mc26.3.jar\", \"url\": \"" + base + "/files/sodium.jar\","
                            + " \"hashes\": {\"sha1\": \"" + sha1(sodiumJar) + "\"}, \"size\": " + sodiumJar.length + "}]}]").getBytes(StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            } else if (path.equals("/files/sodium.jar")) {
                body = sodiumJar;
            } else if (path.equals("/project/empty/version")) {
                body = "[]".getBytes(StandardCharsets.UTF_8);
            } else {
                body = new byte[0];
            }
            exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String realApi = Modrinth.api;
        Modrinth.api = base;
        try {
            Path mods = home.resolve("shaders-test/mods");
            Downloader downloader = new Downloader();
            Downloader.Job job = Modrinth.latest("sodium", "fabric", "26.3", mods, downloader);
            check("Modrinth: the newest release, not an alpha, and its main file", job.file().getFileName().toString(), "sodium-fabric-0.9.2+mc26.3.jar");
            downloader.downloadAll(List.of(job));
            check("and it downloads and checks out", Files.readString(job.file()), "pretend sodium");
            check("Kelp says who it is to Modrinth", agents.get(0), Downloader.USER_AGENT);
            check("a mod that's there isn't added again", GameInstaller.hasMod(mods, "sodium") + " " + GameInstaller.hasMod(mods, "iris"), "true false");
            check("a project with nothing for this version is explained",
                    problem(() -> Modrinth.latest("empty", "fabric", "26.3", mods, new Downloader())), "empty doesn't have a version for Minecraft 26.3 yet.");
        } finally {
            Modrinth.api = realApi;
            server.stop(0);
        }
    }

    /** NeoForge and Forge: picking their version and reading what an installer makes. Real installs are tested by hand. */
    static void forge() throws Exception {
        check("NeoForge's way of writing Minecraft versions", ForgeInstallers.neoForgePrefix("1.21.1") + " " + ForgeInstallers.neoForgePrefix("1.21")
                + " " + ForgeInstallers.neoForgePrefix("26.3") + " " + ForgeInstallers.neoForgePrefix("26.3.1"), "21.1. 21.0. 26.3.0. 26.3.1.");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String reply = switch (path) {
                case "/neo/api/maven/versions/releases/net/neoforged/neoforge" ->
                        "{\"versions\": [\"26.2.0.9\", \"26.3.0.9-beta\", \"26.3.0.55-beta\", \"26.3.1.2\", \"26.3.0.100-beta\"]}";
                case "/promos.json" -> "{\"promos\": {\"26.2-latest\": \"65.0.1\", \"26.2-recommended\": \"65.0.0\", \"26.3-latest\": \"66.0.9\"}}";
                default -> "";
            };
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String realNeo = ForgeInstallers.neoForgeMaven;
        String realPromos = ForgeInstallers.forgePromotions;
        ForgeInstallers.neoForgeMaven = base + "/neo";
        ForgeInstallers.forgePromotions = base + "/promos.json";
        try {
            check("NeoForge: the newest beta when there's no release yet", ForgeInstallers.neoForgeVersion("26.3", new Downloader()), "26.3.0.100-beta");
            check("NeoForge: a release when there is one", ForgeInstallers.neoForgeVersion("26.3.1", new Downloader()), "26.3.1.2");
            check("Forge: recommended first, else latest", ForgeInstallers.forgeVersion("26.2", new Downloader()) + " "
                    + ForgeInstallers.forgeVersion("26.3", new Downloader()), "65.0.0 66.0.9");
            check("a Minecraft version Forge doesn't have yet is explained",
                    problem(() -> ForgeInstallers.forgeVersion("26.4", new Downloader())), "Forge doesn't support Minecraft 26.4 yet.");
        } finally {
            ForgeInstallers.neoForgeMaven = realNeo;
            ForgeInstallers.forgePromotions = realPromos;
            server.stop(0);
        }
        Path installer = home.resolve("fake-installer.jar");
        jar(installer, "install_profile.json", "{\"version\": \"neoforge-26.3.0.55-beta\", \"minecraft\": \"26.3\"}");
        check("reads which version an installer makes", ForgeInstallers.profileId(installer), "neoforge-26.3.0.55-beta");
        jar(installer, "install_profile.json", "{\"version\": \"../../escape\"}");
        check("an installer with a strange version name is refused", problem(() -> ForgeInstallers.profileId(installer)),
                "fake-installer.jar doesn't say which version it makes.");
    }

    /** Mods dropped onto the Mods screen (or picked with Add Mod) are copied in, and other files are explained. */
    static void projects() throws Exception {
        VersionManifest.Version v = new VersionManifest.Version("26.3", "release", "", "");
        Instance instance = Instance.create("Project Test", v, Loader.SQUID);
        Path folder = ModProject.create(instance.mods(), "Mega Mod!", "26.3");
        check("a project is a folder named like its class", folder.getFileName().toString(), "MegaMod");
        check("it has code, a place for pictures, and a squid.json", Files.exists(folder.resolve("src/MegaMod.java")) + " "
                + Files.isDirectory(folder.resolve("resources")) + " " + Json.object(Json.parse(Files.readString(folder.resolve("squid.json")))).get("name"),
                "true true Mega Mod!");
        Map<String, Object> vsCode = Json.object(Json.parse(Files.readString(folder.resolve(".vscode/settings.json"))));
        check("VS Code is told about Squid and Minecraft", Json.array(Json.object(vsCode.get("java.project.referencedLibraries")).get("include")).toString(),
                List.of(Folders.squid().resolve("library").resolve("squid-api.jar").toString(),
                        Folders.versions().resolve("26.3").resolve("26.3.jar").toString()).toString());
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(folder.resolve(".idea/MegaMod.iml").toFile());
        check("IntelliJ gets a module", Files.exists(folder.resolve(".idea/modules.xml")), true);
        check("a second one with the same name gets a number", ModProject.create(instance.mods(), "Mega Mod", "26.3").getFileName().toString(), "MegaMod2");
        check("an easy mod can't take a project's name", ModTemplate.create(instance.mods(), "Mega Mod").getFileName().toString(), "MegaMod3.java");

        InstalledMod listed = InstalledMod.list(instance.mods()).stream().filter(m -> m.file().equals(folder)).findFirst().orElseThrow();
        check("Kelp lists a project as a Squid mod you can edit and pack", listed.name() + " " + listed.version() + " "
                + listed.squidMod() + " " + listed.source() + " " + listed.project(), "Mega Mod! 1.0 true true true");
        Files.writeString(folder.resolve("resources/picture.png"), "pretend picture");
        Path packed = ModProject.pack(folder, Files.createDirectories(home.resolve("pack-test")));
        check("Pack makes one .squid file", packed.getFileName().toString(), "MegaMod.squid");
        List<String> entries = new java.util.ArrayList<>();
        Map<String, Object> packedJson;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(packed.toFile())) {
            zip.stream().forEach(e -> entries.add(e.getName()));
            packedJson = Json.object(Json.parse(new String(zip.getInputStream(zip.getEntry("squid.json")).readAllBytes(), StandardCharsets.UTF_8)));
        }
        check("it has the code, pictures and squid.json, but not the editor settings", entries.toString(),
                "[squid.json, src/MegaMod.java, resources/picture.png]");
        check("its squid.json is filled in, so renaming the file can't break it",
                packedJson.get("id") + " " + packedJson.get("name") + " " + packedJson.get("version") + " " + packedJson.get("main"),
                "mega-mod Mega Mod! 1.0 MegaMod");
        Files.copy(packed, instance.mods().resolve("Shared.squid"));
        InstalledMod shared = InstalledMod.list(instance.mods()).stream()
                .filter(m -> m.file().getFileName().toString().equals("Shared.squid")).findFirst().orElseThrow();
        check("a .squid file is listed by its squid.json", shared.name() + " " + shared.squidMod() + " " + shared.project(), "Mega Mod! true false");
        InstalledMod off = listed.toggle();
        check("a project can be turned off", off.file().getFileName() + " " + InstalledMod.list(instance.mods()).stream()
                .filter(m -> m.file().equals(off.file())).findFirst().map(InstalledMod::enabled).orElse(null), "MegaMod.disabled false");
    }

    static void updates() throws Exception {
        check("newer versions", Updates.newer("0.2", "0.1") + " " + Updates.newer("0.10", "0.9") + " " + Updates.newer("1.0", "0.12")
                + " " + Updates.newer("0.1", "0.1") + " " + Updates.newer("0.1", "0.2"), "true true true false false");

        // A pretend GitHub release with a new kelp.jar and squid.zip
        byte[] newKelp = "new kelp".getBytes();
        java.io.ByteArrayOutputStream squidZip = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(squidZip)) {
            for (String[] file : new String[][] {{"squid.jar", "new squid"}, {"builtin/store.jar", "new store"}, {"library/squid-api.jar", "api"}}) {
                zip.putNextEntry(new ZipEntry(file[0]));
                zip.write(file[1].getBytes());
                zip.closeEntry();
            }
        }
        byte[] newSquid = squidZip.toByteArray();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String[] json = {""};
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = path.equals("/update.json") ? json[0].getBytes() : path.equals("/kelp.jar") ? newKelp
                    : path.equals("/squid.zip") ? newSquid : path.equals("/broken.jar") ? "tampered".getBytes() : new byte[0];
            exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            json[0] = "{\"version\": \"0.2\", \"notes\": \"Kelp 0.2\","
                    + " \"kelp\": {\"url\": \"" + base + "/kelp.jar\", \"sha256\": \"" + sha256(newKelp) + "\", \"size\": " + newKelp.length + "},"
                    + " \"squid\": {\"url\": \"" + base + "/squid.zip\", \"sha256\": \"" + sha256(newSquid) + "\", \"size\": " + newSquid.length + "}}";
            Updates.manifest = base + "/update.json";
            Updates.Release release = Updates.newest();
            check("the newest release is read", release.version() + " " + release.kelp().size(), "0.2 " + newKelp.length);

            // An old Kelp, with its own Squid next to it
            Path app = Files.createDirectories(home.resolve("update-test-app"));
            Files.writeString(app.resolve("kelp.jar"), "old kelp");
            Files.createDirectories(app.resolve("squid/builtin"));
            Files.writeString(app.resolve("squid/squid.jar"), "old squid");
            Files.writeString(app.resolve("squid/builtin/gone.jar"), "a part the new Squid doesn't have");
            Updates.download(release, Updates.folder(app));
            check("an update downloads next to Kelp", Files.exists(Updates.folder(app).resolve("ready.txt")) + " "
                    + Files.readString(Updates.folder(app).resolve("kelp.jar")), "true new kelp");
            Updater.install(app);
            check("putting it in replaces Kelp and Squid", Files.readString(app.resolve("kelp.jar")) + " | " + Files.readString(app.resolve("squid/squid.jar"))
                    + " | " + Files.readString(app.resolve("squid/builtin/store.jar")) + " | " + Files.exists(app.resolve("squid/builtin/gone.jar"))
                    + " | " + Files.exists(Updates.folder(app).resolve("ready.txt")), "new kelp | new squid | new store | false | false");

            // A file that doesn't match its fingerprint is thrown away, and the old Kelp stays
            Updates.Release tampered = new Updates.Release("0.3", "", new Updates.Download(base + "/broken.jar", sha256(newKelp), "tampered".length()),
                    release.squid());
            Path second = Files.createDirectories(home.resolve("update-test-app2"));
            check("a damaged download isn't used", problem(() -> Updates.download(tampered, Updates.folder(second))) + " "
                    + Files.exists(Updates.folder(second).resolve("kelp.jar")) + " " + Files.exists(Updates.folder(second).resolve("ready.txt")),
                    "kelp.jar arrived damaged, so it wasn't used false false");
            check("nothing to put in is explained", problem(() -> Updater.install(second)), "there's no finished download to put in");
        } finally {
            server.stop(0);
        }
    }

    static void crashHelper() throws Exception {
        Path mods = Files.createDirectories(home.resolve("crash-test-mods"));
        jar(mods.resolve("xray.jar"), "fabric.mod.json", "{\"id\": \"xray\", \"name\": \"X-Ray\"}");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(mods.resolve("minimap.jar")))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write("{\"id\": \"minimap\", \"name\": \"Minimap\"}".getBytes());
            zip.putNextEntry(new ZipEntry("com/example/minimap/Map.class"));
            zip.write(new byte[] {1});
            zip.closeEntry();
        }
        List<InstalledMod> installed = InstalledMod.list(mods);

        CrashHelper.Diagnosis memory = CrashHelper.diagnose("java.lang.OutOfMemoryError: Java heap space", "", installed, 1, "26.3");
        check("out of memory", memory.what() + " | " + memory.fix(), "Minecraft ran out of memory. | Give it more in Kelp's Options > Memory, or play with fewer mods.");

        CrashHelper.Diagnosis fabric = CrashHelper.diagnose("Incompatible mods found!\n\t - Mod 'X-Ray' (xray) 1.0.0 requires any version of mod fabric-api, which is missing!",
                "", installed, 1, "26.3");
        check("a Fabric mod missing what it needs", fabric.what() + " | " + fabric.fix() + " | " + fabric.culprit().name(),
                "X-Ray needs fabric-api, which isn't installed. | Add fabric-api for Minecraft 26.3, or turn X-Ray off. | X-Ray");

        CrashHelper.Diagnosis neoforge = CrashHelper.diagnose("Missing or unsupported mandatory dependencies:\n\tMod ID: 'geckolib', Requested by: 'mowzies', "
                + "Expected range: '[4.0,)', Actual version: '[MISSING]'", "", installed, 1, "26.3");
        check("a NeoForge mod missing what it needs", neoforge.what(), "mowzies needs geckolib, which isn't installed.");

        CrashHelper.Diagnosis oldMod = CrashHelper.diagnose("java.lang.NoSuchMethodError: 'void net.minecraft.client.Gui.render()'\n"
                + "\tat com.example.minimap.Map.draw(Map.java:12)\n\tat net.minecraft.client.Gui.render(Gui.java:5)", "", installed, 1, "26.3");
        check("a mod made for another version is found from the stack", oldMod.what() + " | " + oldMod.culprit().name(),
                "Minimap was made for a different Minecraft version. | Minimap");

        CrashHelper.Diagnosis driver = CrashHelper.diagnose("GLFW error 65542: WGL: The driver does not appear to support OpenGL", "", installed, 1, "26.3");
        check("a graphics driver problem", driver.what(), "Your graphics driver had a problem.");

        CrashHelper.Diagnosis suspected = CrashHelper.diagnose("", "---- Minecraft Crash Report ----\nDescription: Rendering overlay\nSuspected Mods: Minimap (minimap)\n",
                installed, 1, "26.3");
        check("the crash report's suspected mod", suspected.what() + " | " + suspected.culprit().name(), "It looks like Minimap caused the crash. | Minimap");

        CrashHelper.Diagnosis unknown = CrashHelper.diagnose("", "---- Minecraft Crash Report ----\nDescription: Ticking entity\n", List.of(), 1, "26.3");
        check("anything else says what Minecraft said", unknown.what() + " | " + unknown.fix(),
                "Minecraft crashed: Ticking entity | Try again. If it keeps happening, copy the report and ask for help.");
    }

    static void backups() throws Exception {
        VersionManifest.Version v = new VersionManifest.Version("26.3", "release", "", "");
        Instance instance = Instance.create("Backup Test", v, Loader.VANILLA);
        Path world = Files.createDirectories(Worlds.saves(instance).resolve("My Base"));
        Files.writeString(world.resolve("level.dat"), "the world");
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("region/r.0.0.mca"), "blocks");
        Files.writeString(world.resolve("session.lock"), "in use");

        check("a changed world gets backed up", Backups.backUpChanged(instance), 1);
        check("an unchanged one doesn't", Backups.backUpChanged(instance), 0);
        Backups.Backup first = Backups.list(instance, "My Base").get(0);
        List<String> entries = new java.util.ArrayList<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(first.file().toFile())) {
            zip.stream().forEach(e -> entries.add(e.getName()));
        }
        check("a backup has the world, but not the lock file", entries.stream().sorted().toList().toString(), "[My Base/level.dat, My Base/region/r.0.0.mca]");

        Files.writeString(world.resolve("level.dat"), "the world, changed");
        Files.setLastModifiedTime(world.resolve("level.dat"), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 2000));
        Thread.sleep(1100); // backups are named by the second
        check("changing it makes it back up again", Backups.backUpChanged(instance), 1);
        for (int n = 0; n < Backups.KEEP + 2; n++) {
            Path old = Backups.folder(instance, "My Base").resolve("2026-01-0" + (n % 9 + 1) + "_10-00-" + String.format("%02d", n) + ".zip");
            Files.copy(first.file(), old, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Backups.backUp(instance, world);
        List<Backups.Backup> kept = Backups.list(instance, "My Base");
        check("only the newest " + Backups.KEEP + " are kept", kept.size(), Backups.KEEP);

        Path restored = Backups.restore(instance, kept.get(0));
        check("restoring adds a new world and keeps the original", restored.getFileName().toString().startsWith("My Base (backup ") + " "
                + Files.readString(restored.resolve("level.dat")) + " | " + Files.readString(world.resolve("level.dat")),
                "true the world, changed | the world, changed");
    }

    static void stats() throws Exception {
        VersionManifest.Version v = new VersionManifest.Version("26.3", "release", "", "");
        Instance instance = Instance.create("Stats Test", v, Loader.VANILLA);
        instance.markPlayed();
        instance.addPlayTime(90 * 60_000);
        instance.addPlayTime(30 * 60_000);
        Instance read = Instance.find(instance.id());
        check("play time and times played add up", GameStats.duration(read.playMillis()) + " " + read.timesPlayed(), "2h 0m 1");

        for (String world : new String[] {"A", "B"}) {
            Path stats = Files.createDirectories(Worlds.saves(instance).resolve(world).resolve("stats"));
            Files.writeString(stats.getParent().resolve("level.dat"), "x");
            Files.writeString(stats.resolve("player.json"), "{\"stats\": {\"minecraft:custom\": {\"minecraft:play_time\": 72000, "
                    + "\"minecraft:deaths\": 2, \"minecraft:mob_kills\": 10, \"minecraft:jump\": 300, \"minecraft:walk_one_cm\": 150000, "
                    + "\"minecraft:sprint_one_cm\": 50000}, \"minecraft:mined\": {\"minecraft:stone\": 100, \"minecraft:dirt\": 25}, "
                    + "\"minecraft:crafted\": {\"minecraft:torch\": 8}}}");
        }
        GameStats total = GameStats.read(instance);
        check("Minecraft's stats add up over every world", GameStats.duration(total.ticksPlayed() * 50) + " " + total.blocksMined() + " " + total.itemsCrafted()
                + " " + total.mobsKilled() + " " + total.deaths() + " " + total.jumps() + " " + total.walkedCm(), "2h 0m 250 16 20 4 600 400000");

        Path shots = Files.createDirectories(instance.folder().resolve("screenshots"));
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", shots.resolve("old.png").toFile());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", shots.resolve("new.png").toFile());
        Files.setLastModifiedTime(shots.resolve("new.png"), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        Files.writeString(shots.resolve("notes.txt"), "not a picture");
        check("the gallery shows pictures, newest first", GalleryScreen.list(shots).stream().map(f -> f.getFileName().toString()).toList().toString(), "[new.png, old.png]");
        check("pictures get a small copy for the grid", GalleryScreen.thumbnail(shots.resolve("new.png")) != null, true);
        Path clips = Files.createDirectories(instance.folder().resolve("clips"));
        Files.writeString(clips.resolve("clip-1.avi"), "video");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 36, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", clips.resolve("clip-1.jpg").toFile());
        Files.setLastModifiedTime(clips.resolve("clip-1.avi"), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 120_000));
        check("clips show in the gallery too, by their picture", GalleryScreen.list(instance).stream().map(f -> f.getFileName().toString()).toList()
                + " " + (GalleryScreen.thumbnail(clips.resolve("clip-1.avi")) != null), "[clip-1.avi, new.png, old.png] true");
    }

    /** A zip with these files (name, text) in it. */
    static void zip(Path file, String... namesAndTexts) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i < namesAndTexts.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndTexts[i]));
                zip.write(namesAndTexts[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    static void modpacks() throws Exception {
        byte[] modBytes = "a fabric mod".getBytes();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = path.equals("/versions.json")
                    ? "{\"versions\": [{\"id\": \"26.3\", \"type\": \"release\", \"url\": \"x\", \"releaseTime\": \"2026-09-01T00:00:00+00:00\"}]}".getBytes()
                    : path.equals("/sodium.jar") ? modBytes : path.startsWith("/cf/1/") ? "curseforge mod".getBytes() : new byte[0];
            exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String realVersions = VersionManifest.url;
        String realCurseForge = ModpackImport.curseForgeDownload;
        VersionManifest.url = base + "/versions.json";
        ModpackImport.curseForgeDownload = base + "/cf/%d/%d";
        ModpackImport.ALLOWED_HOSTS.add("127.0.0.1");
        try {
            Path packs = Files.createDirectories(home.resolve("modpacks"));
            Path mrpack = packs.resolve("Cool Pack.mrpack");
            zip(mrpack, "modrinth.index.json", "{\"formatVersion\": 1, \"game\": \"minecraft\", \"name\": \"Cool Pack\", \"files\": ["
                    + "{\"path\": \"mods/sodium.jar\", \"hashes\": {\"sha1\": \"" + sha1(modBytes) + "\"}, \"downloads\": [\"" + base + "/sodium.jar\"], \"fileSize\": " + modBytes.length + "},"
                    + "{\"path\": \"mods/server-only.jar\", \"env\": {\"client\": \"unsupported\", \"server\": \"required\"}, \"hashes\": {}, \"downloads\": [\"" + base + "/x.jar\"]},"
                    + "{\"path\": \"mods/sneaky.jar\", \"hashes\": {\"sha1\": \"aa\"}, \"downloads\": [\"https://evil.example.com/x.jar\"]}],"
                    + " \"dependencies\": {\"minecraft\": \"26.3\", \"fabric-loader\": \"0.19.5\"}}",
                    "overrides/options.txt", "renderDistance:6", "overrides/config/sodium.json", "{}");
            Downloader downloader = new Downloader();
            ModpackImport.Result modrinth = ModpackImport.importPack(mrpack, downloader, stage -> { });
            Instance cool = modrinth.instance();
            check("a Modrinth pack becomes an instance with its version and loader", cool.name() + " | " + cool.version().id() + " | " + cool.loader(),
                    "Cool Pack | 26.3 | FABRIC");
            check("its mods download, server-only and off-site ones don't", Files.readString(cool.mods().resolve("sodium.jar")) + " "
                    + Files.exists(cool.mods().resolve("server-only.jar")) + " " + Files.exists(cool.mods().resolve("sneaky.jar")), "a fabric mod false false");
            check("its own files are copied in", Files.readString(cool.folder().resolve("options.txt")) + " " + Files.exists(cool.folder().resolve("config/sodium.json")),
                    "renderDistance:6 true");

            Path curse = packs.resolve("curse.zip");
            zip(curse, "manifest.json", "{\"name\": \"Curse Pack\", \"minecraft\": {\"version\": \"26.3\", \"modLoaders\": [{\"id\": \"neoforge-26.3.0.55-beta\", \"primary\": true}]},"
                    + " \"files\": [{\"projectID\": 1, \"fileID\": 10, \"required\": true}, {\"projectID\": 2, \"fileID\": 20, \"required\": true}], \"overrides\": \"overrides\"}");
            ModpackImport.Result curseForge = ModpackImport.importPack(curse, new Downloader(), stage -> { });
            check("a CurseForge pack: the mods that can be downloaded are, the others are listed", curseForge.instance().loader() + " "
                    + Files.exists(curseForge.instance().mods().resolve("curseforge-1-10.jar")) + " " + curseForge.missing().size(), "NEOFORGE true 1");

            Path prism = packs.resolve("prism.zip");
            zip(prism, "My Prism/mmc-pack.json", "{\"components\": [{\"uid\": \"net.minecraft\", \"version\": \"26.3\"}, {\"uid\": \"org.quiltmc.quilt-loader\", \"version\": \"0.30.1\"}]}",
                    "My Prism/.minecraft/mods/thing.jar", "a quilt mod", "My Prism/.minecraft/saves/World/level.dat", "x");
            ModpackImport.Result fromPrism = ModpackImport.importPack(prism, new Downloader(), stage -> { });
            check("a Prism Launcher instance comes over with its mods and worlds", fromPrism.instance().name() + " " + fromPrism.instance().loader() + " "
                    + Files.exists(fromPrism.instance().mods().resolve("thing.jar")) + " " + Files.exists(fromPrism.instance().folder().resolve("saves/World/level.dat")),
                    "My Prism QUILT true true");

            Path notPack = packs.resolve("photo.zip");
            zip(notPack, "photo.png", "x");
            check("something that isn't a modpack is explained", problem(() -> ModpackImport.importPack(notPack, new Downloader(), stage -> { })),
                    "That file isn't a modpack Kelp can read.");
        } finally {
            VersionManifest.url = realVersions;
            ModpackImport.curseForgeDownload = realCurseForge;
            ModpackImport.ALLOWED_HOSTS.remove("127.0.0.1");
            server.stop(0);
        }
    }

    static void themes() throws Exception {
        check("Kelp starts as the Ocean", Theme.current().id(), "ocean");
        check("its own themes", Theme.BUILT_IN.stream().map(Theme::name).toList().toString(), "[Ocean, Lava, Sky, Nether, End]");
        Path picture = home.resolve("my-picture.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 36, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", picture.toFile());
        Theme made = Theme.save("Purple Cave!", Theme.Scene.END, -1, 0x220033, 280, picture, null);
        check("a made theme is saved with its picture", made.id() + " " + made.scene() + " " + Integer.toHexString(made.base()) + " " + made.buttons() + " "
                + made.picture().getFileName(), "purple-cave END 220033 280 background.png");
        Theme.use(made);
        check("the picked theme is remembered", Settings.theme() + " " + Theme.find("purple-cave").name(), "purple-cave Purple Cave!");
        check("themes list Kelp's own, then yours", Theme.all().stream().map(Theme::id).toList().toString(), "[ocean, lava, sky, nether, end, purple-cave]");
        check("an unknown theme is the Ocean", Theme.find("nope").id(), "ocean");
        java.awt.image.BufferedImage red = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        red.setRGB(0, 0, 0xFFFF0000);
        int blue = Textures.hue(red, 240 / 360f).getRGB(0, 0);
        check("buttons can take any color", Integer.toHexString(blue), "ff0000ff");

        // The Store's themes: only "theme" items, checked against their fingerprints
        java.io.ByteArrayOutputStream themeZip = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(themeZip)) {
            zip.putNextEntry(new ZipEntry("theme.properties"));
            zip.write("name=Sunset\nscene=sky\nbase=#FF8844\nbuttons=20\n".getBytes());
            zip.closeEntry();
        }
        byte[] themeBytes = themeZip.toByteArray();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().getPath().equals("/sunset.zip") ? themeBytes : new byte[0];
            exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            List<ThemeStoreScreen.Item> items = ThemeStoreScreen.parse("{\"items\": [{\"id\": \"xray\", \"type\": \"mod\", \"url\": \"u\", \"sha256\": \"a\"},"
                    + "{\"id\": \"sunset\", \"type\": \"theme\", \"name\": \"Sunset\", \"author\": \"Samuel\", \"url\": \"" + base + "/sunset.zip\", \"sha256\": \"" + sha256(themeBytes) + "\"},"
                    + "{\"id\": \"../evil\", \"type\": \"theme\", \"url\": \"u\", \"sha256\": \"a\"}]}");
            check("the Store's themes, and only safe ones", items.stream().map(ThemeStoreScreen.Item::id).toList().toString(), "[sunset]");
            Theme sunset = ThemeStoreScreen.install(items.get(0));
            check("a Store theme installs", sunset.name() + " " + sunset.scene() + " " + Integer.toHexString(sunset.base()), "Sunset SKY ff8844");
            ThemeStoreScreen.Item tampered = new ThemeStoreScreen.Item("tampered", "T", "", base + "/sunset.zip", "00");
            check("a changed download isn't used", problem(() -> ThemeStoreScreen.install(tampered)), "it arrived damaged, so it wasn't installed");
        } finally {
            server.stop(0);
        }
        Theme.use(Theme.OCEAN);
    }

    static void addMods() throws Exception {
        VersionManifest.Version v = new VersionManifest.Version("26.3", "release", "", "");
        Instance instance = Instance.create("Drop Test", v, Loader.SQUID);
        Path downloads = Files.createDirectories(home.resolve("drop-test-downloads"));
        jar(downloads.resolve("xray.jar"), "squid.json", "{\"id\": \"xray\", \"name\": \"X-Ray\", \"version\": \"1.0\", \"main\": \"x\"}");
        jar(downloads.resolve("sodium.jar"), "fabric.mod.json", "{\"name\": \"Sodium\"}");
        Files.writeString(downloads.resolve("notes.txt"), "not a mod");
        ModsScreen screen = new ModsScreen(new OceanPanel(), null, instance);
        java.lang.reflect.Field notice = ModsScreen.class.getDeclaredField("notice");
        java.lang.reflect.Field problemField = ModsScreen.class.getDeclaredField("problem");
        notice.setAccessible(true);
        problemField.setAccessible(true);
        screen.filesDropped(List.of(downloads.resolve("xray.jar")));
        check("a dropped mod is copied in (the original can be deleted)", Files.exists(instance.mods().resolve("xray.jar")) + " "
                + Files.exists(downloads.resolve("xray.jar")) + " " + notice.get(screen), "true true Added xray.jar!");
        screen.filesDropped(List.of(downloads.resolve("sodium.jar")));
        check("a mod for another loader goes in, with a heads-up", notice.get(screen),
                "sodium.jar is a Fabric mod, so it won't load in this Squid instance.");
        screen.filesDropped(List.of(downloads.resolve("notes.txt")));
        check("a file that isn't a mod is explained", problemField.get(screen) + " " + Files.exists(instance.mods().resolve("notes.txt")),
                "notes.txt isn't a mod. Mods are .jar, .squid or .java files. false");
    }

    /** Bringing in worlds from a folder or a .zip. */
    static void worlds() throws Exception {
        Path saves = home.resolve("worlds-test/saves");
        Path elsewhere = Files.createDirectories(home.resolve("worlds-test/elsewhere"));
        Path plain = Files.createDirectories(elsewhere.resolve("My World"));
        Files.writeString(plain.resolve("level.dat"), "pretend level");
        Files.createDirectories(plain.resolve("region"));
        Files.writeString(plain.resolve("region/r.0.0.mca"), "pretend chunks");
        Files.writeString(plain.resolve("session.lock"), "in use");
        Path imported = Worlds.importWorld(plain, saves);
        check("a world folder is copied in, without the game's lock file", imported.getFileName() + " " + Files.readString(imported.resolve("region/r.0.0.mca"))
                + " " + Files.exists(imported.resolve("session.lock")), "My World pretend chunks false");
        check("importing it again keeps both", Worlds.importWorld(plain, saves).getFileName().toString(), "My World (2)");

        Path wrapper = Files.createDirectories(elsewhere.resolve("Download"));
        Files.createDirectories(wrapper.resolve("Skyblock"));
        Files.writeString(wrapper.resolve("Skyblock/level.dat"), "x");
        check("a world one folder deep is found", Worlds.importWorld(wrapper, saves).getFileName().toString(), "Skyblock");
        Path notAWorld = Files.createDirectories(elsewhere.resolve("Homework"));
        check("something that isn't a world is explained", problem(() -> Worlds.importWorld(notAWorld, saves)),
                "That isn't a Minecraft world: there's no level.dat in it.");
        check("the saves folder itself is refused", problem(() -> Worlds.importWorld(saves, saves)).startsWith("That folder has")
                || problem(() -> Worlds.importWorld(saves, saves)).startsWith("That's the instance's own saves folder"), true);

        Path zipRoot = elsewhere.resolve("Parkour Map.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipRoot))) {
            zip.putNextEntry(new ZipEntry("level.dat"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        check("a zip with the world right inside is named after the zip", Worlds.importWorld(zipRoot, saves).getFileName().toString(), "Parkour Map");
        Path zipNested = elsewhere.resolve("download-123.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipNested))) {
            zip.putNextEntry(new ZipEntry("Castle/level.dat"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        check("a zip with a world folder inside keeps the world's name", Worlds.importWorld(zipNested, saves).getFileName().toString(), "Castle");
        Path sneaky = elsewhere.resolve("sneaky.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(sneaky))) {
            zip.putNextEntry(new ZipEntry("../../escaped.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        check("a zip that tries to put files outside is refused", problem(() -> Worlds.importWorld(sneaky, saves)),
                "That zip has a file that would go outside the world, so it wasn't imported.");
        check("and nothing escaped or was left behind", Files.exists(home.resolve("worlds-test/escaped.txt")) + " "
                + Worlds.list(saves).stream().map(Worlds.World::name).sorted().toList(), "false [Castle, My World, My World (2), Parkour Map, Skyblock]");
    }

    static void defaultInstance() throws Exception {
        check("no Squid Count yet is 0", SquidCount.points("abc"), 0);
        Files.writeString(SquidCount.file(), "{\"players\": {\"abc\": {\"name\": \"Sam\", \"points\": 85, \"earned\": []}}}");
        check("Kelp reads the Squid Count", SquidCount.points("abc") + " " + SquidCount.points("someone-else"), "85 0");
        Files.delete(SquidCount.file());
        VersionManifest.Version v = new VersionManifest.Version("26.3", "release", "", "");
        check("the Play corner doesn't repeat what the name says", Instance.create("Squid 26.3", v, Loader.SQUID).summary() + " | "
                + Instance.create("Survival", v, Loader.SQUID).summary() + " | " + Instance.create("Minecraft 26.3", v, Loader.NEOFORGE).summary()
                + " | " + Instance.create("Plain 26.3", v, Loader.VANILLA).summary(),
                "Squid 26.3 | Survival (Minecraft 26.3 + Squid) | Minecraft 26.3 (NeoForge) | Plain 26.3");
        Instance beta = Instance.create("Beta Test", v, Loader.NEOFORGE);
        beta.setLoaderVersion("neoforge-26.3.0.55-beta");
        Instance stable = Instance.create("Stable Test", v, Loader.FORGE);
        stable.setLoaderVersion("26.3-forge-66.0.9");
        check("a beta loader is noticed", beta.loaderIsBeta() + " " + stable.loaderIsBeta(), "true false");
        Instance first = Instance.create("Default Test A", v, Loader.SQUID);
        Instance second = Instance.create("Default Test B", v, Loader.VANILLA);
        Settings.setLastInstance(second.id());
        check("without a default, Play starts the one played last", Instance.toPlay().id(), second.id());
        Settings.setDefaultInstance(first.id());
        check("with a default, Play always starts it", Instance.toPlay().id() + " " + first.isDefault() + " " + second.isDefault(),
                first.id() + " true false");
        Settings.setDefaultInstance(null);
        check("the default can be taken away", Instance.toPlay().id(), second.id());
        Settings.setDefaultInstance(first.id());
        first.delete();
        check("a deleted default falls back to the one played last", Instance.toPlay().id(), second.id());
        Settings.setDefaultInstance(null);
    }

    // ---- Loaders: Vanilla, Squid, Fabric, Quilt... ----

    static void loaders() throws Exception {
        check("unknown loader names are Vanilla", Loader.parse("FABRIC") + " " + Loader.parse("nonsense"), "FABRIC VANILLA");
        check("the loader button goes all the way round", Loader.FORGE.nextReady(), Loader.VANILLA);
        Path old = Folders.instances().resolve("Old Squid");
        Files.createDirectories(old);
        Files.writeString(old.resolve("instance.properties"), "name=Old Squid\nversion=26.3\nsquid=true\n");
        Instance oldInstance = Instance.find("Old Squid");
        check("instances from before loaders keep Squid", oldInstance.loader(), Loader.SQUID);
        oldInstance.setLoaderVersion("fabric-loader-1-26.3");
        oldInstance.setLoader(Loader.FABRIC);
        check("a new loader forgets the old loader's version", Instance.find("Old Squid").loader() + " " + Instance.find("Old Squid").loaderVersion(), "FABRIC null");

        check("picks the newest stable loader even from a list out of order", LoaderProfiles.pick(Json.array(Json.parse(
                "[{\"loader\": {\"version\": \"0.20.0-beta.9\"}}, {\"loader\": {\"version\": \"0.24.0\"}}, "
                + "{\"loader\": {\"version\": \"0.30.1\"}}, {\"loader\": {\"version\": \"0.9.0\"}}]"))), "0.30.1");
        check("versions compare by number, betas before releases", LoaderProfiles.compare("0.10.0", "0.9.9") + " "
                + LoaderProfiles.compare("1.0-beta.2", "1.0") + " " + LoaderProfiles.compare("1.0-beta.10", "1.0-beta.9"), "1 -1 1");
        check("Maven names become paths", LoaderProfiles.mavenPath("net.fabricmc:fabric-loader:0.19.5") + " "
                + LoaderProfiles.mavenPath("org.example:thing:1.0:natives-windows@zip"),
                "net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar org/example/thing/1.0/thing-1.0-natives-windows.zip");
        Map<String, Object> merged = LoaderProfiles.merge(
                Json.object(Json.parse("{\"mainClass\": \"mc.Main\", \"libraries\": [{\"name\": \"a:lib:1.0\"}, {\"name\": \"b:other:1.0\"}],"
                        + " \"arguments\": {\"jvm\": [\"-Dmc\"], \"game\": [\"--mc\"]}}")),
                Json.object(Json.parse("{\"mainClass\": \"loader.Main\", \"libraries\": [{\"name\": \"a:lib:2.0\"}],"
                        + " \"arguments\": {\"jvm\": [\"-Dloader\"], \"game\": []}}")));
        List<String> libraryNames = new java.util.ArrayList<>();
        for (Object l : Json.array(merged.get("libraries"))) libraryNames.add((String) Json.object(l).get("name"));
        check("merging: the loader's main class, its newer library, and both sets of arguments",
                merged.get("mainClass") + " " + libraryNames + " " + Json.object(merged.get("arguments")).get("jvm"),
                "loader.Main [a:lib:2.0, b:other:1.0] [-Dmc, -Dloader]");

        // Installing Fabric from a pretend Fabric server, then starting it
        byte[] loaderJar = "pretend fabric loader".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            if (path.equals("/fabric/versions/loader/test-new")) {
                body = "[{\"loader\": {\"version\": \"0.20.0-beta.1\", \"stable\": false}}, {\"loader\": {\"version\": \"0.19.5\", \"stable\": true}}]"
                        .getBytes(StandardCharsets.UTF_8);
            } else if (path.equals("/fabric/versions/loader/test-new/0.19.5/profile/json")) {
                try {
                    body = ("{\"id\": \"fabric-loader-0.19.5-test-new\", \"inheritsFrom\": \"test-new\", "
                            + "\"mainClass\": \"net.fabricmc.loader.impl.launch.knot.KnotClient\", "
                            + "\"arguments\": {\"game\": [], \"jvm\": [\"-DFabricMcEmu= net.minecraft.client.main.Main \"]}, "
                            + "\"libraries\": [{\"name\": \"net.fabricmc:fabric-loader:0.19.5\", \"url\": \"" + base + "/maven/\", "
                            + "\"sha1\": \"" + sha1(loaderJar) + "\", \"size\": " + loaderJar.length + "}]}").getBytes(StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            } else if (path.equals("/maven/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar")) {
                body = loaderJar;
            } else {
                body = new byte[0];
            }
            exchange.sendResponseHeaders(body.length == 0 ? 404 : 200, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String realMeta = LoaderProfiles.fabricMeta;
        LoaderProfiles.fabricMeta = base + "/fabric";
        try {
            Downloader downloader = new Downloader();
            Map<Path, Downloader.Job> jobs = new java.util.LinkedHashMap<>();
            String id = LoaderProfiles.install(Loader.FABRIC, "test-new", downloader, jobs);
            downloader.downloadAll(new java.util.ArrayList<>(jobs.values()));
            check("installs the newest stable Fabric", id, "fabric-loader-0.19.5-test-new");
            check("and its libraries", Files.readString(Folders.libraries().resolve("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar")),
                    "pretend fabric loader");
            List<String> cmd = Launcher.buildCommand("test-new", home.resolve("game-fabric"), Account.offline("Sam"), Loader.FABRIC, id, 0);
            String classpath = cmd.get(cmd.indexOf("-cp") + 1);
            check("Fabric starts the game", cmd.contains("net.fabricmc.loader.impl.launch.knot.KnotClient")
                    && !cmd.contains("com.example.Main") && cmd.contains("-DFabricMcEmu= net.minecraft.client.main.Main "), true);
            check("with Fabric and Minecraft's libraries", classpath.contains("fabric-loader-0.19.5.jar") + " " + classpath.contains("lib-1.0.jar")
                    + " " + classpath.contains("test-new.jar"), "true true true");
            check("a loader that isn't downloaded is explained", problem(() -> Launcher.buildCommand("test-new", home.resolve("game-fabric"),
                    Account.offline("Sam"), Loader.QUILT, "quilt-loader-9-test-new", 0)), "Quilt isn't downloaded yet. Play again with internet.");
        } finally {
            LoaderProfiles.fabricMeta = realMeta;
            server.stop(0);
        }
    }

    static void version(String id, String json) throws IOException {
        Path folder = Folders.versions().resolve(id);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(id + ".json"), json);
    }
}
