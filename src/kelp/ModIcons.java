package kelp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mod icons, for the Mods screen. A Squid mod's icon is the picture its squid.json names ("icon": "icon.png", in
 * resources), or icon.png if it doesn't say. Fabric and Quilt mods name theirs in their own files, so theirs show too.
 *
 * New projects get an icon of their own, made from their name: a little symmetrical pixel picture, like a space
 * invader, in two colors. Same name, same picture. Kids can paint over it.
 */
public final class ModIcons {
    private ModIcons() {
    }

    /** Pictures already read, by file and when it changed. Empty means it has none. */
    private static final Map<String, Optional<BufferedImage>> CACHE = new ConcurrentHashMap<>();

    /** The mod's icon, or null if it has none (or it can't be read). */
    public static BufferedImage of(InstalledMod mod) {
        Path file = mod.file();
        String key;
        try {
            key = file + "|" + Files.getLastModifiedTime(file).toMillis();
            if (Files.isDirectory(file)) {
                // A folder's own time doesn't change when a file inside does: look at the icon and squid.json
                Path json = file.resolve("squid.json");
                if (Files.exists(json)) key += "|" + Files.getLastModifiedTime(json).toMillis();
                Path resources = file.resolve("resources");
                if (Files.isDirectory(resources)) {
                    try (java.util.stream.Stream<Path> pictures = Files.list(resources)) {
                        for (Path p : pictures.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")).toList()) {
                            key += "|" + p.getFileName() + Files.getLastModifiedTime(p).toMillis();
                        }
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
        Optional<BufferedImage> known = CACHE.get(key);
        if (known != null) return known.orElse(null);
        // A changed picture replaces the old one, so the list of pictures doesn't keep growing
        String prefix = file + "|";
        CACHE.keySet().removeIf(k -> k.startsWith(prefix));
        Optional<BufferedImage> picture = Optional.ofNullable(read(mod));
        CACHE.put(key, picture);
        return picture.orElse(null);
    }

    private static BufferedImage read(InstalledMod mod) {
        Path file = mod.file();
        try {
            if (Files.isDirectory(file)) {
                String icon = iconName(squidJson(file.resolve("squid.json")));
                Path picture = file.resolve("resources").resolve(icon).normalize();
                if (!picture.startsWith(file) || !Files.isRegularFile(picture)) return null;
                return small(javax.imageio.ImageIO.read(picture.toFile()));
            }
            String name = file.getFileName().toString();
            if (!name.contains(".jar") && !name.contains(".squid")) return null;
            try (ZipFile zip = new ZipFile(file.toFile())) {
                String path = null;
                String root = name.contains(".squid") ? ModProject.packedRoot(zip) : "";
                ZipEntry squid = root == null ? null : name.contains(".squid") ? ModProject.entry(zip, root + "squid.json") : zip.getEntry("squid.json");
                ZipEntry fabric = zip.getEntry("fabric.mod.json");
                ZipEntry quilt = zip.getEntry("quilt.mod.json");
                if (squid != null) {
                    String icon = iconName(Json.object(Json.parse(ModProject.text(zip.getInputStream(squid).readAllBytes()))));
                    path = name.contains(".squid") ? root + "resources/" + icon : icon;
                } else if (fabric != null) {
                    Object icon = Json.object(Json.parse(ModProject.text(zip.getInputStream(fabric).readAllBytes()))).get("icon");
                    // Fabric's icon is a path, or sizes and paths: the biggest is fine, it's shown small
                    if (icon instanceof String one) path = one;
                    else if (icon instanceof Map<?, ?> sizes && !sizes.isEmpty()) path = String.valueOf(sizes.values().iterator().next());
                } else if (quilt != null) {
                    Map<String, Object> loader = Json.object(Json.object(Json.parse(ModProject.text(zip.getInputStream(quilt).readAllBytes()))).get("quilt_loader"));
                    Map<String, Object> metadata = loader == null ? null : Json.object(loader.get("metadata"));
                    if (metadata != null && metadata.get("icon") instanceof String one) path = one;
                }
                if (path == null) return null;
                String wanted = path.startsWith("/") ? path.substring(1) : path;
                ZipEntry entry = name.contains(".squid") ? ModProject.entry(zip, wanted) : zip.getEntry(wanted);
                if (entry == null || entry.getSize() > 4 << 20) return null;
                try (InputStream in = zip.getInputStream(entry)) {
                    return small(javax.imageio.ImageIO.read(in));
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Map<String, Object> squidJson(Path file) throws IOException {
        if (!Files.exists(file)) return Map.of();
        Object json = Json.parse(ModProject.text(Files.readAllBytes(file)));
        return json instanceof Map<?, ?> ? Json.object(json) : Map.of();
    }

    private static String iconName(Map<String, Object> json) {
        return json != null && json.get("icon") instanceof String icon && !icon.isBlank() ? icon : "icon.png";
    }

    /** Big pictures are shrunk once (to 64 pixels at most), so drawing them every frame stays quick. */
    private static BufferedImage small(BufferedImage picture) {
        if (picture == null) return null;
        int size = Math.max(picture.getWidth(), picture.getHeight());
        if (size <= 64) return picture;
        int w = Math.max(1, picture.getWidth() * 64 / size);
        int h = Math.max(1, picture.getHeight() * 64 / size);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(picture, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /**
     * An icon made from a mod's name: 16x16 pixels, a symmetrical 8x8 pattern (mirrored left to right) drawn two
     * pixels a square, in a color picked from the name, with a darker edge. Same name, same icon.
     */
    public static BufferedImage generate(String name) {
        long seed = name.hashCode() * 0x9E3779B97F4A7C15L;
        java.util.Random random = new java.util.Random(seed);
        float hue = random.nextFloat();
        int main = java.awt.Color.HSBtoRGB(hue, 0.65f, 0.95f) | 0xFF000000;
        int accent = java.awt.Color.HSBtoRGB((hue + 0.45f) % 1, 0.6f, 1f) | 0xFF000000;
        int edge = java.awt.Color.HSBtoRGB(hue, 0.8f, 0.35f) | 0xFF000000;
        boolean[][] on = new boolean[8][8];
        int[][] color = new int[8][8];
        for (int y = 1; y < 7; y++) {
            for (int x = 1; x < 4; x++) {
                boolean filled = random.nextFloat() < 0.6f;
                int c = random.nextFloat() < 0.2f ? accent : main;
                on[y][x] = on[y][7 - x] = filled;
                color[y][x] = color[y][7 - x] = c;
            }
        }
        // Never empty: the middle column always has something
        on[3][3] = on[3][4] = on[4][3] = on[4][4] = true;
        color[3][3] = color[3][4] = color[4][3] = color[4][4] = main;
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (!on[y][x]) continue;
                boolean border = y == 0 || x == 0 || y == 7 || x == 7 || !on[y - 1][x] || !on[y + 1][x] || !on[y][x - 1] || !on[y][x + 1];
                int c = color[y][x];
                for (int py = 0; py < 2; py++) {
                    for (int px = 0; px < 2; px++) icon.setRGB(x * 2 + px, y * 2 + py, c);
                }
                // A darker outline under each blob, so it reads on any background
                if (border) {
                    if (y + 1 < 8 && !on[y + 1][x]) for (int px = 0; px < 2; px++) icon.setRGB(x * 2 + px, y * 2 + 1, edge);
                    if (x + 1 < 8 && !on[y][x + 1]) for (int py = 0; py < 2; py++) icon.setRGB(x * 2 + 1, y * 2 + py, edge);
                }
            }
        }
        return icon;
    }
}
