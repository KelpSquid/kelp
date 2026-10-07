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
        instances();
        gameOptions();
        mods();
        downloader();
        launcher();
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
                Files.readString(file).replace("\r\n", "|"), "version:5023|renderDistance:20|key_key.jump:key.keyboard.space|fullscreen:true|");

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
                "Squid needs Java 21, but Minecraft test-old runs on Java 8. Turn Squid off to play it.");
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

    static void version(String id, String json) throws IOException {
        Path folder = Folders.versions().resolve(id);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(id + ".json"), json);
    }
}
