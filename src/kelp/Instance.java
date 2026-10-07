package kelp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * One setup of Minecraft, like an instance in Prism: a name, a version, a {@link Loader} (Vanilla, Squid, Fabric...),
 * and its own folder for worlds, settings, screenshots and mods. Its details live in instance.properties.
 */
public final class Instance {
    private static final String FILE = "instance.properties";

    private final Path folder;
    private String name;
    private String versionId;
    private String versionType;
    private String versionUrl;
    private Loader loader;
    private String loaderVersion; // like "fabric-loader-0.19.5-26.3", once it's installed (Fabric and Quilt only)
    private long lastPlayed;

    private Instance(Path folder) {
        this.folder = folder;
    }

    /** Every instance, the most recently played first. */
    public static List<Instance> all() {
        List<Instance> instances = new ArrayList<>();
        if (!Files.isDirectory(Folders.instances())) return instances;
        try (Stream<Path> folders = Files.list(Folders.instances())) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) instances.add(load(folder));
        } catch (IOException e) {
            System.err.println("Couldn't list instances: " + e.getMessage());
        }
        instances.sort(Comparator.comparingLong(Instance::lastPlayed).reversed().thenComparing(i -> i.name().toLowerCase()));
        return instances;
    }

    /**
     * What the title screen's Play button starts: the default instance if one is set, else the one played last,
     * else the newest. Null when there are no instances yet.
     */
    public static Instance toPlay() {
        Instance chosen = find(Settings.defaultInstance());
        if (chosen == null) chosen = find(Settings.lastInstance());
        if (chosen == null) {
            List<Instance> all = all();
            if (!all.isEmpty()) chosen = all.get(0);
        }
        return chosen;
    }

    /**
     * What Play starts, in a few words: the name, plus the Minecraft version and loader only if the name doesn't
     * already say them. "Squid 26.3" stays "Squid 26.3"; "Survival" becomes "Survival (Minecraft 26.3 + Squid)".
     */
    public String summary() {
        String lower = name.toLowerCase();
        java.util.List<String> extra = new java.util.ArrayList<>();
        if (!lower.contains(versionId.toLowerCase())) extra.add("Minecraft " + versionId);
        if (loader != Loader.VANILLA && !lower.contains(loader.label().toLowerCase())) extra.add(loader.label());
        return extra.isEmpty() ? name : name + " (" + String.join(" + ", extra) + ")";
    }

    /** Whether the installed Fabric, Quilt, NeoForge or Forge is a beta (or alpha), which can crash more. */
    public boolean loaderIsBeta() {
        return loaderVersion != null && loaderVersion.toLowerCase().matches(".*(alpha|beta|-rc|-pre).*");
    }

    /** Whether the title screen's Play button always starts this one. */
    public boolean isDefault() {
        return id().equals(Settings.defaultInstance());
    }

    /** The instance in this folder (by folder name), or null if there isn't one. */
    public static Instance find(String folderName) {
        if (folderName == null) return null;
        Path folder = Folders.instances().resolve(folderName);
        return Files.isDirectory(folder) ? load(folder) : null;
    }

    /** Makes a new instance with its own folder. */
    public static Instance create(String name, VersionManifest.Version version, boolean squid) throws IOException {
        return create(name, version, squid ? Loader.SQUID : Loader.VANILLA);
    }

    /** Makes a new instance with its own folder. */
    public static Instance create(String name, VersionManifest.Version version, Loader loader) throws IOException {
        // The folder is named after the instance, made safe for every operating system, and never reused
        String base = name.trim().replaceAll("[^A-Za-z0-9 ._-]", "").replaceAll("[ .]+$", "");
        if (base.isEmpty()) base = "instance";
        if (base.matches("(?i)(con|prn|aux|nul|com[0-9]|lpt[0-9])")) base += " instance"; // names Windows won't allow
        Path folder = Folders.instances().resolve(base);
        for (int n = 2; Files.exists(folder); n++) folder = Folders.instances().resolve(base + " " + n);
        Files.createDirectories(folder);

        Instance instance = new Instance(folder);
        instance.name = name.trim();
        instance.versionId = version.id();
        instance.versionType = version.type();
        instance.versionUrl = version.url();
        instance.loader = loader;
        instance.save();
        return instance;
    }

    private static Instance load(Path folder) {
        Instance instance = new Instance(folder);
        Properties values = new Properties();
        Path file = folder.resolve(FILE);
        if (Files.exists(file)) {
            try (Reader in = Files.newBufferedReader(file)) {
                values.load(in);
            } catch (IOException e) {
                System.err.println("Couldn't read " + file + ": " + e.getMessage());
            }
        }
        // Folders from before Kelp had instances are named after their version, so that's the fallback
        String folderName = folder.getFileName().toString();
        instance.name = values.getProperty("name", folderName);
        instance.versionId = values.getProperty("version", folderName);
        instance.versionType = values.getProperty("versionType", "release");
        instance.versionUrl = values.getProperty("versionUrl", "");
        // Before Kelp had loaders, instances only said whether Squid was on
        instance.loader = values.getProperty("loader") != null ? Loader.parse(values.getProperty("loader"))
                : Boolean.parseBoolean(values.getProperty("squid", "false")) ? Loader.SQUID : Loader.VANILLA;
        instance.loaderVersion = values.getProperty("loaderVersion");
        instance.lastPlayed = Long.parseLong(values.getProperty("lastPlayed", "0"));
        return instance;
    }

    private void save() throws IOException {
        Properties values = new Properties();
        values.setProperty("name", name);
        values.setProperty("version", versionId);
        values.setProperty("versionType", versionType);
        values.setProperty("versionUrl", versionUrl);
        values.setProperty("loader", loader.name());
        if (loaderVersion != null) values.setProperty("loaderVersion", loaderVersion);
        values.setProperty("lastPlayed", String.valueOf(lastPlayed));
        try (Writer out = Files.newBufferedWriter(folder.resolve(FILE))) {
            values.store(out, "Kelp instance");
        }
    }

    public String name() {
        return name;
    }

    /** The folder's name, which never changes, so it's what Kelp remembers instances by. */
    public String id() {
        return folder.getFileName().toString();
    }

    public Path folder() {
        return folder;
    }

    public Path mods() {
        return folder.resolve("mods");
    }

    /** Whether this instance runs Squid. */
    public boolean squid() {
        return loader == Loader.SQUID;
    }

    public Loader loader() {
        return loader;
    }

    /** The installed Fabric or Quilt version id, or null. */
    public String loaderVersion() {
        return loaderVersion;
    }

    public long lastPlayed() {
        return lastPlayed;
    }

    /** The version, as Mojang's list describes it. The url can be empty for instances from before Kelp had them. */
    public VersionManifest.Version version() {
        return new VersionManifest.Version(versionId, versionType, versionUrl, "");
    }

    public void setSquid(boolean squid) throws IOException {
        setLoader(squid ? Loader.SQUID : Loader.VANILLA);
    }

    public void setLoader(Loader loader) throws IOException {
        if (loader != this.loader) loaderVersion = null; // a different loader gets installed next time
        this.loader = loader;
        save();
    }

    /** Remembers which Fabric or Quilt version got installed, so the game can start without internet next time. */
    public void setLoaderVersion(String loaderVersion) throws IOException {
        this.loaderVersion = loaderVersion;
        save();
    }

    /** Remembers where this version's details live, for instances that didn't know yet. */
    public void setVersion(VersionManifest.Version version) throws IOException {
        versionId = version.id();
        versionType = version.type();
        versionUrl = version.url();
        save();
    }

    public void markPlayed() throws IOException {
        lastPlayed = System.currentTimeMillis();
        save();
    }

    /** Deletes the instance's folder, with its worlds, settings and mods. */
    public void delete() throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); // insides first
        }
    }

    // Two Instance objects for the same folder are the same instance
    @Override
    public boolean equals(Object other) {
        return other instanceof Instance i && i.folder.equals(folder);
    }

    @Override
    public int hashCode() {
        return folder.hashCode();
    }
}
