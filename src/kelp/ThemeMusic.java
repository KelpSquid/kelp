package kelp;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.nio.file.Path;

/** Plays a theme's music (a .wav file) over and over while Kelp is open, and stops it when the theme changes. */
public final class ThemeMusic {
    private ThemeMusic() {
    }

    private static Clip playing;
    private static Path playingFile;

    /** Plays this music on repeat, or stops the music for null. Nothing happens if it's already the one playing. */
    public static synchronized void play(Path music) {
        if (music == null ? playingFile == null : music.equals(playingFile)) return;
        stop();
        if (music == null) return;
        try (AudioInputStream in = AudioSystem.getAudioInputStream(music.toFile())) {
            Clip clip = AudioSystem.getClip();
            clip.open(in);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            playing = clip;
            playingFile = music;
        } catch (Exception e) {
            // no sound card, or not a .wav Java can play: Kelp just stays quiet
            System.err.println("Couldn't play " + music.getFileName() + ": " + e.getMessage());
            playingFile = music; // don't try again every frame
        }
    }

    public static synchronized void stop() {
        if (playing != null) playing.close();
        playing = null;
        playingFile = null;
    }
}
