package kelp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unpacks files out of a zip with a ceiling on how big they can get all together. A "zip bomb" is a tiny zip
 * that unpacks to terabytes; without a ceiling, opening one would fill the whole disk.
 */
final class Unzip {
    static final long MB = 1024L * 1024;
    static final long GB = 1024 * MB;

    private final long max;
    private final String tooBig;
    private long total;

    /** tooBig is the message when the files would go past max bytes all together. */
    Unzip(long max, String tooBig) {
        this.max = max;
        this.tooBig = tooBig;
    }

    /** Copies one file out of the zip into target (replacing it). If the ceiling is hit, the half-written file is deleted. */
    void copy(InputStream in, Path target) throws IOException {
        boolean done = false;
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) {
                total += n;
                if (total > max) throw new IOException(tooBig);
                out.write(buffer, 0, n);
            }
            done = true;
        } finally {
            if (!done) Files.deleteIfExists(target);
        }
    }

    /** Reads a small file (like a pack's list of mods) whole, refusing one bigger than max bytes. */
    static byte[] read(InputStream in, long max, String tooBig) throws IOException {
        byte[] bytes = in.readNBytes((int) Math.min(max + 1, Integer.MAX_VALUE - 8));
        if (bytes.length > max) throw new IOException(tooBig);
        return bytes;
    }
}
