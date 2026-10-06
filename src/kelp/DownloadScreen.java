package kelp;

import java.awt.Color;
import java.awt.Graphics2D;

/** Downloads a Minecraft version with a progress bar, then starts it and keeps an eye on it. */
public class DownloadScreen extends Screen {
    private static final String PLAYER_NAME = "Player"; // offline name until Kelp has settings

    private enum Phase { DOWNLOADING, STARTING, RUNNING, CLOSED, CRASHED, FAILED }

    private final TitleScreen parent;
    private final VersionManifest.Version version;
    private final boolean withSquid;
    private final GameInstaller installer = new GameInstaller();
    private final McButton button = new McButton("Cancel", this::leave);

    // Set by the background thread, read while drawing
    private volatile Phase phase = Phase.DOWNLOADING;
    private volatile String error;
    private volatile int exitCode;

    public DownloadScreen(OceanPanel panel, TitleScreen parent, VersionManifest.Version version, boolean withSquid) {
        super(panel);
        this.parent = parent;
        this.version = version;
        this.withSquid = withSquid;
        buttons.add(button);

        Thread worker = new Thread(() -> {
            try {
                installer.install(version);
                phase = Phase.STARTING;
                Process game = Launcher.launch(version.id(), PLAYER_NAME, withSquid);
                phase = Phase.RUNNING;
                exitCode = game.waitFor();
                phase = exitCode == 0 ? Phase.CLOSED : Phase.CRASHED;
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
                phase = Phase.FAILED;
            }
        }, "download and launch");
        worker.setDaemon(true); // don't keep Kelp open just for this (the game keeps running on its own)
        worker.start();
    }

    private void leave() {
        if (phase == Phase.DOWNLOADING) installer.getDownloader().cancel();
        panel.setScreen(parent); // if the game is running, it keeps running
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int centerY = h / 2;
        int titleY = centerY - 40 * GUI;
        int lineY = centerY - 16 * GUI;
        String name = "Minecraft " + version.id() + (withSquid ? " + Squid" : "");

        switch (phase) {
            case DOWNLOADING -> {
                centered(g, font, "Downloading Minecraft " + version.id(), w, titleY, 0xFFFFFF);
                drawProgress(g, font, w, lineY);
                button.setLabel("Cancel");
            }
            case STARTING -> {
                centered(g, font, "Starting " + name + "...", w, titleY, 0xFFFFFF);
                button.setLabel("Back");
            }
            case RUNNING -> {
                centered(g, font, name + " is running!", w, titleY, 0xFFFFFF);
                centered(g, font, "Have fun. Kelp will wait down here.", w, lineY, 0xA0A0A0);
                button.setLabel("Back");
            }
            case CLOSED -> {
                centered(g, font, name + " closed.", w, titleY, 0xFFFFFF);
                button.setLabel("Done");
            }
            case CRASHED -> {
                centered(g, font, name + " crashed (code " + exitCode + ")", w, titleY, 0xFF5555);
                centered(g, font, "What it said is saved in:", w, lineY, 0xA0A0A0);
                centered(g, font, "Kelp\\instances\\" + version.id() + "\\kelp-output.log", w, lineY + 12 * GUI, 0xFFFFFF);
                button.setLabel("Back");
            }
            case FAILED -> {
                centered(g, font, "Something went wrong:", w, titleY, 0xFF5555);
                centered(g, font, error, w, lineY, 0xFFFFFF);
                button.setLabel("Back");
            }
        }

        button.setBounds(w / 2 - 100 * GUI, centerY + 30 * GUI, 200 * GUI, 20 * GUI);
        button.draw(g, font, GUI);
    }

    /** A progress bar in the style of Minecraft's loading screen: a white outline that fills up. */
    private void drawProgress(Graphics2D g, McFont font, int w, int barY) {
        Downloader downloader = installer.getDownloader();
        int barW = 200 * GUI;
        int barH = 10 * GUI;
        int barX = (w - barW) / 2;
        long total = downloader.getBytesTotal();
        double progress = total > 0 ? (double) downloader.getBytesDone() / total : 0;
        g.setColor(Color.WHITE);
        g.fillRect(barX, barY, barW, barH);
        g.setColor(new Color(0x0B1633));
        g.fillRect(barX + GUI, barY + GUI, barW - 2 * GUI, barH - 2 * GUI);
        g.setColor(Color.WHITE);
        g.fillRect(barX + 2 * GUI, barY + 2 * GUI, (int) ((barW - 4 * GUI) * progress), barH - 4 * GUI);

        String counts = installer.getStage();
        if (downloader.getFilesTotal() > 0) {
            counts = downloader.getFilesDone() + " / " + downloader.getFilesTotal() + " files   "
                    + megabytes(downloader.getBytesDone()) + " / " + megabytes(total) + " MB";
        }
        centered(g, font, counts, w, barY + barH + 6 * GUI, 0xA0A0A0);
    }

    private static void centered(Graphics2D g, McFont font, String text, int w, int y, int rgb) {
        font.draw(g, text, (w - font.width(text, GUI)) / 2, y, GUI, rgb);
    }

    private static String megabytes(long bytes) {
        return String.format("%.1f", bytes / 1048576.0);
    }
}
