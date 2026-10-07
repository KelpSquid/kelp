package kelp;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;

/** The Options screen: your player name and how much memory the game gets. */
public class SettingsScreen extends Screen {
    private final Screen parent;
    private final McTextField nameField = new McTextField(16);
    private final McButton memoryButton = new McButton("", this::nextMemory);
    private final McButton folderButton = new McButton("Open Kelp Folder", this::openFolder);
    private final McButton doneButton = new McButton("Done", this::done);

    public SettingsScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(memoryButton);
        buttons.add(folderButton);
        buttons.add(doneButton);
        nameField.setText(Settings.playerName());
        // Save the name as soon as it's a real Minecraft name, so there's no Save button to forget
        nameField.onChange(name -> {
            if (Settings.isValidName(name)) Settings.setPlayerName(name);
        });
    }

    private void nextMemory() {
        int[] choices = Settings.MEMORY_CHOICES;
        int current = 0;
        for (int i = 0; i < choices.length; i++) if (choices[i] == Settings.memoryGb()) current = i;
        Settings.setMemoryGb(choices[(current + 1) % choices.length]);
    }

    private void openFolder() {
        try {
            Files.createDirectories(Folders.home());
            Desktop.getDesktop().open(Folders.home().toFile());
        } catch (IOException e) {
            System.err.println("Couldn't open " + Folders.home() + ": " + e.getMessage());
        }
    }

    private void done() {
        panel.setScreen(parent);
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int left = w / 2 - 100 * GUI;
        int top = h / 4;
        centered(g, "Options", w, 12 * GUI, 0xFFFFFF);

        font.draw(g, "Player name", left, top, GUI, 0xA0A0A0);
        nameField.setBounds(left, top + 11 * GUI, 200 * GUI, 20 * GUI);
        nameField.draw(g, font, GUI, panel.getTime());
        if (Settings.isValidName(nameField.getText())) {
            centered(g, "Offline names show in single player and LAN.", w, top + 35 * GUI, 0x808080);
        } else {
            centered(g, "Use 3-16 letters, numbers or _", w, top + 35 * GUI, 0xFF5555);
        }

        int gb = Settings.memoryGb();
        memoryButton.setLabel("Memory: " + (gb == 0 ? "Minecraft's choice" : gb + " GB"));
        memoryButton.setBounds(left, top + 52 * GUI, 200 * GUI, 20 * GUI);
        folderButton.setBounds(left, top + 76 * GUI, 200 * GUI, 20 * GUI);
        doneButton.setBounds(left, h - 28 * GUI, 200 * GUI, 20 * GUI);
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
