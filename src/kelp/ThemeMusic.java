package kelp;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Plays a theme's music over and over while Kelp is open, and stops it when the theme changes. A .wav plays as it is;
 * MP3, FLAC, Ogg and .sqda files are decoded with Squid's own decoders (Kelp brings Squid along). A .sqda loops at its
 * own loop points, so a song can have an intro that plays once and a middle that goes round forever.
 */
public final class ThemeMusic {
    private ThemeMusic() {
    }

    /** The kinds of music a theme can have. */
    public static final List<String> KINDS = List.of("wav", "mp3", "m4a", "aac", "flac", "ogg", "sqda");

    private static Clip playing;
    private static Path playingFile;
    /** Counts up on every change, so music that finishes decoding after the theme changed isn't played. */
    private static int generation;

    public static boolean isMusic(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return KINDS.stream().anyMatch(kind -> name.endsWith("." + kind));
    }

    /** Plays this music on repeat, or stops the music for null. Nothing happens if it's already the one playing. */
    public static synchronized void play(Path music) {
        if (music == null ? playingFile == null : music.equals(playingFile)) return;
        stop();
        if (music == null) return;
        playingFile = music; // don't start it again every frame while it gets ready
        int mine = ++generation;
        // Decoding a song takes a moment, so it happens away from the screen
        Thread.ofVirtual().start(() -> {
            try {
                Clip clip = load(music);
                synchronized (ThemeMusic.class) {
                    if (generation != mine) {
                        clip.close(); // the theme changed while it was getting ready
                        return;
                    }
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                    playing = clip;
                }
            } catch (Throwable e) {
                // no sound card, or a file that can't be read: Kelp just stays quiet
                System.err.println("Couldn't play " + music.getFileName() + ": " + e.getMessage());
            }
        });
    }

    public static synchronized void stop() {
        generation++;
        if (playing != null) playing.close();
        playing = null;
        playingFile = null;
    }

    private static Clip load(Path music) throws Exception {
        String name = music.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".wav")) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(music.toFile())) {
                Clip clip = AudioSystem.getClip();
                clip.open(in);
                return clip;
            } catch (Exception javaCant) {
                // a .wav Java can't play itself (24-bit, floats...): Squid's decoder can
            }
        }
        return decodeWithSquid(music);
    }

    /** Decodes with Squid, and for a .sqda, loops at its loop points. */
    private static Clip decodeWithSquid(Path music) throws Exception {
        List<URL> jars = new ArrayList<>();
        try (Stream<Path> files = Files.list(Folders.squidInUse())) {
            for (Path p : files.filter(f -> f.toString().endsWith(".jar")).toList()) jars.add(p.toUri().toURL());
        }
        try (URLClassLoader squid = new URLClassLoader(jars.toArray(URL[]::new), ThemeMusic.class.getClassLoader())) {
            byte[] data = Files.readAllBytes(music);
            Class<?> audio = squid.loadClass("squid.audio.Audio");
            Object pcm = audio.getMethod("decode", byte[].class).invoke(null, (Object) data);
            short[] samples = (short[]) pcm.getClass().getMethod("samples").invoke(pcm);
            int channels = (int) pcm.getClass().getMethod("channels").invoke(pcm);
            int rate = (int) pcm.getClass().getMethod("rate").invoke(pcm);
            long[] loop = null;
            Class<?> sqda = squid.loadClass("squid.audio.Sqda");
            if ((boolean) sqda.getMethod("is", byte[].class).invoke(null, (Object) data)) {
                Object file = sqda.getMethod("read", byte[].class).invoke(null, (Object) data);
                Field loopsField = sqda.getField("loops");
                for (Object l : (List<?>) loopsField.get(file)) {
                    if ((int) l.getClass().getMethod("variant").invoke(l) != 0) continue;
                    loop = new long[] {(long) l.getClass().getMethod("start").invoke(l), (long) l.getClass().getMethod("end").invoke(l)};
                }
            }
            ByteBuffer bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short s : samples) bytes.putShort(s);
            AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
            Clip clip = AudioSystem.getClip();
            try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(bytes.array()), format, samples.length / channels)) {
                clip.open(in);
            }
            int frames = samples.length / channels;
            if (loop != null && loop[1] - loop[0] >= 512 && loop[1] <= frames) {
                clip.setLoopPoints((int) loop[0], (int) loop[1] - 1);
            }
            return clip;
        }
    }
}
