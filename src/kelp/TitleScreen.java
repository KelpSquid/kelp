package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;

/** The first thing you see: the Kelp title, a splash and the main buttons. */
public class TitleScreen extends Screen {
    private final McButton play = new McButton(t("Play"), this::play);
    private final McButton instances = new McButton(t("Instances"), () -> panel.setScreen(new InstancesScreen(panel, this)));
    private final McButton settings = new McButton(t("Options..."), () -> panel.setScreen(new SettingsScreen(panel, this)));
    private final McButton quit = new McButton(t("Quit Game"), () -> System.exit(0));
    private final McButton update = new McButton("", this::update);
    private String updateProblem;

    private Instance last; // what Play starts: the default instance, or the one played last
    private int count;
    private double countReadAt = -1;

    public TitleScreen(OceanPanel panel) {
        super(panel);
        buttons.add(play);
        buttons.add(instances);
        buttons.add(settings);
        buttons.add(quit);
        buttons.add(update);
    }

    /** Closes Kelp and opens the new version (see {@link Updates}). Minecraft has to be closed, since Squid's files are in use. */
    private void update() {
        try {
            Updates.installAndRestart();
        } catch (java.io.IOException e) {
            updateProblem = t("Couldn't update: {0}", e.getMessage());
        }
    }

    @Override
    public void shown() {
        last = Instance.toPlay(); // the default instance, or else the one played last
    }

    /** Plays the last instance again, or opens the instance list if there isn't one yet. */
    private void play() {
        if (last != null) panel.setScreen(new DownloadScreen(panel, this, last));
        else panel.setScreen(new InstancesScreen(panel, this));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();

        // The buttons, laid out like Minecraft's title screen (sizes are in GUI pixels, times GUI)
        int buttonsY = h / 4 + 48 * GUI;
        int left = w / 2 - 100 * GUI;
        play.setBounds(left, buttonsY, 200 * GUI, 20 * GUI);
        instances.setBounds(left, buttonsY + 24 * GUI, 200 * GUI, 20 * GUI);
        settings.setBounds(left, buttonsY + 60 * GUI, 98 * GUI, 20 * GUI);
        quit.setBounds(left + 102 * GUI, buttonsY + 60 * GUI, 98 * GUI, 20 * GUI);
        // A newer Kelp, once it's downloaded, waits in the top-right corner. It can't go in while Minecraft is open.
        String ready = Updates.ready();
        if (ready != null) {
            update.setLabel(t("Update to {0}", ready));
            update.setActive(!RunningGames.any());
            update.setBounds(w - 104 * GUI, 4 * GUI, 100 * GUI, 20 * GUI);
            String hint = updateProblem != null ? updateProblem : RunningGames.any() ? t("Close Minecraft first, then update.") : null;
            if (hint != null) font.draw(g, hint, w - 4 * GUI - font.width(hint, GUI), 28 * GUI, GUI, updateProblem != null ? 0xFF5555 : 0xFFFF55);
        } else {
            update.setBounds(-1000, -1000, 0, 0); // out of sight until there's an update
        }
        for (McButton b : buttons) b.draw(g, font, GUI);

        // The title, sitting above the buttons
        int titleScale = 12;
        String title = "Kelp";
        int titleX = (w - font.width(title, titleScale)) / 2;
        int titleY = buttonsY - 8 * titleScale - 40;
        font.draw(g, title, titleX, titleY, titleScale, 0xFFFFFF);

        // A yellow splash, tilted and pulsing like the one on Minecraft's title screen.
        // Minecraft's own formula: it pulses every second and long splashes get shrunk to fit.
        String splash = t("A launcher from the deep!");
        double pulse = 1.8 - Math.abs(Math.sin(panel.getTime() % 1.0 * Math.PI * 2) * 0.1);
        double splashScale = pulse * 100 / (font.width(splash, 1) + 32) * GUI;
        Graphics2D s = (Graphics2D) g.create();
        s.translate(w / 2 + 90 * GUI, titleY + 6 * titleScale);
        s.rotate(Math.toRadians(-20));
        s.scale(splashScale, splashScale);
        font.draw(s, splash, -font.width(splash, 1) / 2, -4, 1, 0xFFFF00);
        s.dispose();

        // What Play will start, in the bottom-left corner where Minecraft shows its own version
        String playing = last == null ? t("No instances yet") : t("Play: {0}", last.summary());
        font.draw(g, playing, 2 * GUI, h - 10 * GUI, GUI, 0xFFFFFF);
        // The Squid Count of whoever is playing, read again about once a second
        if (panel.getTime() - countReadAt > 1 || countReadAt < 0) {
            count = SquidCount.points(Accounts.active().id());
            countReadAt = panel.getTime();
        }
        font.draw(g, t("Squid Count: {0}", count), 2 * GUI, h - 20 * GUI, GUI, 0xFFAA00);

        // Mojang's rules ask projects like Kelp to say this clearly, so it's always on the title screen
        String[] notice = {t("NOT AN OFFICIAL MINECRAFT PRODUCT."), t("NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.")};
        int noticeW = Math.max(font.width(notice[0], 1), font.width(notice[1], 1)) + 8;
        g.setColor(new java.awt.Color(0, 0, 0, 150)); // a dark backing so kelp and bubbles never cover it
        g.fillRect(w - noticeW, h - notice.length * 10 - 5, noticeW, notice.length * 10 + 5);
        for (int i = 0; i < notice.length; i++) {
            int lineY = h - (notice.length - i) * 10 - 2;
            font.draw(g, notice[i], w - font.width(notice[i], 1) - 4, lineY, 1, 0xE0E0E0);
        }
    }
}
