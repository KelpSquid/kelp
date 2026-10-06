package kelp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Downloads lots of files at once and keeps count, so a screen can show the progress. */
public class Downloader {
    /**
     * One file to download.
     *
     * @param sha1 the file's fingerprint, to check it arrived undamaged (or null to skip the check)
     * @param size its size in bytes
     */
    public record Job(String url, Path file, String sha1, long size) {
    }

    private static final int THREADS = 8; // how many files download at the same time
    private static final int TRIES = 3;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final AtomicInteger filesDone = new AtomicInteger();
    private final AtomicLong bytesDone = new AtomicLong();
    private volatile int filesTotal;
    private volatile long bytesTotal;
    private volatile ExecutorService pool;

    public int getFilesDone() {
        return filesDone.get();
    }

    public int getFilesTotal() {
        return filesTotal;
    }

    public long getBytesDone() {
        return bytesDone.get();
    }

    public long getBytesTotal() {
        return bytesTotal;
    }

    /** Downloads a small text file (like a JSON list), saves a copy, and returns the text. */
    public String fetchText(String url, Path saveTo) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Got error " + response.statusCode() + " for " + url);
        Files.createDirectories(saveTo.getParent());
        Files.writeString(saveTo, response.body());
        return response.body();
    }

    /** Downloads every job, 8 at a time. Files that are already there are skipped. */
    public void downloadAll(List<Job> jobs) throws IOException, InterruptedException {
        filesTotal = jobs.size();
        bytesTotal = jobs.stream().mapToLong(Job::size).sum();
        pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> running = new ArrayList<>();
            for (Job job : jobs) {
                running.add(pool.submit(() -> {
                    download(job);
                    return null;
                }));
            }
            for (Future<?> f : running) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException io) throw io;
                    throw new IOException(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Stops all downloads. */
    public void cancel() {
        ExecutorService p = pool;
        if (p != null) p.shutdownNow();
    }

    private void download(Job job) throws IOException, InterruptedException {
        Path file = job.file();
        if (Files.exists(file) && Files.size(file) == job.size()) { // already downloaded earlier
            finished(job);
            return;
        }
        Files.createDirectories(file.getParent());
        // Download to a .part file first, so a half-finished download never looks like a real file
        Path part = file.resolveSibling(file.getFileName() + ".part");
        IOException problem = null;
        for (int attempt = 1; attempt <= TRIES; attempt++) {
            try {
                HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(job.url())).build(),
                        HttpResponse.BodyHandlers.ofFile(part));
                if (response.statusCode() != 200) throw new IOException("got error " + response.statusCode());
                if (job.sha1() != null && !job.sha1().equals(sha1(part))) throw new IOException("it arrived damaged");
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
                finished(job);
                return;
            } catch (IOException e) {
                problem = e;
            }
        }
        Files.deleteIfExists(part);
        throw new IOException("Couldn't download " + file.getFileName() + ": " + problem.getMessage(), problem);
    }

    private void finished(Job job) {
        filesDone.incrementAndGet();
        bytesDone.addAndGet(job.size());
    }

    private static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java has SHA-1, so this never happens
        }
    }
}
