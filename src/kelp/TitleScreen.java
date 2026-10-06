package kelp;

import java.awt.Graphics2D;

/** The first thing you see: the Kelp title, a splash and the main buttons. */
public class TitleScreen extends Screen {
    private final McButton play = new McButton("Play", () -> panel.setScreen(new VersionScreen(panel, this)));
    private final McButton instances = new McButton("Instances", () -> { });
    private final McButton settings = new McButton("Settings", () -> { });
    private final McButton quit = new McButton("Quit", () -> System.exit(0));

    private VersionManifest.Version version; // the version picked on the version screen, or null

    public TitleScreen(OceanPanel panel) {
        super(panel);
        buttons.add(play);
        buttons.add(instances);
        buttons.add(settings);
        buttons.add(quit);
    }

    public VersionManifest.Version getVersion() {
        return version;
    }

    public void setVersion(VersionManifest.Version version) {
        this.version = version;
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

        // The picked version in the bottom-left corner, where Minecraft shows its own version
        String versionText = version == null ? "No version picked" : "Minecraft " + version.id();
        font.draw(g, versionText, 2 * GUI, h - 10 * GUI, GUI, 0xFFFFFF);
    }
}
