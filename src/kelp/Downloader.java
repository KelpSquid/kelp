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
import java.time.Duration;
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
     * @param size its size in bytes, or -1 if it isn't known
     */
    public record Job(String url, Path file, String sha1, long size) {
    }

    private static final int THREADS = 8; // how many files download at the same time
    private static final int TRIES = 3;
    // Sites like Modrinth ask every app to say who it is, so they can reach the developer if something goes wrong
    static final String USER_AGENT = "KelpSquid/kelp/0.1 (kelp@kelplauncher.org)";

    // Give up connecting after 15 seconds, so a bad connection fails instead of waiting forever
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final AtomicInteger filesDone = new AtomicInteger();
    private final AtomicLong bytesDone = new AtomicLong();
    private volatile int filesTotal;
    private volatile long bytesTotal;
    private volatile ExecutorService pool;
    private volatile boolean cancelled; // once cancelled, every later download stops right away too

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

    /**
     * Downloads a small text file (like a JSON list), saves a copy, and returns the text.
     * Without internet it uses the copy saved last time, so games that are already downloaded still start.
     */
    public String fetchText(String url, Path saveTo) throws IOException, InterruptedException {
        stopIfCancelled();
        String text;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(30)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IOException("Got error " + response.statusCode() + " for " + url);
            text = response.body();
        } catch (IOException offline) {
            if (Files.exists(saveTo)) return Files.readString(saveTo); // no internet: last time's copy is fine
            throw new IOException("Couldn't download " + saveTo.getFileName() + ": " + explain(offline), offline);
        }
        // Write to a .part file first, so a half-written file never replaces a good copy
        Files.createDirectories(saveTo.getParent());
        Path part = saveTo.resolveSibling(saveTo.getFileName() + ".part");
        Files.writeString(part, text);
        Files.move(part, saveTo, StandardCopyOption.REPLACE_EXISTING);
        return text;
    }

    /** Downloads every job, 8 at a time. Files that are already there are skipped. */
    public void downloadAll(List<Job> jobs) throws IOException, InterruptedException {
        stopIfCancelled();
        filesTotal = jobs.size();
        bytesTotal = jobs.stream().mapToLong(job -> Math.max(0, job.size())).sum();
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
        stopIfCancelled(); // a cancel during the downloads can look like a finished download, so check again
    }

    /** Stops all downloads, including any that haven't started yet. */
    public void cancel() {
        cancelled = true;
        ExecutorService p = pool;
        if (p != null) p.shutdownNow();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    private void stopIfCancelled() throws IOException {
        if (cancelled) throw new IOException("Cancelled.");
    }

    private void download(Job job) throws IOException, InterruptedException {
        Path file = job.file();
        if (Files.exists(file) && (job.size() < 0 || Files.size(file) == job.size())) { // already downloaded earlier
            finished(job);
            return;
        }
        Files.createDirectories(file.getParent());
        // Download to a .part file first, so a half-finished download never looks like a real file
        Path part = file.resolveSibling(file.getFileName() + ".part");
        IOException problem = null;
        for (int attempt = 1; attempt <= TRIES; attempt++) {
            try {
                HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(job.url())).header("User-Agent", USER_AGENT).build(),
                        HttpResponse.BodyHandlers.ofFile(part));
                if (response.statusCode() != 200) throw new IOException("got error " + response.statusCode());
                if (job.sha1() != null && !job.sha1().equals(sha1(part))) throw new IOException("it arrived damaged");
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
                finished(job);
                return;
            } catch (IOException e) {
                problem = e;
                if (attempt < TRIES) Thread.sleep(attempt == 1 ? 1000 : 3000); // give a hiccup time to pass
            }
        }
        Files.deleteIfExists(part);
        throw new IOException("Couldn't download " + file.getFileName() + ": " + explain(problem), problem);
    }

    /** A connection problem in plain words. Java's own messages are often empty or technical. */
    static String explain(IOException e) {
        if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException
                || e instanceof java.net.http.HttpConnectTimeoutException) {
            return "no internet connection";
        }
        if (e instanceof java.net.http.HttpTimeoutException) return "the connection is too slow";
        return e.getMessage() != null ? e.getMessage() : "the connection failed";
    }

    private void finished(Job job) {
        filesDone.incrementAndGet();
        bytesDone.addAndGet(Math.max(0, job.size()));
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
