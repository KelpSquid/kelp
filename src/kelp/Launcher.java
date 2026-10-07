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
        List<String> command = buildCommand(versionId, gameFolder, account, loader, loaderVersion, memoryGb);
        Files.deleteIfExists(SquidReport.file(gameFolder)); // so Kelp never shows last time's report
        return new ProcessBuilder(command)
                .directory(gameFolder.toFile())
                .redirectErrorStream(true)
                .redirectOutput(gameFolder.resolve("kelp-output.log").toFile())
                .start();
    }

    /** The command for Vanilla (withSquid false) or Squid (true). */
    public static List<String> buildCommand(String versionId, Path gameFolder, Account account, boolean withSquid,
                                            int memoryGb) throws IOException {
        return buildCommand(versionId, gameFolder, account, withSquid ? Loader.SQUID : Loader.VANILLA, null, memoryGb);
    }

    /** The full command that starts the game: java, its settings, then the game's own settings. */
    public static List<String> buildCommand(String versionId, Path gameFolder, Account account, Loader loader,
                                            String loaderVersion, int memoryGb) throws IOException {
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
            vars.put("classpath", String.join(File.pathSeparator, squidJars()));
            squidSettings.add("-Dsquid.gameClasspath=" + gameClasspath);
            squidSettings.add("-Dsquid.mainClass=" + mainClass);
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
        return command;
    }

    /**
     * squid.jar and its libraries. A packaged Kelp brings its own Squid in a squid folder next to kelp.jar;
     * otherwise Kelp uses the one Squid's build.bat installs.
     */
    private static List<String> squidJars() throws IOException {
        Path folder = Folders.app().resolve("squid");
        if (!Files.exists(folder.resolve("squid.jar"))) folder = Folders.squid();
        if (!Files.exists(folder.resolve("squid.jar"))) {
            throw new IOException("Squid isn't installed. Run build.bat in the squid repo, or turn Squid off.");
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> p.toString().endsWith(".jar")).map(Path::toString).sorted().toList();
        }
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
