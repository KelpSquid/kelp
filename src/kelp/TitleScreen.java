package kelp;

import java.awt.Graphics2D;
import java.util.List;

/** The first thing you see: the Kelp title, a splash and the main buttons. */
public class TitleScreen extends Screen {
    private final McButton play = new McButton("Play", this::play);
    private final McButton instances = new McButton("Instances", () -> panel.setScreen(new InstancesScreen(panel, this)));
    private final McButton settings = new McButton("Options...", () -> panel.setScreen(new SettingsScreen(panel, this)));
    private final McButton quit = new McButton("Quit Game", () -> System.exit(0));

    private Instance last; // the instance played last, which Play starts

    public TitleScreen(OceanPanel panel) {
        super(panel);
        buttons.add(play);
        buttons.add(instances);
        buttons.add(settings);
        buttons.add(quit);
    }

    @Override
    public void shown() {
        last = Instance.find(Settings.lastInstance());
        if (last == null) {
            // Nothing played yet (or it was deleted): use the newest instance, if there is one
            List<Instance> all = Instance.all();
            if (!all.isEmpty()) last = all.get(0);
        }
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
        for (McButton b : buttons) b.draw(g, font, GUI);

        // The title, sitting above the buttons
        int titleScale = 12;
        String title = "Kelp";
        int titleX = (w - font.width(title, titleScale)) / 2;
        int titleY = buttonsY - 8 * titleScale - 40;
        font.draw(g, title, titleX, titleY, titleScale, 0xFFFFFF);

        // A yellow splash, tilted and pulsing like the one on Minecraft's title screen.
        // Minecraft's own formula: it pulses every second and long splashes get shrunk to fit.
        String splash = "A launcher from the deep!";
        double pulse = 1.8 - Math.abs(Math.sin(panel.getTime() % 1.0 * Math.PI * 2) * 0.1);
        double splashScale = pulse * 100 / (font.width(splash, 1) + 32) * GUI;
        Graphics2D s = (Graphics2D) g.create();
        s.translate(w / 2 + 90 * GUI, titleY + 6 * titleScale);
        s.rotate(Math.toRadians(-20));
        s.scale(splashScale, splashScale);
        font.draw(s, splash, -font.width(splash, 1) / 2, -4, 1, 0xFFFF00);
        s.dispose();

        // What Play will start, in the bottom-left corner where Minecraft shows its own version
        String playing = last == null ? "No instances yet" : last.name() + " - Minecraft " + last.version().id()
                + (last.squid() ? " + Squid" : "");
        font.draw(g, playing, 2 * GUI, h - 10 * GUI, GUI, 0xFFFFFF);

        // Mojang's rules ask projects like Kelp to say this clearly, so it's always on the title screen
        String[] notice = {"NOT AN OFFICIAL MINECRAFT PRODUCT.", "NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."};
        int noticeW = Math.max(font.width(notice[0], 1), font.width(notice[1], 1)) + 8;
        g.setColor(new java.awt.Color(0, 0, 0, 150)); // a dark backing so kelp and bubbles never cover it
        g.fillRect(w - noticeW, h - notice.length * 10 - 5, noticeW, notice.length * 10 + 5);
        for (int i = 0; i < notice.length; i++) {
            int lineY = h - (notice.length - i) * 10 - 2;
            font.draw(g, notice[i], w - font.width(notice[i], 1) - 4, lineY, 1, 0xE0E0E0);
        }
    }
}
