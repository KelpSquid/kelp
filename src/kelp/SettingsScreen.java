package kelp;

import static kelp.Lang.t;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;

/** The Options screen: who you play as, and how much memory the game gets. */
public class SettingsScreen extends Screen {
    private final Screen parent;
    private final McButton languageButton = new McButton("", this::pickLanguage);
    private final McButton accountButton = new McButton("", () -> panel.setScreen(new AccountsScreen(panel, this)));
    private final McButton memoryButton = new McButton("", this::nextMemory);
    private final McButton backupButton = new McButton("", () -> Settings.setAutoBackup(!Settings.autoBackup()));
    private final McButton folderButton = new McButton(t("Open Kelp Folder"), this::openFolder);
    private final McButton themeButton = new McButton("", () -> panel.setScreen(new ThemeScreen(panel, this)));
    private final McButton emblemButton = new McButton(t("Emblem"), () -> panel.setScreen(new EmblemScreen(panel, this)));
    private final McButton doneButton = new McButton(t("Done"), this::done);

    public SettingsScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(accountButton);
        buttons.add(memoryButton);
        buttons.add(languageButton);
        buttons.add(backupButton);
        buttons.add(folderButton);
        buttons.add(themeButton);
        buttons.add(emblemButton);
        buttons.add(doneButton);
    }

    private void nextMemory() {
        int[] choices = Settings.MEMORY_CHOICES;
        int current = 0;
        for (int i = 0; i < choices.length; i++) if (choices[i] == Settings.memoryGb()) current = i;
        Settings.setMemoryGb(choices[(current + 1) % choices.length]);
    }

    /** The list of every language. Picking one makes the title screen again, so its buttons change too. */
    private void pickLanguage() {
        panel.setScreen(new LanguageScreen(panel, this));
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
        centered(g, t("Options"), w, 12 * GUI, 0xFFFFFF);

        Account account = Accounts.active();
        accountButton.setLabel(t("Account: {0}", account.name()));
        accountButton.setBounds(left, top + 6 * GUI, 146 * GUI, 20 * GUI);
        emblemButton.setBounds(left + 150 * GUI, top + 6 * GUI, 50 * GUI, 20 * GUI);
        EmblemScreen.drawSmall(g, account.id(), left - 24 * GUI, top + 6 * GUI, 20 * GUI); // the emblem, next to the name
        String kind = account.microsoft() ? t("Signed in with Microsoft.") : t("Offline names show in single player and LAN.");
        centered(g, kind, w, top + 30 * GUI, 0x808080);

        int gb = Settings.memoryGb();
        memoryButton.setLabel(gb == 0 ? t("Memory: Minecraft's choice") : t("Memory: {0} GB", gb));
        memoryButton.setBounds(left, top + 52 * GUI, 200 * GUI, 20 * GUI);
        languageButton.setLabel(t("Language: {0}", Lang.current().name()));
        languageButton.setBounds(left, top + 76 * GUI, 200 * GUI, 20 * GUI);
        backupButton.setLabel(Settings.autoBackup() ? t("World Backups: Every 15 min") : t("World Backups: Off"));
        backupButton.setBounds(left, top + 100 * GUI, 200 * GUI, 20 * GUI);
        themeButton.setLabel(t("Theme: {0}", Theme.current().label()));
        themeButton.setBounds(left, top + 124 * GUI, 200 * GUI, 20 * GUI);
        folderButton.setBounds(left, top + 148 * GUI, 200 * GUI, 20 * GUI);
        doneButton.setBounds(left, h - 28 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }
}
