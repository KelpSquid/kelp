package kelp;

import java.awt.Graphics2D;
import java.io.IOException;

/** Makes a new instance: pick a name, a Minecraft version, and whether it uses Squid. */
public class NewInstanceScreen extends Screen {
    private final InstancesScreen parent;
    private final McTextField nameField = new McTextField(32);
    private final McButton versionButton = new McButton("", this::pickVersion);
    private final McButton squidButton = new McButton("", this::toggleSquid);
    private final McButton createButton = new McButton("Create", this::create);
    private final McButton cancelButton = new McButton("Cancel", this::back);

    private VersionManifest.Version version;
    private boolean squid;
    private boolean nameTyped; // once the player types a name, picking a version stops changing it
    private String problem;

    public NewInstanceScreen(OceanPanel panel, InstancesScreen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(versionButton);
        buttons.add(squidButton);
        buttons.add(createButton);
        buttons.add(cancelButton);
        nameField.setFocused(true);
        nameField.onChange(text -> nameTyped = true);
    }

    private void toggleSquid() {
        squid = !squid;
    }

    private void back() {
        panel.setScreen(parent);
    }

    private void pickVersion() {
        panel.setScreen(new VersionScreen(panel, this, version, picked -> {
            version = picked;
            if (!nameTyped) nameField.setText("Minecraft " + picked.id()); // a name to start from
        }));
    }

    private void create() {
        try {
            Instance instance = Instance.create(nameField.getText(), version, squid);
            parent.select(instance);
            panel.setScreen(parent);
        } catch (IOException e) {
            problem = "Couldn't make it: " + e.getMessage();
        }
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int left = w / 2 - 100 * GUI;
        int top = h / 4;
        centered(g, "New Instance", w, 12 * GUI, 0xFFFFFF);

        font.draw(g, "Name", left, top, GUI, 0xA0A0A0);
        nameField.setBounds(left, top + 11 * GUI, 200 * GUI, 20 * GUI);
        nameField.draw(g, font, GUI, panel.getTime());

        versionButton.setLabel(version == null ? "Version: Pick one" : "Version: " + version.id());
        squidButton.setLabel("Squid: " + (squid ? "ON" : "OFF"));
        versionButton.setBounds(left, top + 40 * GUI, 200 * GUI, 20 * GUI);
        squidButton.setBounds(left, top + 64 * GUI, 200 * GUI, 20 * GUI);
        if (problem != null) centered(g, problem, w, top + 90 * GUI, 0xFF5555);

        createButton.setActive(version != null && !nameField.getText().isBlank());
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
        nameField.keyTyped(c);
    }

    @Override
    public void keyPressed(int keyCode, boolean ctrl) {
        nameField.keyPressed(keyCode, ctrl);
    }
}
