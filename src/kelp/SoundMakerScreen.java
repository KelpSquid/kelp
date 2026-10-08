package kelp;

import static kelp.Lang.t;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Sound Maker: turns a WAV, FLAC, MP3 or Ogg file into a .sqda, Squid's own sound file, for resource packs. Pick (or
 * drop) a sound, give it a title, pick a quality, and if it should loop, where. A tempo adds beat cues that mods and
 * lights can follow. The .sqda is saved next to the original.
 *
 * Find Loop listens to the sound (Squid's Analysis does it) and fills in the tempo and a loop that sounds
 * seamless, on whole bars, so nobody has to hunt for loop points by ear.
 *
 * The squeezing is Squid's (Kelp brings Squid along), so it's loaded from squid.jar here. Variants, light cues and
 * entity triggers are made with a recipe file: see SqdaTool in Squid.
 */
public class SoundMakerScreen extends Screen {
    private final Screen parent;
    private final McButton pickButton = new McButton("", this::pickSound);
    private final McTextField titleField = new McTextField(48);
    private final McTextField loopStartField = new McTextField(8);
    private final McTextField loopEndField = new McTextField(8);
    private final McTextField bpmField = new McTextField(6);
    private final McButton loopButton = new McButton("", this::toggleLoop);
    private final McButton makeButton = new McButton(t("Make .sqda"), this::make);
    private final McButton findButton = new McButton(t("Find Loop"), this::find);
    private final McButton folderButton = new McButton(t("Show File"), this::showFile);
    private final McButton doneButton = new McButton(t("Done"), this::done);
    private final List<McTextField> fields = List.of(titleField, loopStartField, loopEndField, bpmField);
    private final McSlider qualitySlider;
    private Path sound;
    private Path made;
    private boolean looping;
    private int quality = 6;
    private volatile boolean working;
    private volatile String message;
    private volatile boolean messageIsProblem;
    private volatile boolean finding;
    /** When the first beat is, in seconds, from Find Loop (beat cues start there). */
    private volatile double beatOffset;

    public SoundMakerScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        qualitySlider = new McSlider(0, 10, 1, quality, v -> t("Quality: {0}", (int) v), v -> quality = (int) v);
        loopStartField.setText("0");
        for (McButton b : new McButton[] {pickButton, loopButton, findButton, makeButton, folderButton, doneButton}) buttons.add(b);
    }

    private void done() {
        panel.setScreen(parent);
    }

    private void toggleLoop() {
        looping = !looping;
    }

    private void pickSound() {
        JFileChooser chooser = new JFileChooser(new java.io.File(System.getProperty("user.home")));
        chooser.setDialogTitle(t("Pick a sound"));
        chooser.setFileFilter(new FileNameExtensionFilter(t("Sounds (.wav, .flac, .mp3, .ogg)"), "wav", "flac", "mp3", "ogg"));
        if (chooser.showDialog(SwingUtilities.getWindowAncestor(panel), t("Use")) != JFileChooser.APPROVE_OPTION) return;
        use(chooser.getSelectedFile().toPath());
    }

    private void use(Path file) {
        sound = file;
        made = null;
        message = null;
        beatOffset = 0;
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (titleField.getText().isBlank()) titleField.setText(dot > 0 ? name.substring(0, dot) : name);
    }

    @Override
    public void filesDropped(List<Path> files) {
        if (!files.isEmpty()) use(files.get(0));
    }

    private static Double number(String text) {
        try {
            return text.isBlank() ? null : Double.parseDouble(text.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void make() {
        if (sound == null || working) return;
        Double loopStart = number(loopStartField.getText());
        Double loopEnd = number(loopEndField.getText());
        Double bpm = number(bpmField.getText());
        if (looping && (loopStart == null || loopEnd == null || loopEnd <= loopStart)) {
            problem(t("The loop needs a start and an end (in seconds), with the end after the start."));
            return;
        }
        working = true;
        message = t("Making it...");
        messageIsProblem = false;
        Path from = sound;
        String title = titleField.getText().strip();
        int q = quality;
        boolean loop = looping;
        double offset = beatOffset;
        Thread.ofVirtual().start(() -> {
            try {
                Path out = outFor(from, made);
                String summary = convert(from, out, q, loop ? loopStart : null, loop ? loopEnd : null, title, bpm, offset);
                made = out;
                message = t("Made {0} ({1} KB).", out.getFileName(), Files.size(out) / 1024) + " " + summary;
                messageIsProblem = false;
            } catch (Throwable e) {
                // Even running out of memory on a huge file says so, instead of "Making it..." forever
                Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
                problem(t("Couldn't make it: {0}", cause.getMessage() == null ? cause.toString() : cause.getMessage()));
            } finally {
                working = false;
            }
        });
    }

    /** Listens to the sound for its tempo and a seamless loop, and fills them in. */
    private void find() {
        if (sound == null || working || finding) return;
        finding = true;
        message = t("Listening...");
        messageIsProblem = false;
        Path from = sound;
        Thread.ofVirtual().start(() -> {
            try {
                double[] found = analyze(from);
                double bpm = found[0];
                if (bpm > 0) {
                    bpmField.setText(Math.rint(bpm) == bpm ? String.valueOf((long) bpm) : String.format(java.util.Locale.ROOT, "%.2f", bpm));
                    beatOffset = found[1];
                }
                if (found[2] >= 0) {
                    looping = true;
                    loopStartField.setText(String.format(java.util.Locale.ROOT, "%.2f", found[2]));
                    loopEndField.setText(String.format(java.util.Locale.ROOT, "%.2f", found[3]));
                }
                if (bpm > 0 && found[2] >= 0) {
                    message = t("{0} BPM, and a loop from {1} s to {2} s.", bpmField.getText(), loopStartField.getText(), loopEndField.getText());
                } else if (bpm > 0) {
                    message = t("{0} BPM. It's too short for a loop.", bpmField.getText());
                } else if (found[2] >= 0) {
                    message = t("No steady beat, but a loop from {0} s to {1} s.", loopStartField.getText(), loopEndField.getText());
                } else {
                    problem(t("Couldn't find a beat or a loop in it."));
                    return;
                }
                messageIsProblem = false;
            } catch (Throwable e) {
                Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
                problem(t("Couldn't listen to it: {0}", cause.getMessage() == null ? cause.toString() : cause.getMessage()));
            } finally {
                finding = false;
            }
        });
    }

    /** Squid's Analysis on a sound: {bpm, first beat (s), loop start (s), loop end (s)}, with -1 for what it didn't find. */
    static double[] analyze(Path from) throws Exception {
        try (URLClassLoader squid = squidLoader()) {
            Class<?> audio = squid.loadClass("squid.audio.Audio");
            Class<?> pcmClass = squid.loadClass("squid.audio.Pcm");
            Class<?> analysis = squid.loadClass("squid.audio.Analysis");
            Class<?> tempoClass = squid.loadClass("squid.audio.Analysis$Tempo");
            Object pcm = audio.getMethod("decode", byte[].class).invoke(null, (Object) Files.readAllBytes(from));
            Object tempo = analysis.getMethod("tempo", pcmClass).invoke(null, pcm);
            Object loop = analysis.getMethod("loop", pcmClass, tempoClass, double.class).invoke(null, pcm, tempo, 10.0);
            double[] found = {-1, 0, -1, -1};
            if (tempo != null) {
                found[0] = (double) tempoClass.getMethod("bpm").invoke(tempo);
                found[1] = (double) tempoClass.getMethod("offset").invoke(tempo);
            }
            if (loop != null) {
                found[2] = (double) loop.getClass().getMethod("start").invoke(loop);
                found[3] = (double) loop.getClass().getMethod("end").invoke(loop);
            }
            return found;
        }
    }

    /** A class loader with the Squid that Kelp brought along. */
    private static URLClassLoader squidLoader() throws java.io.IOException {
        List<URL> jars = new ArrayList<>();
        try (Stream<Path> files = Files.list(Folders.squidInUse())) {
            for (Path p : files.filter(f -> f.toString().endsWith(".jar")).toList()) jars.add(p.toUri().toURL());
        }
        if (jars.isEmpty()) throw new java.io.IOException(t("Squid isn't installed, and the Sound Maker needs it."));
        return new URLClassLoader(jars.toArray(URL[]::new), SoundMakerScreen.class.getClassLoader());
    }

    private void problem(String text) {
        message = text;
        messageIsProblem = true;
    }

    /**
     * Where the .sqda goes: next to the sound, with the same name. It never saves over the sound itself (making a
     * .sqda from a .sqda), or over another .sqda that's already there: those get a number, like "Song 2.sqda".
     * Making the same one again (lastMade) replaces it, since that's the point of making it again.
     */
    static Path outFor(Path from, Path lastMade) {
        String name = from.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        Path out = from.resolveSibling(base + ".sqda");
        for (int n = 2; (out.equals(from) || Files.exists(out)) && !out.equals(lastMade); n++) {
            out = from.resolveSibling(base + " " + n + ".sqda");
        }
        return out;
    }

    /** Squid does the work: its decoders read the sound, Squid Music squeezes it, and SqdaTool puts the .sqda together. */
    static String convert(Path from, Path out, int quality, Double loopStart, Double loopEnd, String title, Double bpm,
                          double beatOffset) throws Exception {
        try (URLClassLoader squid = squidLoader()) {
            Class<?> audio = squid.loadClass("squid.audio.Audio");
            Class<?> pcmClass = squid.loadClass("squid.audio.Pcm");
            Class<?> tool = squid.loadClass("squid.audio.SqdaTool");
            Class<?> sqdaClass = squid.loadClass("squid.audio.Sqda");
            Object pcm = audio.getMethod("decode", byte[].class).invoke(null, (Object) Files.readAllBytes(from));
            Map<String, String> info = new LinkedHashMap<>();
            if (!title.isBlank()) info.put("title", title);
            // A loop that runs past the end of the sound ends where the sound does
            double seconds = (double) pcmClass.getMethod("seconds").invoke(pcm);
            if (loopStart != null && loopStart >= seconds) {
                throw new java.io.IOException(t("The loop starts after the sound ends ({0} s long).", String.format(java.util.Locale.ROOT, "%.1f", seconds)));
            }
            if (loopEnd != null && loopEnd > seconds) loopEnd = seconds;
            Method simple = tool.getMethod("simple", pcmClass, int.class, Double.class, Double.class, Map.class, Double.class, double.class, int.class);
            Object sqda = simple.invoke(null, pcm, quality, loopStart, loopEnd, info, bpm, beatOffset, 4);
            Files.write(out, (byte[]) sqdaClass.getMethod("write").invoke(sqda));
            String described = (String) tool.getMethod("describe", sqdaClass).invoke(null, sqda);
            return described.lines().findFirst().orElse("").strip();
        }
    }

    private void showFile() {
        if (made == null) return;
        try {
            Desktop.getDesktop().open(made.getParent().toFile());
        } catch (Exception e) {
            problem(t("Couldn't open the folder: {0}", e.getMessage()));
        }
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Sound Maker"), w, 12 * GUI, 0xFFFFFF);
        centered(g, t("Makes a .sqda (Squid's own sound file) for resource packs."), w, 24 * GUI, 0x808080);
        int left = w / 2 - 100 * GUI;
        int y = 40 * GUI;
        pickButton.setLabel(sound == null ? t("Pick a Sound...") : sound.getFileName().toString());
        pickButton.setBounds(left, y, 200 * GUI, 20 * GUI);
        font.draw(g, t("Title"), left, y + 26 * GUI, GUI, 0xA0A0A0);
        titleField.setBounds(left, y + 36 * GUI, 200 * GUI, 20 * GUI);
        titleField.draw(g, font, GUI, panel.getTime());
        qualitySlider.setBounds(left, y + 60 * GUI, 200 * GUI, 20 * GUI);
        qualitySlider.draw(g, font, GUI);
        loopButton.setLabel(looping ? t("Loop: On") : t("Loop: Off"));
        loopButton.setBounds(left, y + 84 * GUI, 64 * GUI, 20 * GUI);
        if (looping) {
            loopStartField.setBounds(left + 68 * GUI, y + 84 * GUI, 64 * GUI, 20 * GUI);
            loopEndField.setBounds(left + 136 * GUI, y + 84 * GUI, 64 * GUI, 20 * GUI);
            loopStartField.draw(g, font, GUI, panel.getTime());
            loopEndField.draw(g, font, GUI, panel.getTime());
            font.draw(g, t("From (s)"), left + 68 * GUI, y + 106 * GUI, GUI, 0x808080);
            font.draw(g, t("To (s)"), left + 136 * GUI, y + 106 * GUI, GUI, 0x808080);
        } else {
            loopStartField.setBounds(-1000, -1000, 0, 0);
            loopEndField.setBounds(-1000, -1000, 0, 0);
        }
        font.draw(g, t("Tempo (BPM, for beat cues)"), left, y + 118 * GUI, GUI, 0xA0A0A0);
        bpmField.setBounds(left, y + 128 * GUI, 98 * GUI, 20 * GUI);
        findButton.setLabel(finding ? t("Listening...") : t("Find Loop"));
        findButton.setBounds(sound == null ? -1000 : left + 102 * GUI, y + 128 * GUI, 98 * GUI, 20 * GUI);
        bpmField.draw(g, font, GUI, panel.getTime());
        makeButton.setLabel(working ? t("Making...") : t("Make .sqda"));
        makeButton.setBounds(left, y + 154 * GUI, made == null ? 200 * GUI : 98 * GUI, 20 * GUI);
        folderButton.setBounds(made == null ? -1000 : left + 102 * GUI, y + 154 * GUI, 98 * GUI, 20 * GUI);
        String m = message;
        if (m != null) centered(g, m, w, h - 44 * GUI, messageIsProblem ? 0xFF5555 : 0x55FF55);
        doneButton.setBounds(left, h - 28 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        qualitySlider.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        for (McTextField f : fields) f.setFocused(f.contains(x, y));
        if (qualitySlider.mousePressed(x, y)) return;
        super.mousePressed(x, y);
    }

    @Override
    public void mouseDragged(int x, int y) {
        qualitySlider.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased(int x, int y) {
        qualitySlider.mouseReleased();
    }

    @Override
    public void keyTyped(char c) {
        for (McTextField f : fields) f.keyTyped(c);
    }

    @Override
    public void keyPressed(int keyCode, boolean ctrl) {
        for (McTextField f : fields) f.keyPressed(keyCode, ctrl);
    }
}
