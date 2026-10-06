package kelp;

import java.awt.Color;
import java.awt.Graphics2D;

/** Downloads a Minecraft version and shows a progress bar while it happens. */
public class DownloadScreen extends Screen {
    private final TitleScreen parent;
    private final VersionManifest.Version version;
    private final GameInstaller installer = new GameInstaller();
    private final McButton button = new McButton("Cancel", this::leave);

    private volatile boolean finished;
    private volatile String error;

    public DownloadScreen(OceanPanel panel, TitleScreen parent, VersionManifest.Version version) {
        super(panel);
        this.parent = parent;
        this.version = version;
        buttons.add(button);

        Thread download = new Thread(() -> {
            try {
                installer.install(version);
                finished = true;
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
        }, "game download");
        download.setDaemon(true); // don't keep Kelp running just for this
        download.start();
    }

    private void leave() {
        installer.getDownloader().cancel(); // does nothing if it's already done
        panel.setScreen(parent);
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        Downloader downloader = installer.getDownloader();
        int centerY = h / 2;

        String header = finished ? "Minecraft " + version.id() + " is ready!" : "Downloading Minecraft " + version.id();
        centered(g, font, header, w, centerY - 40 * GUI, 0xFFFFFF);

        if (error != null) {
            centered(g, font, "Something went wrong:", w, centerY - 16 * GUI, 0xFF5555);
            centered(g, font, error, w, centerY - 4 * GUI, 0xFFFFFF);
            button.setLabel("Back");
        } else if (finished) {
            centered(g, font, "Launching it is the next step.", w, centerY - 16 * GUI, 0xA0A0A0);
            button.setLabel("Done");
        } else {
            // The progress bar, in the style of Minecraft's loading screen: a white outline that fills up
            int barW = 200 * GUI;
            int barH = 10 * GUI;
            int barX = (w - barW) / 2;
            int barY = centerY - 16 * GUI;
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

        button.setBounds(w / 2 - 100 * GUI, centerY + 30 * GUI, 200 * GUI, 20 * GUI);
        button.draw(g, font, GUI);
    }

    private static void centered(Graphics2D g, McFont font, String text, int w, int y, int rgb) {
        font.draw(g, text, (w - font.width(text, GUI)) / 2, y, GUI, rgb);
    }

    private static String megabytes(long bytes) {
        return String.format("%.1f", bytes / 1048576.0);
    }
}
