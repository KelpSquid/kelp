package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.io.IOException;

/** Makes a new instance: pick a name, a Minecraft version, and its loader (Squid unless you pick another). */
public class NewInstanceScreen extends Screen {
    private final InstancesScreen parent;
    private final McTextField nameField = new McTextField(32);
    private final McButton versionButton = new McButton("", this::pickVersion);
    private final McButton loaderButton = new McButton("", this::nextLoader);
    private final McButton createButton = new McButton(t("Create"), this::create);
    private final McButton cancelButton = new McButton(t("Cancel"), this::back);
    private final McButton importButton = new McButton(t("Import Modpack..."), this::pickModpack);

    private VersionManifest.Version version;
    private Loader loader = Loader.SQUID;
    private boolean nameTyped; // once the player types a name, picking a version stops changing it
    private String problem;

    public NewInstanceScreen(OceanPanel panel, InstancesScreen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(versionButton);
        buttons.add(loaderButton);
        buttons.add(createButton);
        buttons.add(cancelButton);
        buttons.add(importButton);
        nameField.setFocused(true);
        nameField.onChange(text -> nameTyped = true);
    }

    private void nextLoader() {
        loader = loader.next();
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
            Instance instance = Instance.create(nameField.getText(), version, loader);
            parent.select(instance);
            panel.setScreen(parent);
        } catch (IOException e) {
            problem = t("Couldn't make it: {0}", e.getMessage());
        }
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int left = w / 2 - 100 * GUI;
        int top = h / 4;
        centered(g, t("New Instance"), w, 12 * GUI, 0xFFFFFF);

        font.draw(g, t("Name"), left, top, GUI, 0xA0A0A0);
        nameField.setBounds(left, top + 11 * GUI, 200 * GUI, 20 * GUI);
        nameField.draw(g, font, GUI, panel.getTime());

        versionButton.setLabel(version == null ? t("Version: Pick one") : t("Version: {0}", version.id()));
        loaderButton.setLabel(loader.recommended() ? t("Loader: {0} (best)", loader.label()) : t("Loader: {0}", loader.label()));
        versionButton.setBounds(left, top + 40 * GUI, 200 * GUI, 20 * GUI);
        loaderButton.setBounds(left, top + 64 * GUI, 200 * GUI, 20 * GUI);
        int aboutColor = !loader.ready() ? 0xFFFF55 : loader.recommended() ? 0x55FF55 : 0xA0A0A0;
        centered(g, t(loader.about()), w, top + 88 * GUI, aboutColor);
        if (problem != null) centered(g, problem, w, top + 102 * GUI, 0xFF5555);

        createButton.setActive(version != null && !nameField.getText().isBlank() && loader.ready());
        createButton.setBounds(left, h - 28 * GUI, 98 * GUI, 20 * GUI);
        cancelButton.setBounds(w / 2 + 2 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        importButton.setBounds(left, h - 52 * GUI, 200 * GUI, 20 * GUI);
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

    /** A Modrinth (.mrpack), CurseForge or Prism Launcher pack becomes a new instance. */
    private void pickModpack() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(new java.io.File(System.getProperty("user.home"), "Downloads"));
        chooser.setDialogTitle(t("Pick a modpack"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(t("Modpacks (.mrpack, .zip)"), "mrpack", "zip"));
        if (chooser.showDialog(javax.swing.SwingUtilities.getWindowAncestor(panel), t("Import")) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        panel.setScreen(new ImportScreen(panel, parent, chooser.getSelectedFile().toPath()));
    }

    /** A modpack dropped onto the window is imported too. */
    @Override
    public void filesDropped(java.util.List<java.nio.file.Path> files) {
        if (!files.isEmpty()) panel.setScreen(new ImportScreen(panel, parent, files.get(0)));
    }
}
