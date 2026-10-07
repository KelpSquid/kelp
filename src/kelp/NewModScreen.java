package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Path;

/** Makes a new mod: type a name, and Kelp writes a working mod file and opens it for editing. */
public class NewModScreen extends Screen {
    private final Screen parent;
    private final Instance instance;
    private final McTextField nameField = new McTextField(32);
    private final McButton createButton = new McButton(t("Create"), this::create);
    private final McButton cancelButton = new McButton(t("Cancel"), this::back);
    private String problem;

    public NewModScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        buttons.add(createButton);
        buttons.add(cancelButton);
        nameField.setText("My Mod");
        nameField.setFocused(true);
    }

    private void back() {
        panel.setScreen(parent);
    }

    private void create() {
        try {
            Path file = ModTemplate.create(instance.mods(), nameField.getText());
            if (!instance.squid()) instance.setSquid(true); // mods only load with Squid, so switch it on
            try {
                Editors.open(file);
            } catch (IOException e) {
                System.err.println("Couldn't open " + file + ": " + e.getMessage()); // it's still in the mods folder
            }
            back();
        } catch (IOException e) {
            problem = t("Couldn't make it: {0}", e.getMessage());
        }
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int left = w / 2 - 100 * GUI;
        int top = h / 4;
        centered(g, t("New Mod"), w, 12 * GUI, 0xFFFFFF);

        font.draw(g, t("Mod name"), left, top, GUI, 0xA0A0A0);
        nameField.setBounds(left, top + 11 * GUI, 200 * GUI, 20 * GUI);
        nameField.draw(g, font, GUI, panel.getTime());
        String file = ModTemplate.className(nameField.getText()) + ".java";
        centered(g, t("Kelp makes {0} and opens it.", file), w, top + 38 * GUI, 0x808080);
        centered(g, t("Change it, save it, then play!"), w, top + 50 * GUI, 0x808080);
        if (problem != null) centered(g, problem, w, top + 66 * GUI, 0xFF5555);

        createButton.setActive(!nameField.getText().isBlank());
        createButton.setBounds(left, h - 28 * GUI, 98 * GUI, 20 * GUI);
        cancelButton.setBounds(w / 2 + 2 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mousePressed(int x, int y) {
        nameField.setFocused(nameField.contains(x, y));
        super.mousePressed(x, y);
    }

    @Override
    public void keyTyped(char c) {
        if (c == '\n') {
            if (!nameField.getText().isBlank()) create(); // Enter makes it
        } else {
            nameField.keyTyped(c);
        }
    }

    @Override
    public void keyPressed(int keyCode, boolean ctrl) {
        nameField.keyPressed(keyCode, ctrl);
    }
}
