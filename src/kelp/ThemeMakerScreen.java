package kelp;

import static kelp.Lang.t;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Make a theme: a name, a scene, colors, and if you like, your own background picture and music. Everything shows
 * behind the screen as you change it. Save puts it with your themes; Cancel puts the old look back.
 */
public class ThemeMakerScreen extends Screen {
    private final Screen parent;
    private final McTextField nameField = new McTextField(32);
    private final McButton sceneButton = new McButton("", this::nextScene);
    private final McButton pictureButton = new McButton("", this::pickPicture);
    private final McButton musicButton = new McButton("", this::pickMusic);
    private final McButton saveButton = new McButton(t("Save"), this::save);
    private final McButton cancelButton = new McButton(t("Cancel"), this::cancel);
    private final List<McSlider> sliders = new ArrayList<>();
    private final Theme before = Theme.current();
    private Theme.Scene scene;
    private double hue;         // 0-359: the background tiles' color
    private double darkness;    // 0-1: how dark the base color is
    private double buttonHue;   // -1 for sea-glass teal, or 0-359
    private Path picture;
    private Path music;
    private String problem;

    /** editing is the theme to change, or null for a new one. */
    public ThemeMakerScreen(OceanPanel panel, Screen parent, Theme editing) {
        super(panel);
        this.parent = parent;
        Theme start = editing != null ? editing : Theme.current();
        nameField.setText(editing != null ? editing.name() : t("My Theme"));
        scene = start.scene();
        float[] hsb = Color.RGBtoHSB(start.water() >> 16 & 0xFF, start.water() >> 8 & 0xFF, start.water() & 0xFF, null);
        hue = start.water() < 0 ? 210 : Math.round(hsb[0] * 360);
        darkness = 1 - Color.RGBtoHSB(start.base() >> 16 & 0xFF, start.base() >> 8 & 0xFF, start.base() & 0xFF, null)[2];
        buttonHue = start.buttons();
        picture = editing != null ? editing.picture() : null;
        music = editing != null ? editing.music() : null;
        sliders.add(new McSlider(0, 359, 1, hue, v -> t("Color: {0}", (int) v), v -> {
            hue = v;
            preview();
        }));
        sliders.add(new McSlider(0, 1, 0.01, darkness, v -> t("Darkness: {0}%", (int) Math.round(v * 100)), v -> {
            darkness = v;
            preview();
        }));
        sliders.add(new McSlider(-1, 359, 1, buttonHue, v -> v < 0 ? t("Buttons: Sea glass") : t("Buttons: {0}", (int) v), v -> {
            buttonHue = v;
            preview();
        }));
        for (McButton b : new McButton[] {sceneButton, pictureButton, musicButton, saveButton, cancelButton}) buttons.add(b);
        preview();
    }

    /** The theme as it is right now, shown behind the screen. */
    private Theme building() {
        int water = scene == Theme.Scene.SKY || scene == Theme.Scene.END || scene == Theme.Scene.PLAIN ? -1
                : Color.HSBtoRGB((float) (hue / 360), 0.65f, 0.9f) & 0xFFFFFF;
        int base = Color.HSBtoRGB((float) (hue / 360), 0.75f, (float) Math.max(0.03, 1 - darkness)) & 0xFFFFFF;
        return new Theme("making", nameField.getText(), scene, water, base, (int) buttonHue, picture, music);
    }

    private void preview() {
        Theme.preview(building());
    }

    private void nextScene() {
        Theme.Scene[] all = Theme.Scene.values();
        scene = all[(scene.ordinal() + 1) % all.length];
        preview();
    }

    private void pickPicture() {
        if (picture != null) {
            picture = null; // the button says Remove Picture while there is one
            preview();
            return;
        }
        Path picked = pick(t("Pick a background picture"), t("Pictures (.png, .jpg)"), "png", "jpg", "jpeg");
        if (picked != null) {
            picture = picked;
            preview();
        }
    }

    private void pickMusic() {
        if (music != null) {
            music = null;
            preview();
            return;
        }
        Path picked = pick(t("Pick music"), t("Music (.wav, .mp3, .flac, .ogg, .sqda)"), ThemeMusic.KINDS.toArray(String[]::new));
        if (picked != null) {
            music = picked;
            preview();
        }
    }

    private Path pick(String title, String kind, String... extensions) {
        JFileChooser chooser = new JFileChooser(new java.io.File(System.getProperty("user.home")));
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(kind, extensions));
        if (chooser.showDialog(SwingUtilities.getWindowAncestor(panel), t("Use")) != JFileChooser.APPROVE_OPTION) return null;
        return chooser.getSelectedFile().toPath();
    }

    private void save() {
        Theme made = building();
        try {
            Theme saved = Theme.save(nameField.getText().isBlank() ? t("My Theme") : nameField.getText().strip(), made.scene(), made.water(),
                    made.base(), made.buttons(), made.picture(), made.music());
            Theme.use(saved);
            panel.setScreen(parent);
        } catch (IOException e) {
            problem = t("Couldn't save it: {0}", e.getMessage());
        }
    }

    private void cancel() {
        Theme.preview(before);
        panel.setScreen(parent);
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Theme Maker"), w, 12 * GUI, 0xFFFFFF);
        int left = w / 2 - 100 * GUI;
        int y = 30 * GUI;
        font.draw(g, t("Name"), left, y, GUI, 0xA0A0A0);
        nameField.setBounds(left, y + 10 * GUI, 200 * GUI, 20 * GUI);
        nameField.draw(g, font, GUI, panel.getTime());
        sceneButton.setLabel(t("Scene: {0}", t(scene.label)));
        sceneButton.setBounds(left, y + 34 * GUI, 200 * GUI, 20 * GUI);
        for (int i = 0; i < sliders.size(); i++) {
            sliders.get(i).setBounds(left, y + 58 * GUI + i * 24 * GUI, 200 * GUI, 20 * GUI);
            sliders.get(i).draw(g, font, GUI);
        }
        pictureButton.setLabel(picture == null ? t("Background Picture...") : t("Remove Picture"));
        pictureButton.setBounds(left, y + 130 * GUI, 98 * GUI, 20 * GUI);
        musicButton.setLabel(music == null ? t("Music...") : t("Remove Music"));
        musicButton.setBounds(left + 102 * GUI, y + 130 * GUI, 98 * GUI, 20 * GUI);
        if (problem != null) centered(g, problem, w, h - 40 * GUI, 0xFF5555);
        saveButton.setBounds(left, h - 28 * GUI, 98 * GUI, 20 * GUI);
        cancelButton.setBounds(left + 102 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        for (McSlider s : sliders) s.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        nameField.setFocused(nameField.contains(x, y));
        for (McSlider s : sliders) {
            if (s.mousePressed(x, y)) return;
        }
        super.mousePressed(x, y);
    }

    @Override
    public void mouseDragged(int x, int y) {
        for (McSlider s : sliders) s.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased(int x, int y) {
        for (McSlider s : sliders) s.mouseReleased();
    }

    @Override
    public void keyTyped(char c) {
        nameField.keyTyped(c);
    }

    @Override
    public void keyPressed(int keyCode, boolean ctrl) {
        nameField.keyPressed(keyCode, ctrl);
    }

    /** A picture dropped onto the window becomes the background. */
    @Override
    public void filesDropped(List<Path> files) {
        if (!files.isEmpty()) {
            String name = files.get(0).getFileName().toString().toLowerCase();
            if (ThemeMusic.isMusic(files.get(0))) music = files.get(0);
            else picture = files.get(0);
            preview();
        }
    }
}
