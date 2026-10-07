package kelp;

import java.awt.Graphics2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/** Changes one instance's Minecraft settings, the same ones as in the game's own Options screen. */
public class GameOptionsScreen extends Screen {
    private static final String[] GUI_SCALES = {"Auto", "1", "2", "3", "4"};

    private final Instance instance;
    private final McButton doneButton;
    private final List<McSlider> sliders = new ArrayList<>();
    private final List<Object> layout = new ArrayList<>(); // sliders and buttons, in the order they're laid out
    private GameOptions options;
    private String problem;

    public GameOptionsScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.instance = instance;
        doneButton = new McButton("Done", () -> panel.setScreen(parent));
        buttons.add(doneButton);
        try {
            options = GameOptions.load(instance);
        } catch (IOException e) {
            problem = e.getMessage();
            return;
        }

        // FOV is saved as -1 to 1, where 0 means 70
        slider("fov", 30, 110, 1, 70 + 40 * options.getDouble("fov", 0),
                v -> "FOV: " + (int) v, v -> String.valueOf((v - 70) / 40));
        slider("renderDistance", 2, 32, 1, options.getDouble("renderDistance", 12),
                v -> "Render Distance: " + (int) v, v -> String.valueOf((int) v));
        slider("simulationDistance", 5, 32, 1, options.getDouble("simulationDistance", 12),
                v -> "Simulation: " + (int) v, v -> String.valueOf((int) v));
        slider("maxFps", 10, 260, 10, options.getDouble("maxFps", 120),
                v -> "Max FPS: " + (v >= 260 ? "Unlimited" : String.valueOf((int) v)), v -> String.valueOf((int) v));
        slider("gamma", 0, 1, 0.01, options.getDouble("gamma", 0.5),
                v -> "Brightness: " + percent(v), String::valueOf);
        slider("mouseSensitivity", 0, 1, 0.005, options.getDouble("mouseSensitivity", 0.5),
                v -> "Sensitivity: " + percent(v * 2), String::valueOf); // the game shows 0.5 as 100%
        slider("soundCategory_master", 0, 1, 0.01, options.getDouble("soundCategory_master", 1),
                v -> "Master Volume: " + percent(v), String::valueOf);
        slider("soundCategory_music", 0, 1, 0.01, options.getDouble("soundCategory_music", 1),
                v -> "Music: " + percent(v), String::valueOf);
        guiScaleButton();
        toggle("VSync", "enableVsync", true);
        toggle("Fullscreen", "fullscreen", false);
        toggle("View Bobbing", "bobView", true);
        toggle("Auto-Jump", "autoJump", false);
    }

    private static String percent(double v) {
        return Math.round(v * 100) + "%";
    }

    /** A slider for a number setting. save turns the slider's value into the text written to options.txt. */
    private void slider(String key, double min, double max, double step, double start,
                        DoubleFunction<String> label, DoubleFunction<String> save) {
        double value = Math.max(min, Math.min(max, start));
        McSlider slider = new McSlider(min, max, step, value, label, v -> write(key, save.apply(v)));
        sliders.add(slider);
        layout.add(slider);
    }

    private void guiScaleButton() {
        int[] scale = {Math.max(0, Math.min(4, (int) options.getDouble("guiScale", 0)))};
        McButton[] button = new McButton[1]; // so the button's action can change its own label
        button[0] = new McButton("GUI Scale: " + GUI_SCALES[scale[0]], () -> {
            scale[0] = (scale[0] + 1) % GUI_SCALES.length;
            write("guiScale", String.valueOf(scale[0]));
            button[0].setLabel("GUI Scale: " + GUI_SCALES[scale[0]]);
        });
        add(button[0]);
    }

    /** An ON/OFF button for a true/false setting. */
    private void toggle(String name, String key, boolean fallback) {
        boolean[] on = {options.getBoolean(key, fallback)};
        McButton[] button = new McButton[1];
        button[0] = new McButton(name + ": " + (on[0] ? "ON" : "OFF"), () -> {
            on[0] = !on[0];
            write(key, String.valueOf(on[0]));
            button[0].setLabel(name + ": " + (on[0] ? "ON" : "OFF"));
        });
        add(button[0]);
    }

    private void add(McButton button) {
        buttons.add(button);
        layout.add(button);
    }

    private void write(String key, String value) {
        try {
            options.set(key, value);
            problem = null;
        } catch (IOException e) {
            problem = "Couldn't save: " + e.getMessage();
        }
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, "Game Options: " + instance.name(), w, 12 * GUI, 0xFFFFFF);

        // Two columns of 150, like the game's own options screen
        int top = 32 * GUI;
        for (int i = 0; i < layout.size(); i++) {
            int x = i % 2 == 0 ? w / 2 - 155 * GUI : w / 2 + 5 * GUI;
            int y = top + i / 2 * 24 * GUI;
            if (layout.get(i) instanceof McSlider slider) {
                slider.setBounds(x, y, 150 * GUI, 20 * GUI);
                slider.draw(g, font, GUI);
            } else if (layout.get(i) instanceof McButton button) {
                button.setBounds(x, y, 150 * GUI, 20 * GUI);
                button.draw(g, font, GUI);
            }
        }

        int bottom = h - 28 * GUI;
        if (problem != null) {
            centered(g, problem, w, bottom - 14 * GUI, 0xFF5555);
        } else if (RunningGames.isRunning(instance)) {
            // Minecraft saves its own options when it closes, which would undo changes made now
            centered(g, "Close the game first, or it will undo these.", w, bottom - 14 * GUI, 0xFFFF55);
        } else {
            centered(g, "Changes are used the next time this instance starts.", w, bottom - 14 * GUI, 0x808080);
        }
        doneButton.setBounds(w / 2 - 100 * GUI, bottom, 200 * GUI, 20 * GUI);
        doneButton.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        for (McSlider s : sliders) s.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
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
}
