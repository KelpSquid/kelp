package kelp;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;

/** The Options screen: who you play as, and how much memory the game gets. */
public class SettingsScreen extends Screen {
    private final Screen parent;
    private final McButton accountButton = new McButton("", () -> panel.setScreen(new AccountsScreen(panel, this)));
    private final McButton memoryButton = new McButton("", this::nextMemory);
    private final McButton folderButton = new McButton("Open Kelp Folder", this::openFolder);
    private final McButton doneButton = new McButton("Done", this::done);

    public SettingsScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(accountButton);
        buttons.add(memoryButton);
        buttons.add(folderButton);
        buttons.add(doneButton);
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

        Account account = Accounts.active();
        accountButton.setLabel("Account: " + account.name());
        accountButton.setBounds(left, top + 6 * GUI, 200 * GUI, 20 * GUI);
        String kind = account.microsoft() ? "Signed in with Microsoft." : "Offline names show in single player and LAN.";
        centered(g, kind, w, top + 30 * GUI, 0x808080);

        int gb = Settings.memoryGb();
        memoryButton.setLabel("Memory: " + (gb == 0 ? "Minecraft's choice" : gb + " GB"));
        memoryButton.setBounds(left, top + 52 * GUI, 200 * GUI, 20 * GUI);
        folderButton.setBounds(left, top + 76 * GUI, 200 * GUI, 20 * GUI);
        doneButton.setBounds(left, h - 28 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }
}
