import javax.imageio.ImageIO;
import javax.tools.ToolProvider;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Builds Kelp's downloads, one for each kind of computer. Each has its own Java, so nobody needs to install anything:
 *
 *   build/Kelp-windows.zip    unzip, then double-click "Start Kelp"
 *   build/Kelp-linux.tar.gz   unpack, then run start-kelp.sh
 *   build/Kelp-mac.tar.gz     unpack, then open Kelp.app (works on Apple Silicon and Intel Macs)
 *
 * Squid comes along too, from the squid folder next to this one (run Squid's build.bat first).
 * Run it with package.bat.
 */
public class Package {
    static final String VERSION = "0.1";
    static final Path BUILD = Path.of("build");
    static final Path SQUID = Path.of("..", "squid");
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public static void main(String[] args) throws Exception {
        Path kelpJar = buildKelpJar();
        Map<String, Path> squidJars = squidJars();
        String readme = Files.readString(Path.of("tools", "DOWNLOAD-README.txt")).replace("\r\n", "\n");
        byte[] asmLicense = Files.readAllBytes(Path.of("licenses", "ASM-LICENSE.txt"));

        windows(kelpJar, squidJars, readme, asmLicense);
        linux(kelpJar, squidJars, readme, asmLicense);
        mac(kelpJar, squidJars, readme, asmLicense);
        System.out.println("Done. The packages are in the build folder.");
    }

    // ---- Kelp itself ----

    /** Compiles Kelp and packs it, with its textures and logo inside, into one kelp.jar. */
    static Path buildKelpJar() throws IOException {
        // A fresh temporary folder each time, outside OneDrive, which can hold files open while it syncs
        Path classes = Files.createTempDirectory("kelp-classes");
        List<String> javac = new ArrayList<>(List.of("--release", "21", "-encoding", "UTF-8", "-d", classes.toString()));
        try (Stream<Path> sources = Files.walk(Path.of("src"))) {
            sources.filter(p -> p.toString().endsWith(".java")).forEach(p -> javac.add(p.toString()));
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, javac.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("Kelp didn't compile");
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "kelp.Kelp");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, VERSION);
        Path jar = BUILD.resolve("kelp.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            addFolder(out, classes, "");
            addFolder(out, Path.of("textures"), "textures/");
            addFolder(out, Path.of("branding"), "branding/");
            addFolder(out, Path.of("lang"), "lang/");
        }
        System.out.println("Built " + jar);
        return jar;
    }

    static void addFolder(JarOutputStream out, Path folder, String prefix) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(prefix + folder.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }

    /**
     * squid.jar, ASM, and Squid's built-in parts like the Store, from Squid's own build.
     * The keys are where each goes inside the squid folder: built-in parts go in squid/builtin.
     */
    static Map<String, Path> squidJars() throws IOException {
        Path squid = SQUID.resolve("build").resolve("squid.jar");
        if (!Files.exists(squid)) throw new IOException("Build Squid first: run build.bat in the squid folder.");
        Map<String, Path> jars = new LinkedHashMap<>();
        jars.put("squid.jar", squid);
        try (Stream<Path> lib = Files.list(SQUID.resolve("lib"))) {
            for (Path jar : lib.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) jars.put(jar.getFileName().toString(), jar);
        }
        Path builtIn = SQUID.resolve("build").resolve("builtin");
        if (Files.isDirectory(builtIn)) {
            try (Stream<Path> parts = Files.list(builtIn)) {
                for (Path jar : parts.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                    jars.put("builtin/" + jar.getFileName(), jar);
                }
            }
        }
        return jars;
    }

    // ---- Windows: a zip with a Start Kelp file ----

    static void windows(Path kelpJar, Map<String, Path> squidJars, String readme, byte[] asmLicense) throws Exception {
        Path jre = downloadJava("windows", "x64");
        Path out = BUILD.resolve("Kelp-windows.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            putFile(zip, "Kelp/kelp.jar", Files.readAllBytes(kelpJar));
            for (Map.Entry<String, Path> jar : squidJars.entrySet()) putFile(zip, "Kelp/squid/" + jar.getKey(), Files.readAllBytes(jar.getValue()));
            putFile(zip, "Kelp/squid/ASM-LICENSE.txt", asmLicense);
            putFile(zip, "Kelp/README.txt", windowsText(readme));
            // javaw runs Kelp without a black console window staying open
            putFile(zip, "Kelp/Start Kelp.bat", windowsText("@echo off\nstart \"\" \"%~dp0jre\\bin\\javaw.exe\" -jar \"%~dp0kelp.jar\"\n"));
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jre))) {
                for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                    if (entry.isDirectory()) continue;
                    putFile(zip, "Kelp/jre/" + withoutTopFolder(entry.getName()), in.readAllBytes());
                }
            }
        }
        System.out.println("Built " + out);
    }

    static void putFile(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    static byte[] windowsText(String text) {
        return text.replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    // ---- Linux: a tar.gz, so the start script and Java keep their "runnable" mark ----

    static void linux(Path kelpJar, Map<String, Path> squidJars, String readme, byte[] asmLicense) throws Exception {
        Path jre = downloadJava("linux", "x64");
        Path out = BUILD.resolve("Kelp-linux.tar.gz");
        try (Tar tar = new Tar(Files.newOutputStream(out))) {
            tar.file("Kelp/kelp.jar", 0644, Files.readAllBytes(kelpJar));
            for (Map.Entry<String, Path> jar : squidJars.entrySet()) tar.file("Kelp/squid/" + jar.getKey(), 0644, Files.readAllBytes(jar.getValue()));
            tar.file("Kelp/squid/ASM-LICENSE.txt", 0644, asmLicense);
            tar.file("Kelp/README.txt", 0644, readme.getBytes(StandardCharsets.UTF_8));
            tar.file("Kelp/start-kelp.sh", 0755, ("#!/bin/sh\n"
                    + "# Starts Kelp with the Java that comes with it\n"
                    + "cd \"$(dirname \"$0\")\"\n"
                    + "exec ./jre/bin/java -jar kelp.jar\n").getBytes(StandardCharsets.UTF_8));
            copyTar(jre, tar, name -> "Kelp/jre/" + withoutTopFolder(name));
        }
        System.out.println("Built " + out);
    }

    // ---- Mac: Kelp.app with both Javas, picking the one that matches the Mac's chip ----

    static void mac(Path kelpJar, Map<String, Path> squidJars, String readme, byte[] asmLicense) throws Exception {
        Path arm = downloadJava("mac", "aarch64");
        Path intel = downloadJava("mac", "x64");
        String app = "Kelp/Kelp.app/Contents/";
        Path out = BUILD.resolve("Kelp-mac.tar.gz");
        try (Tar tar = new Tar(Files.newOutputStream(out))) {
            tar.file("Kelp/README.txt", 0644, readme.getBytes(StandardCharsets.UTF_8));
            tar.file(app + "Info.plist", 0644, infoPlist().getBytes(StandardCharsets.UTF_8));
            tar.file(app + "MacOS/Kelp", 0755, ("#!/bin/sh\n"
                    + "# Starts Kelp with the Java that matches this Mac's chip\n"
                    + "here=\"$(cd \"$(dirname \"$0\")/../Resources\" && pwd)\"\n"
                    + "if [ \"$(uname -m)\" = \"arm64\" ]; then java=\"$here/jre-arm64/bin/java\"; else java=\"$here/jre-x64/bin/java\"; fi\n"
                    + "exec \"$java\" -Xdock:name=Kelp -Xdock:icon=\"$here/kelp.icns\" -jar \"$here/kelp.jar\"\n")
                    .getBytes(StandardCharsets.UTF_8));
            tar.file(app + "Resources/kelp.jar", 0644, Files.readAllBytes(kelpJar));
            tar.file(app + "Resources/kelp.icns", 0644, icns(ImageIO.read(Path.of("branding", "kelp.png").toFile())));
            for (Map.Entry<String, Path> jar : squidJars.entrySet()) tar.file(app + "Resources/squid/" + jar.getKey(), 0644, Files.readAllBytes(jar.getValue()));
            tar.file(app + "Resources/squid/ASM-LICENSE.txt", 0644, asmLicense);
            // A Mac Java download is itself a bundle; only its Contents/Home folder is the Java
            copyTar(arm, tar, name -> insideHome(name, app + "Resources/jre-arm64/"));
            copyTar(intel, tar, name -> insideHome(name, app + "Resources/jre-x64/"));
        }
        System.out.println("Built " + out);
    }

    static String insideHome(String name, String target) {
        String rest = withoutTopFolder(name);
        return rest.startsWith("Contents/Home/") ? target + rest.substring("Contents/Home/".length()) : null;
    }

    static String infoPlist() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleName</key><string>Kelp</string>
                    <key>CFBundleDisplayName</key><string>Kelp</string>
                    <key>CFBundleIdentifier</key><string>org.kelplauncher.kelp</string>
                    <key>CFBundleExecutable</key><string>Kelp</string>
                    <key>CFBundleIconFile</key><string>kelp</string>
                    <key>CFBundlePackageType</key><string>APPL</string>
                    <key>CFBundleShortVersionString</key><string>%s</string>
                    <key>CFBundleVersion</key><string>%s</string>
                    <key>LSMinimumSystemVersion</key><string>11.0</string>
                    <key>NSHighResolutionCapable</key><true/>
                </dict>
                </plist>
                """.formatted(VERSION, VERSION);
    }

    /** A Mac icon file: PNGs of the logo at several sizes, each tagged with Apple's code for that size. */
    static byte[] icns(BufferedImage logo) throws IOException {
        ByteArrayOutputStream entries = new ByteArrayOutputStream();
        String[][] sizes = {{"ic07", "128"}, {"ic08", "256"}, {"ic09", "512"}, {"ic10", "1024"}};
        for (String[] size : sizes) {
            int px = Integer.parseInt(size[1]);
            BufferedImage scaled = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
            // The logo is pixel art, so it's scaled without smoothing to stay crisp
            scaled.getGraphics().drawImage(logo.getScaledInstance(px, px, Image.SCALE_REPLICATE), 0, 0, null);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", png);
            entries.write(size[0].getBytes(StandardCharsets.US_ASCII));
            writeInt(entries, png.size() + 8);
            png.writeTo(entries);
        }
        ByteArrayOutputStream icns = new ByteArrayOutputStream();
        icns.write("icns".getBytes(StandardCharsets.US_ASCII));
        writeInt(icns, entries.size() + 8);
        entries.writeTo(icns);
        return icns.toByteArray();
    }

    static void writeInt(OutputStream out, int value) throws IOException {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    // ---- Downloading Java ----

    /**
     * The official Eclipse Temurin Java 21 for one kind of computer, from Adoptium.
     * Downloaded once into build/java, and checked against Adoptium's fingerprint.
     */
    static Path downloadJava(String os, String arch) throws Exception {
        String api = "https://api.adoptium.net/v3/assets/latest/21/hotspot?image_type=jre&vendor=eclipse&os=" + os + "&architecture=" + arch;
        String json = HTTP.send(HttpRequest.newBuilder(URI.create(api)).build(), HttpResponse.BodyHandlers.ofString()).body();
        // The answer lists an installer and a plain package, each with its own link and fingerprint; we want the package
        int start = json.indexOf("\"package\"");
        if (start < 0) throw new IOException("Adoptium's answer didn't have a package for " + os + " " + arch);
        String pkg = json.substring(start, json.indexOf('}', start));
        String link = find(pkg, "\"link\"\\s*:\\s*\"([^\"]+\\.(?:zip|tar\\.gz))\"");
        String checksum = find(pkg, "\"checksum\"\\s*:\\s*\"([0-9a-f]{64})\"");
        Path file = BUILD.resolve("java").resolve(link.substring(link.lastIndexOf('/') + 1));
        if (!Files.exists(file) || !sha256(file).equals(checksum)) {
            System.out.println("Downloading Java for " + os + " " + arch);
            Files.createDirectories(file.getParent());
            HTTP.send(HttpRequest.newBuilder(URI.create(link)).build(), HttpResponse.BodyHandlers.ofFile(file));
            if (!sha256(file).equals(checksum)) {
                Files.delete(file);
                throw new IOException("The Java download for " + os + " arrived damaged");
            }
        }
        return file;
    }

    static String find(String text, String regex) throws IOException {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) throw new IOException("Adoptium's answer didn't have what was expected");
        return m.group(1);
    }

    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String withoutTopFolder(String name) {
        int slash = name.indexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }

    // ---- Tar files ----

    interface Rename {
        String apply(String name);
    }

    /** Copies every file, folder and link from a downloaded .tar.gz into ours, renamed. Null names are skipped. */
    static void copyTar(Path source, Tar tar, Rename rename) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(source), 1 << 16)) {
            String longName = null;
            String paxPath = null;
            String paxLink = null;
            byte[] header = new byte[512];
            while (in.readNBytes(header, 0, 512) == 512) {
                if (header[0] == 0) break; // two empty blocks mark the end
                String name = longName != null ? longName : paxPath != null ? paxPath : headerName(header);
                int mode = (int) octal(header, 100, 8);
                long size = octal(header, 124, 12);
                char type = (char) header[156];
                String link = paxLink != null ? paxLink : cString(header, 157, 100);
                byte[] data = in.readNBytes((int) size);
                in.skipNBytes((512 - size % 512) % 512);
                longName = null;
                if (type == 'L') { // GNU long name for the next entry
                    longName = cString(data, 0, data.length);
                    continue;
                }
                if (type == 'x') { // PAX extra details for the next entry
                    String pax = new String(data, StandardCharsets.UTF_8);
                    paxPath = paxValue(pax, "path");
                    paxLink = paxValue(pax, "linkpath");
                    continue;
                }
                paxPath = null;
                paxLink = null;
                if (type == 'g') continue; // details for the whole file, not needed
                String renamed = rename.apply(name);
                if (renamed == null || renamed.isEmpty() || renamed.endsWith("/") && type != '5') continue;
                if (type == '5') tar.folder(renamed, mode);
                else if (type == '2') tar.link(renamed, link);
                else if (type == '0' || type == 0) tar.file(renamed, mode, data);
            }
        }
    }

    static String headerName(byte[] h) {
        String name = cString(h, 0, 100);
        String prefix = cString(h, 345, 155); // ustar keeps long paths split in two
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    static String paxValue(String pax, String key) {
        for (String line : pax.split("\n")) {
            int space = line.indexOf(' ');
            if (space > 0 && line.startsWith(key + "=", space + 1)) return line.substring(space + 2 + key.length());
        }
        return null;
    }

    static String cString(byte[] b, int offset, int length) {
        int end = offset;
        while (end < offset + length && end < b.length && b[end] != 0) end++;
        return new String(b, offset, end - offset, StandardCharsets.UTF_8);
    }

    static long octal(byte[] b, int offset, int length) {
        String s = cString(b, offset, length).trim();
        return s.isEmpty() ? 0 : Long.parseLong(s, 8);
    }

    /** Writes a .tar.gz: the archive format Linux and Mac use, which remembers which files are runnable. */
    static final class Tar implements AutoCloseable {
        private final OutputStream out;

        Tar(OutputStream raw) throws IOException {
            this.out = new GZIPOutputStream(raw, 1 << 16);
        }

        void file(String name, int mode, byte[] data) throws IOException {
            entry(name, mode, '0', data.length, "");
            out.write(data);
            out.write(new byte[(512 - data.length % 512) % 512]);
        }

        void folder(String name, int mode) throws IOException {
            entry(name.endsWith("/") ? name : name + "/", mode == 0 ? 0755 : mode, '5', 0, "");
        }

        void link(String name, String target) throws IOException {
            entry(name, 0777, '2', 0, target);
        }

        private void entry(String name, int mode, char type, long size, String link) throws IOException {
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            if (nameBytes.length > 100) { // too long for the header: send it first as a GNU long name
                byte[] longName = (name + "\0").getBytes(StandardCharsets.UTF_8);
                out.write(header("././@LongLink", 0644, 'L', longName.length, ""));
                out.write(longName);
                out.write(new byte[(512 - longName.length % 512) % 512]);
            }
            out.write(header(name, mode, type, size, link));
        }

        private static byte[] header(String name, int mode, char type, long size, String link) {
            byte[] h = new byte[512];
            put(h, 0, 100, name);
            put(h, 100, 8, String.format("%07o", mode & 07777));
            put(h, 108, 8, "0000000");
            put(h, 116, 8, "0000000");
            put(h, 124, 12, String.format("%011o", size));
            put(h, 136, 12, String.format("%011o", System.currentTimeMillis() / 1000));
            for (int i = 148; i < 156; i++) h[i] = ' ';
            h[156] = (byte) type;
            put(h, 157, 100, link);
            put(h, 257, 6, "ustar");
            put(h, 263, 2, "00");
            int sum = 0;
            for (byte b : h) sum += b & 0xff;
            put(h, 148, 8, String.format("%06o", sum) + "\0 ");
            return h;
        }

        private static void put(byte[] h, int offset, int length, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(bytes, 0, h, offset, Math.min(length, bytes.length));
        }

        @Override
        public void close() throws IOException {
            out.write(new byte[1024]); // the end of the archive
            out.close();
        }
    }
}
