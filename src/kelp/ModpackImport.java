package kelp;

import static kelp.Lang.t;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Brings a modpack in as a new instance: a Modrinth pack (.mrpack), a CurseForge pack (a .zip with manifest.json), or
 * a Prism Launcher / MultiMC instance exported as a .zip. Kelp makes the instance with the pack's Minecraft version and
 * loader, downloads its mods (checking each one's fingerprint when the pack has one), and copies in its settings and
 * other files.
 */
public final class ModpackImport {
    private ModpackImport() {
    }

    /** Where Modrinth packs may download from (Modrinth's own rules). Tests add their pretend server. */
    static final List<String> ALLOWED_HOSTS = new ArrayList<>(List.of("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com"));
    /** CurseForge's public download link for a mod's file. */
    static String curseForgeDownload = "https://www.curseforge.com/api/v1/mods/%d/files/%d/download";

    /** What a pack says: its name, Minecraft version and loader, what to download, and which folder in it to copy. */
    record Plan(String name, String minecraft, Loader loader, List<Downloader.Job> files, List<String> overrides) {
    }

    /** What happened: the new instance, and the mods that couldn't be downloaded (to get by hand), if any. */
    public record Result(Instance instance, List<String> missing) {
    }

    /** Reads a pack's details, without changing anything yet. files' paths are relative to the new instance. */
    static Plan read(Path pack) throws IOException {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            if (zip.getEntry("modrinth.index.json") != null) return modrinth(text(zip, "modrinth.index.json"));
            if (zip.getEntry("manifest.json") != null) return curseForge(text(zip, "manifest.json"));
            ZipEntry mmc = find(zip, "mmc-pack.json");
            if (mmc != null) return multiMc(zip, mmc);
        } catch (java.util.zip.ZipException e) {
            throw new IOException(t("That file isn't a modpack Kelp can read."));
        }
        throw new IOException(t("That file isn't a modpack Kelp can read."));
    }

    private static Plan modrinth(String json) throws IOException {
        Map<String, Object> index = Json.object(Json.parse(json));
        Map<String, Object> needs = Json.object(index.get("dependencies"));
        String minecraft = (String) needs.get("minecraft");
        Loader loader = needs.containsKey("fabric-loader") ? Loader.FABRIC : needs.containsKey("quilt-loader") ? Loader.QUILT
                : needs.containsKey("neoforge") ? Loader.NEOFORGE : needs.containsKey("forge") ? Loader.FORGE : Loader.VANILLA;
        List<Downloader.Job> files = new ArrayList<>();
        for (Object entry : Json.array(index.get("files"))) {
            Map<String, Object> file = Json.object(entry);
            Map<String, Object> env = Json.object(file.get("env"));
            if (env != null && "unsupported".equals(env.get("client"))) continue; // only for servers
            String path = (String) file.get("path");
            if (!safe(path)) throw new IOException(t("That modpack has a file that would go outside its instance, so it wasn't imported."));
            String url = null;
            for (Object link : Json.array(file.get("downloads"))) {
                if (allowed((String) link)) {
                    url = (String) link;
                    break;
                }
            }
            if (url == null) continue;
            Map<String, Object> hashes = Json.object(file.get("hashes"));
            long size = file.get("fileSize") instanceof Number n ? n.longValue() : -1;
            files.add(new Downloader.Job(url, Path.of(path), hashes == null ? null : (String) hashes.get("sha1"), size));
        }
        return new Plan((String) index.getOrDefault("name", "Modpack"), minecraft, loader, files, List.of("overrides", "client-overrides"));
    }

    private static Plan curseForge(String json) {
        Map<String, Object> manifest = Json.object(Json.parse(json));
        Map<String, Object> minecraft = Json.object(manifest.get("minecraft"));
        Loader loader = Loader.VANILLA;
        for (Object entry : Json.array(minecraft.get("modLoaders"))) {
            String id = String.valueOf(Json.object(entry).get("id"));
            if (id.startsWith("fabric")) loader = Loader.FABRIC;
            else if (id.startsWith("quilt")) loader = Loader.QUILT;
            else if (id.startsWith("neoforge")) loader = Loader.NEOFORGE;
            else if (id.startsWith("forge")) loader = Loader.FORGE;
        }
        List<Downloader.Job> files = new ArrayList<>();
        for (Object entry : Json.array(manifest.get("files"))) {
            Map<String, Object> file = Json.object(entry);
            long project = ((Number) file.get("projectID")).longValue();
            long id = ((Number) file.get("fileID")).longValue();
            files.add(new Downloader.Job(curseForgeDownload.formatted(project, id), Path.of("mods", "curseforge-" + project + "-" + id + ".jar"), null, -1));
        }
        String overrides = manifest.get("overrides") instanceof String o ? o : "overrides";
        return new Plan((String) manifest.getOrDefault("name", "Modpack"), (String) minecraft.get("version"), loader, files, List.of(overrides));
    }

    /** A Prism Launcher or MultiMC instance: its components say the versions, and its .minecraft folder has everything. */
    private static Plan multiMc(ZipFile zip, ZipEntry mmc) throws IOException {
        Map<String, Object> pack = Json.object(Json.parse(new String(zip.getInputStream(mmc).readAllBytes(), StandardCharsets.UTF_8)));
        String minecraft = null;
        Loader loader = Loader.VANILLA;
        for (Object entry : Json.array(pack.get("components"))) {
            Map<String, Object> component = Json.object(entry);
            String uid = String.valueOf(component.get("uid"));
            switch (uid) {
                case "net.minecraft" -> minecraft = (String) component.get("version");
                case "net.fabricmc.fabric-loader" -> loader = Loader.FABRIC;
                case "org.quiltmc.quilt-loader" -> loader = Loader.QUILT;
                case "net.neoforged" -> loader = Loader.NEOFORGE;
                case "net.minecraftforge" -> loader = Loader.FORGE;
                default -> {
                }
            }
        }
        String top = mmc.getName().contains("/") ? mmc.getName().substring(0, mmc.getName().lastIndexOf('/') + 1) : "";
        String name = top.isEmpty() ? "Imported" : top.substring(0, top.length() - 1);
        String gameFolder = zip.getEntry(top + ".minecraft/") != null || zip.stream().anyMatch(e -> e.getName().startsWith(top + ".minecraft/"))
                ? top + ".minecraft" : top + "minecraft";
        return new Plan(name, minecraft, loader, List.of(), List.of(gameFolder));
    }

    /**
     * Makes the new instance and fills it in. stage hears what's happening, for the progress screen. Mods that can't be
     * downloaded (some CurseForge authors don't allow it) are listed in the result rather than stopping the import.
     */
    public static Result importPack(Path pack, Downloader downloader, Consumer<String> stage) throws IOException, InterruptedException {
        stage.accept(t("Reading the modpack..."));
        Plan plan = read(pack);
        if (plan.minecraft() == null) throw new IOException(t("That modpack doesn't say which Minecraft version it's for."));
        VersionManifest.Version version = null;
        for (VersionManifest.Version v : VersionManifest.download()) {
            if (v.id().equals(plan.minecraft())) version = v;
        }
        if (version == null) throw new IOException(t("That modpack is for Minecraft {0}, which Kelp can't play.", plan.minecraft()));

        Instance instance = Instance.create(plan.name(), version, plan.loader());
        try {
            stage.accept(t("Copying the modpack's files..."));
            copyOverrides(pack, plan.overrides(), instance.folder());
            stage.accept(t("Downloading {0} mods...", plan.files().size()));
            List<String> missing = new ArrayList<>();
            List<Downloader.Job> jobs = new ArrayList<>();
            for (Downloader.Job job : plan.files()) {
                jobs.add(new Downloader.Job(job.url(), instance.folder().resolve(job.file()), job.sha1(), job.size()));
            }
            if (plan.files().stream().allMatch(j -> j.sha1() != null)) {
                downloader.downloadAll(jobs); // a Modrinth pack: every file has a fingerprint, so all of them must arrive
            } else {
                for (Downloader.Job job : jobs) { // CurseForge: some authors don't allow downloads outside their site
                    try {
                        downloader.downloadAll(List.of(job));
                    } catch (IOException e) {
                        if (downloader.isCancelled()) throw e;
                        missing.add(job.url());
                    }
                }
            }
            return new Result(instance, missing);
        } catch (IOException | InterruptedException e) {
            instance.delete(); // nothing half-imported is left behind
            throw e;
        }
    }

    /** Copies the pack's own files (settings, configs, resource packs...) into the instance. */
    private static void copyOverrides(Path pack, List<String> folders, Path instance) throws IOException {
        Path root = instance.toAbsolutePath().normalize();
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                for (String folder : folders) {
                    String prefix = folder.endsWith("/") ? folder : folder + "/";
                    if (!entry.getName().startsWith(prefix) || entry.isDirectory()) continue;
                    Path target = root.resolve(entry.getName().substring(prefix.length())).normalize();
                    if (!target.startsWith(root)) throw new IOException(t("That modpack has a file that would go outside its instance, so it wasn't imported."));
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static boolean safe(String path) {
        if (path == null || path.isBlank()) return false;
        Path p = Path.of(path).normalize();
        return !p.isAbsolute() && !p.startsWith("..");
    }

    private static boolean allowed(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && ALLOWED_HOSTS.contains(host.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static ZipEntry find(ZipFile zip, String name) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().equals(name) || entry.getName().endsWith("/" + name) && entry.getName().indexOf('/') == entry.getName().lastIndexOf('/')) {
                return entry;
            }
        }
        return null;
    }

    private static String text(ZipFile zip, String name) throws IOException {
        return new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
    }
}
