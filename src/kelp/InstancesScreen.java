package kelp;

import java.awt.Graphics2D;
import java.io.IOException;

/** Every instance, with buttons to play, make, change and delete them. */
public class InstancesScreen extends Screen {
    private final Screen parent;
    private final McList<Instance> list = new McList<>(220);
    private final McButton playButton = new McButton("Play", this::play);
    private final McButton newButton = new McButton("New Instance", () -> panel.setScreen(new NewInstanceScreen(panel, this)));
    private final McButton squidButton = new McButton("", this::toggleSquid);
    private final McButton modsButton = new McButton("Mods", this::openMods);
    private final McButton deleteButton = new McButton("Delete", this::delete);
    private final McButton backButton = new McButton("Back", this::back);
    private String problem;

    public InstancesScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(playButton);
        buttons.add(newButton);
        buttons.add(squidButton);
        buttons.add(modsButton);
        buttons.add(deleteButton);
        buttons.add(backButton);
    }

    private void back() {
        panel.setScreen(parent);
    }

    /** Selects an instance, like a new one that was just made. */
    public void select(Instance instance) {
        list.setSelected(instance);
    }

    @Override
    public void shown() {
        Instance selected = list.getSelected();
        list.setItems(Instance.all()); // they might have changed while another screen was open
        if (selected != null && list.getItems().contains(selected)) list.setSelected(selected);
        else if (list.getSelected() == null) list.setSelected(Instance.find(Settings.lastInstance()));
    }

    private void play() {
        panel.setScreen(new DownloadScreen(panel, this, list.getSelected()));
    }

    private void toggleSquid() {
        Instance instance = list.getSelected();
        try {
            instance.setSquid(!instance.squid());
        } catch (IOException e) {
            problem = "Couldn't save that: " + e.getMessage();
        }
    }

    private void openMods() {
        panel.setScreen(new ModsScreen(panel, this, list.getSelected()));
    }

    private void delete() {
        Instance instance = list.getSelected();
        panel.setScreen(new ConfirmScreen(panel, "Delete " + instance.name() + "?",
                "Its worlds, settings and mods will be gone forever!", () -> {
            try {
                instance.delete();
                problem = null;
            } catch (IOException e) {
                problem = "Couldn't delete all of it. Is the game still running?";
            }
            panel.setScreen(this);
        }, () -> panel.setScreen(this)));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, "Instances", w, 12 * GUI, 0xFFFFFF);

        int listBottom = h - 84 * GUI;
        String empty = list.getItems().isEmpty() ? "No instances yet. Make one with New Instance!" : null;
        list.draw(g, font, w, 32 * GUI, listBottom, empty, (gg, instance, x, y, width) -> {
            font.draw(gg, instance.name(), x, y, GUI, 0xFFFFFF);
            String details = "Minecraft " + instance.version().id() + (instance.squid() ? " + Squid" : "");
            font.draw(gg, details, x + width - font.width(details, GUI), y, GUI, 0xA0A0A0);
        });
        if (problem != null) centered(g, problem, w, listBottom + 2 * GUI, 0xFF5555);

        // Buttons: everything but New Instance and Back needs an instance picked first
        Instance selected = list.getSelected();
        for (McButton b : new McButton[] {playButton, squidButton, modsButton, deleteButton}) b.setActive(selected != null);
        squidButton.setLabel("Squid: " + (selected != null && selected.squid() ? "ON" : "OFF"));
        int y = h - 76 * GUI;
        int left = w / 2 - 100 * GUI;
        int right = w / 2 + 2 * GUI;
        playButton.setBounds(left, y, 98 * GUI, 20 * GUI);
        newButton.setBounds(right, y, 98 * GUI, 20 * GUI);
        squidButton.setBounds(left, y + 24 * GUI, 98 * GUI, 20 * GUI);
        modsButton.setBounds(right, y + 24 * GUI, 98 * GUI, 20 * GUI);
        deleteButton.setBounds(left, y + 48 * GUI, 98 * GUI, 20 * GUI);
        backButton.setBounds(right, y + 48 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        list.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        if (list.mousePressed(x, y) == null) super.mousePressed(x, y);
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        list.mouseWheel(notches);
    }
}
