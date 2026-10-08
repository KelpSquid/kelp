package kelp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Starts Minecraft: builds the long java command a version needs, then runs it. */
public final class Launcher {
    private static final String NAME = "Kelp";
    private static final String VERSION = "0.1";
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");

    private Launcher() {
    }

    /**
     * Starts a downloaded version in gameFolder (where its worlds, settings and mods go), with its loader:
     * Squid, or Fabric or Quilt from their version id (loaderVersion). memoryGb is how much memory to give it,
     * or 0 for Mojang's choice. Everything the game prints goes into kelp-output.log in the game folder.
     */
    public static Process launch(String versionId, Path gameFolder, Account account, Loader loader, String loaderVersion,
                                 int memoryGb) throws IOException {
        return launch(versionId, gameFolder, account, loader, loaderVersion, memoryGb, true);
    }

    /**
     * Like {@link #launch(String, Path, Account, Loader, String, int)}; fast false starts Squid the normal way even
     * when its fast boot is ready (Kelp does that when Squid says the fast boot files don't match anymore).
     */
    public static Process launch(String versionId, Path gameFolder, Account account, Loader loader, String loaderVersion,
                                 int memoryGb, boolean fast) throws IOException {
        return launch(versionId, gameFolder, account, loader, loaderVersion, memoryGb, fast, null);
    }

    /** Like the others; world (a world's folder name in saves, or null) is the world the game opens straight into. */
    public static Process launch(String versionId, Path gameFolder, Account account, Loader loader, String loaderVersion,
                                 int memoryGb, boolean fast, String world) throws IOException {
        return launch(versionId, gameFolder, account, loader, loaderVersion, memoryGb, fast, world, null);
    }

    /** Like the others; server (an address, or null) is a server the game joins straight away. */
    public static Process launch(String versionId, Path gameFolder, Account account, Loader loader, String loaderVersion,
                                 int memoryGb, boolean fast, String world, String server) throws IOException {
        return launch(versionId, gameFolder, account, loader, loaderVersion, memoryGb, fast, world, server, false);
    }

    /** Like the others; safe starts Squid with every mod off (Play Without Mods, after a crash). */
    public static Process launch(String versionId, Path gameFolder, Account account, Loader loader, String loaderVersion,
                                 int memoryGb, boolean fast, String world, String server, boolean safe) throws IOException {
        List<String> command = buildCommand(versionId, gameFolder, account, loader, loaderVersion, memoryGb, fast && !safe, world, server, safe);
        Files.deleteIfExists(SquidReport.file(gameFolder)); // so Kelp never shows last time's report
        Process game = new ProcessBuilder(command)
                .directory(gameFolder.toFile())
                .redirectErrorStream(true)
                .redirectOutput(gameFolder.resolve("kelp-output.log").toFile())
                .start();
        if (command.contains("-Dsquid.trainLater=true")) FastBoot.trainAfter(game, command, gameFolder);
        return game;
    }

    /** The command for Vanilla (withSquid false) or Squid (true). */
    public static List<String> buildCommand(String versionId, Path gameFolder, Account account, boolean withSquid,
                                            int memoryGb) throws IOException {
        return buildCommand(versionId, gameFolder, account, withSquid ? Loader.SQUID : Loader.VANILLA, null, memoryGb);
    }

    /** The full command that starts the game: java, its settings, then the game's own settings. */
    public static List<String> buildCommand(String versionId, Path gameFolder, Account account, Loader loader,
                                            String loaderVersion, int memoryGb) throws IOException {
        return buildCommand(versionId, gameFolder, account, loader, loaderVersion, memoryGb, true);
    }

    public static List<String> buildCommand(String versionId, Path gameFolder, Account account, Loader loader,
                                            String loaderVersion, int memoryGb, boolean fast) throws IOException {
        return buildCommand(versionId, gameFolder, account, loader, loaderVersion, memoryGb, fast, null);
    }

    /**
     * The full command; world (a world's folder name, or null) makes the game skip the title screen and open that
     * world, which Minecraft can do since 1.20 (its "quick play").
     */
    public static List<String> buildCommand(String versionId, Path gameFolder, Account account, Loader loader,
                                            String loaderVersion, int memoryGb, boolean fast, String world) throws IOException {
        return buildCommand(versionId, gameFolder, account, loader, loaderVersion, memoryGb, fast, world, null);
    }

    /** The full command; server (an address, or null) makes the game join that server straight away. */
    public static List<String> buildCommand(String versionId, Path gameFolder, Account account, Loader loader,
                                            String loaderVersion, int memoryGb, boolean fast, String world, String server) throws IOException {
        return buildCommand(versionId, gameFolder, account, loader, loaderVersion, memoryGb, fast, world, server, false);
    }

    /** The full command; safe starts Squid with every mod off, so a broken mod can't stop the game. */
    public static List<String> buildCommand(String versionId, Path gameFolder, Account account, Loader loader,
                                            String loaderVersion, int memoryGb, boolean fast, String world, String server,
                                            boolean safe) throws IOException {
        boolean withSquid = loader == Loader.SQUID;
        Path versionFolder = Folders.versions().resolve(versionId);
        Map<String, Object> details = Json.object(Json.parse(Files.readString(versionFolder.resolve(versionId + ".json"))));
        if (loaderVersion != null) {
            // Fabric and Quilt: their version file goes on top of Minecraft's
            Path loaderFile = Folders.versions().resolve(loaderVersion).resolve(loaderVersion + ".json");
            if (!Files.exists(loaderFile)) throw new IOException(loader.label() + " isn't downloaded yet. Play again with internet.");
            details = LoaderProfiles.merge(details, Json.object(Json.parse(Files.readString(loaderFile))));
        }
        Path natives = versionFolder.resolve("natives");
        Files.createDirectories(gameFolder);
        extractNatives(details, natives);

        String assetsId = (String) Json.object(details.get("assetIndex")).get("id");
        Path gameAssets = prepareOldAssets(assetsId, gameFolder);

        // Who's playing. Offline names have no token, so the game only lets them into single player and LAN.
        String token = account.accessToken() != null ? account.accessToken() : "0";
        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", account.name());
        vars.put("auth_uuid", account.id());
        vars.put("auth_access_token", token);
        vars.put("auth_session", token);
        vars.put("auth_xuid", "0");
        vars.put("clientid", account.microsoft() && MicrosoftLogin.ready() ? MicrosoftLogin.CLIENT_ID : "0");
        vars.put("user_type", account.microsoft() ? "msa" : "legacy");
        vars.put("user_properties", "{}");
        vars.put("version_name", versionId);
        vars.put("version_type", (String) details.get("type"));
        vars.put("game_directory", gameFolder.toString());
        vars.put("assets_root", Folders.assets().toString());
        vars.put("assets_index_name", assetsId);
        vars.put("game_assets", gameAssets.toString());
        vars.put("natives_directory", natives.toString());
        vars.put("library_directory", Folders.libraries().toString());
        vars.put("classpath_separator", File.pathSeparator);
        String gameClasspath = String.join(File.pathSeparator, classpath(details, versionFolder.resolve(versionId + ".jar")));
        String mainClass = (String) details.get("mainClass");
        List<String> squidSettings = new ArrayList<>();
        if (withSquid) {
            // Squid starts first. Only Squid goes on the normal classpath, and Squid loads Minecraft itself,
            // so mods can change Minecraft's code as it loads.
            checkSquidCanRun(details, versionId);
            List<String> fastClasspath = fast ? FastBoot.classpath(gameFolder) : null;
            if (fastClasspath == null) {
                vars.put("classpath", String.join(File.pathSeparator, squidJars()));
            } else {
                // Fast boot: Minecraft already patched, straight on Java's classpath (see FastBoot)
                List<String> classpath = new ArrayList<>(squidJars());
                classpath.addAll(fastClasspath);
                vars.put("classpath", String.join(File.pathSeparator, classpath));
                squidSettings.add("-Dsquid.fastBoot=" + FastBoot.folder(gameFolder));
                if (javaMajor(details) >= 25) { // Java's AOT cache came in Java 25
                    Path aot = FastBoot.aotCache(gameFolder);
                    if (aot != null) squidSettings.add("-XX:AOTCache=" + aot);
                    else squidSettings.add("-Dsquid.trainLater=true"); // make it after this game closes
                }
            }
            squidSettings.add("-Dsquid.gameClasspath=" + gameClasspath);
            squidSettings.add("-Dsquid.mainClass=" + mainClass);
            squidSettings.add("-Dsquid.home=" + Folders.home()); // where Squid keeps things shared by every instance, like the Squid Count
            if (!ParentControls.voiceAllowed()) squidSettings.add("-Dsquid.voice=off"); // a parent turned voice chat off
            if (safe) squidSettings.add("-Dsquid.safeMode=true"); // Play Without Mods
            mainClass = "squid.Main";
            Files.createDirectories(gameFolder.resolve("mods"));
        } else {
            vars.put("classpath", gameClasspath);
        }
        vars.put("launcher_name", NAME);
        vars.put("launcher_version", VERSION);

        List<String> command = new ArrayList<>();
        command.add(JavaRuntime.executable(JavaRuntime.componentFor(details)).toString());

        Map<String, Object> arguments = Json.object(details.get("arguments"));
        if (arguments != null && arguments.get("default-user-jvm") != null) {
            addArguments(command, arguments.get("default-user-jvm"), vars); // Mojang's suggested memory settings
        } else {
            command.add("-Xmx2G"); // older versions don't suggest any, so give the game 2 GB
        }
        if (memoryGb > 0) {
            // The player picked an amount in Settings, so it replaces Mojang's
            command.removeIf(arg -> arg.startsWith("-Xmx") || arg.startsWith("-Xms"));
            command.add("-Xms" + Math.min(2, memoryGb) + "G");
            command.add("-Xmx" + memoryGb + "G");
        } else {
            int small = smallComputerMemoryGb(Hardware.memoryGb());
            if (small > 0) {
                // Mojang's 4 GB (all of it claimed as the game starts) leaves too little for Windows on a computer with
                // 4 or 6 GB, and everything slows to a crawl as it swaps to disk
                command.removeIf(arg -> arg.startsWith("-Xmx") || arg.startsWith("-Xms") || arg.equals("-XX:+AlwaysPreTouch"));
                command.add("-Xms1G");
                command.add("-Xmx" + small + "G");
            }
        }
        if (arguments != null) {
            addArguments(command, arguments.get("jvm"), vars);
        } else {
            // Before 1.13, versions didn't list these, so use the classic ones
            command.add("-Djava.library.path=" + natives);
            command.add("-cp");
            command.add(vars.get("classpath"));
        }

        Map<String, Object> logging = Json.object(details.get("logging"));
        if (logging != null && logging.get("client") != null) {
            Map<String, Object> client = Json.object(logging.get("client"));
            String logFile = (String) Json.object(client.get("file")).get("id");
            vars.put("path", Folders.assets().resolve("log_configs").resolve(logFile).toString());
            command.add(fill((String) client.get("argument"), vars));
        }

        command.addAll(squidSettings);
        command.add(mainClass);

        if (arguments != null) {
            addArguments(command, arguments.get("game"), vars);
        } else {
            for (String part : ((String) details.get("minecraftArguments")).split(" ")) command.add(fill(part, vars));
        }
        if (world != null) {
            if (arguments == null || !String.valueOf(arguments.get("game")).contains("quickPlaySingleplayer")) {
                throw new IOException("Minecraft " + versionId + " can't open a world by itself. Play it, then pick the world.");
            }
            command.add("--quickPlaySingleplayer");
            command.add(world);
        } else if (server != null) {
            if (!ParentControls.multiplayerAllowed()) throw new IOException("A parent turned multiplayer off in Parent Controls.");
            if (arguments == null || !String.valueOf(arguments.get("game")).contains("quickPlayMultiplayer")) {
                throw new IOException("Minecraft " + versionId + " can't join a server by itself. Play it, then pick the server.");
            }
            command.add("--quickPlayMultiplayer");
            command.add(server);
        }
        command.addAll(ParentControls.gameArguments()); // Minecraft's own switches for no multiplayer or no chat
        return command;
    }

    /**
     * Whether this version (once it's downloaded) can open a world by itself, skipping the title screen, for Play
     * World and Continue. Minecraft can since 1.20.
     */
    static boolean canOpenWorld(String versionId) {
        try {
            return Files.readString(Folders.versions().resolve(versionId).resolve(versionId + ".json")).contains("quickPlaySingleplayer");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * squid.jar and its libraries. A packaged Kelp brings its own Squid in a squid folder next to kelp.jar;
     * otherwise Kelp uses the one Squid's build.bat installs.
     */
    private static List<String> squidJars() throws IOException {
        Path folder = Folders.squidInUse();
        if (!Files.exists(folder.resolve("squid.jar"))) {
            throw new IOException("Squid isn't installed. Run build.bat in the squid repo, or turn Squid off.");
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> p.toString().endsWith(".jar")).map(Path::toString).sorted().toList();
        }
    }

    private static int javaMajor(Map<String, Object> details) {
        Map<String, Object> javaVersion = Json.object(details.get("javaVersion"));
        return javaVersion == null ? 8 : ((Number) javaVersion.get("majorVersion")).intValue();
    }

    /** Squid is built for Java 21, so it can only start versions that run on Java 21 or newer. */
    private static void checkSquidCanRun(Map<String, Object> details, String versionId) throws IOException {
        Map<String, Object> javaVersion = Json.object(details.get("javaVersion"));
        int java = javaVersion == null ? 8 : ((Number) javaVersion.get("majorVersion")).intValue();
        if (java < 21) {
            throw new IOException("Squid needs Java 21, but Minecraft " + versionId + " runs on Java " + java
                    + ". Pick the Vanilla loader to play it.");
        }
    }

    /**
     * Minecraft and every library it needs, for a code editor to know them. Just the game's jar if this version
     * isn't downloaded yet.
     */
    static List<String> gameClasspath(String versionId) {
        Path versionFolder = Folders.versions().resolve(versionId);
        Path gameJar = versionFolder.resolve(versionId + ".jar");
        try {
            Map<String, Object> details = Json.object(Json.parse(Files.readString(versionFolder.resolve(versionId + ".json"))));
            return classpath(details, gameJar);
        } catch (IOException | RuntimeException e) {
            return List.of(gameJar.toString());
        }
    }

    /** Every library the game needs, plus the game itself, in the order Mojang lists them. */
    private static List<String> classpath(Map<String, Object> details, Path gameJar) {
        Set<String> paths = new LinkedHashSet<>();
        for (Object entry : Json.array(details.get("libraries"))) {
            Map<String, Object> library = Json.object(entry);
            if (!Rules.allowed(library.get("rules"))) continue;
            Map<String, Object> downloads = Json.object(library.get("downloads"));
            Map<String, Object> artifact = downloads == null ? null : Json.object(downloads.get("artifact"));
            if (artifact != null) paths.add(Folders.libraries().resolve((String) artifact.get("path")).toString());
        }
        paths.add(gameJar.toString());
        return new ArrayList<>(paths);
    }

    /**
     * How much memory to give the game on a computer with little of it (when the player hasn't picked an amount), or 0
     * to keep Mojang's. 2 GB on a 4 GB computer and 3 GB on a 6 GB one leave Windows enough room.
     */
    static int smallComputerMemoryGb(double computerGb) {
        if (computerGb <= 0) return 0; // Java couldn't tell
        if (computerGb <= 5) return 2;
        if (computerGb <= 7) return 3;
        return 0;
    }

    /** Adds Mojang's list of arguments, skipping the ones whose rules say they aren't for this computer. */
    private static void addArguments(List<String> command, Object list, Map<String, String> vars) {
        for (Object entry : Json.array(list)) {
            if (entry instanceof String s) {
                command.add(fill(s, vars));
                continue;
            }
            Map<String, Object> conditional = Json.object(entry);
            if (!Rules.allowed(conditional.get("rules"))) continue;
            Object value = conditional.get("value");
            if (value instanceof String s) {
                command.add(fill(s, vars));
            } else {
                for (Object part : Json.array(value)) command.add(fill((String) part, vars));
            }
        }
    }

    /** Swaps every ${name} in the text for its value. */
    private static String fill(String text, Map<String, String> vars) {
        Matcher m = VARIABLE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = vars.getOrDefault(m.group(1), m.group());
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Versions before 1.19 keep their DLLs packed in jars, which have to be unpacked before the game starts. */
    private static void extractNatives(Map<String, Object> details, Path natives) throws IOException {
        Files.createDirectories(natives);
        for (Object entry : Json.array(details.get("libraries"))) {
            Map<String, Object> library = Json.object(entry);
            Map<String, Object> nativesMap = Json.object(library.get("natives"));
            Map<String, Object> downloads = Json.object(library.get("downloads"));
            if (nativesMap == null || downloads == null || !Rules.allowed(library.get("rules"))) continue;
            String classifier = (String) nativesMap.get(Rules.osName());
            Map<String, Object> classifiers = Json.object(downloads.get("classifiers"));
            if (classifier == null || classifiers == null) continue;
            Map<String, Object> file = Json.object(classifiers.get(classifier.replace("${arch}", "64")));
            if (file == null) continue;

            Path jar = Folders.libraries().resolve((String) file.get("path"));
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry item;
                while ((item = zip.getNextEntry()) != null) {
                    if (item.isDirectory() || item.getName().startsWith("META-INF/")) continue;
                    Path target = natives.resolve(item.getName()).normalize();
                    if (!target.startsWith(natives)) continue; // never write outside the natives folder
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Versions before 1.7.3 can't read assets by fingerprint, so they get a copy under their real names.
     * Returns the folder those old versions read from (newer versions don't use it).
     */
    private static Path prepareOldAssets(String assetsId, Path gameFolder) throws IOException {
        Path virtual = Folders.assets().resolve("virtual").resolve(assetsId);
        Map<String, Object> index = Json.object(Json.parse(
                Files.readString(Folders.assets().resolve("indexes").resolve(assetsId + ".json"))));
        boolean isVirtual = Boolean.TRUE.equals(index.get("virtual"));
        boolean toResources = Boolean.TRUE.equals(index.get("map_to_resources"));
        if (!isVirtual && !toResources) return virtual;

        Path target = isVirtual ? virtual : gameFolder.resolve("resources");
        for (Map.Entry<String, Object> asset : Json.object(index.get("objects")).entrySet()) {
            String hash = (String) Json.object(asset.getValue()).get("hash");
            Path copy = target.resolve(asset.getKey());
            if (Files.exists(copy)) continue;
            Files.createDirectories(copy.getParent());
            try (InputStream in = Files.newInputStream(Folders.assets().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash))) {
                Files.copy(in, copy);
            }
        }
        return target;
    }
}
