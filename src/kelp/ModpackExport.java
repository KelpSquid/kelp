package kelp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Share: packs an instance into one .mrpack file (the Modrinth modpack format, which Kelp, Prism and others import)
 * for sending to a friend. It holds the instance's mods, settings, configs, resource packs and shader packs, and its
 * worlds if asked. Your own mod projects go in as .squid files, so your friend gets readable code, not your editor's
 * files.
 *
 * Everything is inside the file, so it's for sharing with friends: mods made by other people are copied in, so it
 * shouldn't be posted publicly unless their authors allow that.
 */
public final class ModpackExport {
    private ModpackExport() {
    }

    /** What goes in, from the instance's folder. Worlds (saves) only when asked. */
    static final List<String> KEEP = List.of("config", "resourcepacks", "shaderpacks", "options.txt");

    /** Makes the pack in outFolder and gives back the file, like "Survival.mrpack" (or "Survival 2.mrpack"). */
    public static Path export(Instance instance, Path outFolder, boolean withWorlds) throws IOException {
        Files.createDirectories(outFolder);
        String base = instance.name().replaceAll("[\\\\/:*?\"<>|]", "").strip();
        if (base.isEmpty()) base = "Instance";
        Path file = outFolder.resolve(base + ".mrpack");
        for (int n = 2; Files.exists(file); n++) file = outFolder.resolve(base + " " + n + ".mrpack");

        Path folder = instance.folder();
        Path temp = Files.createTempDirectory("kelp-share");
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "modrinth.index.json", index(instance).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Mods: files as they are; your own projects packed into .squid files
            Path mods = instance.mods();
            if (Files.isDirectory(mods)) {
                try (Stream<Path> list = Files.list(mods)) {
                    for (Path mod : list.sorted().toList()) {
                        String name = mod.getFileName().toString();
                        if (name.startsWith(".")) continue; // Squid's own build cache
                        if (Files.isDirectory(mod)) {
                            if (!ModProject.isProject(mod)) continue;
                            try {
                                Path packed = ModProject.pack(mod, temp);
                                String packedName = packed.getFileName().toString() + (name.endsWith(".disabled") ? ".disabled" : "");
                                put(zip, "overrides/mods/" + packedName, Files.readAllBytes(packed));
                            } catch (IOException broken) {
                                // a project that can't be packed yet (a mistake in it) stays out; the rest still go
                            }
                        } else {
                            put(zip, "overrides/mods/" + name, Files.readAllBytes(mod));
                        }
                    }
                }
            }
            List<String> keep = new ArrayList<>(KEEP);
            if (withWorlds) keep.add("saves");
            for (String part : keep) {
                Path path = folder.resolve(part);
                if (Files.isRegularFile(path)) {
                    put(zip, "overrides/" + part, Files.readAllBytes(path));
                } else if (Files.isDirectory(path)) {
                    try (Stream<Path> walk = Files.walk(path)) {
                        for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            if (name.equals("session.lock") || ModProject.junk(name)) continue; // a world's lock, and computer junk
                            put(zip, "overrides/" + folder.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
                        }
                    }
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(file); // nothing half-made is left behind
            throw e;
        } finally {
            try (Stream<Path> walk = Files.walk(temp)) {
                for (Path p : walk.sorted((a, b) -> b.compareTo(a)).toList()) Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // the temp folder goes on its own eventually
            }
        }
        return file;
    }

    /** The pack's description: its name, the Minecraft version, and the loader (Squid is "squid"). */
    static String index(Instance instance) {
        Map<String, Object> needs = new LinkedHashMap<>();
        String minecraft = instance.version().id();
        needs.put("minecraft", minecraft);
        String loaderVersion = loaderVersion(instance.loaderVersion(), minecraft);
        switch (instance.loader()) {
            case FABRIC, SHADERS -> needs.put("fabric-loader", loaderVersion);
            case QUILT -> needs.put("quilt-loader", loaderVersion);
            case NEOFORGE -> needs.put("neoforge", loaderVersion);
            case FORGE -> needs.put("forge", loaderVersion);
            case SQUID -> needs.put("squid", "*");
            default -> {
                // Vanilla: just Minecraft
            }
        }
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("formatVersion", 1.0);
        index.put("game", "minecraft");
        index.put("versionId", "1.0");
        index.put("name", instance.name());
        index.put("summary", "Shared from Kelp");
        index.put("files", List.of());
        index.put("dependencies", needs);
        return ModProject.toJson(index);
    }

    /** "fabric-loader-0.19.5-26.3" becomes "0.19.5". Without one, "*" (any). */
    static String loaderVersion(String installed, String minecraft) {
        if (installed == null || installed.isBlank()) return "*";
        String v = installed.replaceFirst("^(fabric-loader|quilt-loader|neoforge|forge)-", "");
        if (v.endsWith("-" + minecraft)) v = v.substring(0, v.length() - minecraft.length() - 1);
        return v.isEmpty() ? "*" : v;
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
