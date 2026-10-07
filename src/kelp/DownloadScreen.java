package kelp;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** Downloads what an instance's version needs with a progress bar, then starts it and keeps an eye on it. */
public class DownloadScreen extends Screen {
    private enum Phase { DOWNLOADING, STARTING, RUNNING, CLOSED, CRASHED, FAILED }

    private final Screen parent;
    private final Instance instance;
    private final VersionManifest.Version version;
    private final boolean withSquid;
    private final GameInstaller installer = new GameInstaller();
    private final McButton button = new McButton("Cancel", this::leave);

    // Set by the background thread, read while drawing
    private volatile Phase phase = Phase.DOWNLOADING;
    private volatile String error;
    private volatile int exitCode;
    private SquidReport report;        // what Squid last said, checked about once a second
    private double reportCheckedAt = -1;

    public DownloadScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        this.version = instance.version();
        this.withSquid = instance.squid();
        buttons.add(button);
        Settings.setLastInstance(instance.id()); // so the title screen's Play button starts this one next time

        // If this instance's game is already open, just keep an eye on it instead of starting a second copy
        Process alreadyOpen = RunningGames.get(instance);
        if (alreadyOpen != null) phase = Phase.RUNNING;

        Thread worker = new Thread(() -> {
            try {
                Process game = alreadyOpen;
                if (game == null) {
                    installer.install(findDetails(version), instance.loader(), instance.mods());
                    if (installer.getLoaderVersion() != null) instance.setLoaderVersion(installer.getLoaderVersion());
                    phase = Phase.STARTING;
                    game = Launcher.launch(version.id(), instance.folder(), Accounts.readyToPlay(), instance.loader(),
                            instance.loaderVersion(), Settings.memoryGb());
                    RunningGames.add(instance, game);
                    instance.markPlayed();
                }
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

    /** Instances from before Kelp had them don't know where their version's details live, so look it up once. */
    private VersionManifest.Version findDetails(VersionManifest.Version version) throws Exception {
        if (!version.url().isEmpty()) return version;
        for (VersionManifest.Version v : VersionManifest.download()) {
            if (v.id().equals(version.id())) {
                instance.setVersion(v);
                return v;
            }
        }
        throw new IllegalStateException("Mojang doesn't list Minecraft " + version.id() + " anymore");
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
        String name = instance.name();

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
                SquidReport squid = squidReport();
                String line = "Have fun. Kelp will wait down here.";
                if (squid != null && squid.status().equals("loading")) line = "Squid is starting your mods...";
                if (squid != null && squid.status().equals("running")) {
                    line = "Squid loaded " + squid.modCount() + (squid.modCount() == 1 ? " mod." : " mods.") + " Have fun!";
                }
                centered(g, font, line, w, lineY, 0xA0A0A0);
                int y = lineY + 12 * GUI;
                if (squid != null && !squid.skipped().isEmpty()) {
                    // Mods that couldn't work this time. The game still opened without them.
                    List<SquidReport.Skipped> skipped = squid.skipped();
                    if (skipped.size() == 1) {
                        for (String part : wrap(font, "Squid skipped " + skipped.get(0).mod() + ": " + skipped.get(0).reason(), 300 * GUI)) {
                            centered(g, font, part, w, y, 0xFFFF55);
                            y += 12 * GUI;
                        }
                    } else {
                        List<String> names = skipped.stream().map(SquidReport.Skipped::mod).toList();
                        centered(g, font, "Squid skipped " + skipped.size() + " mods: " + String.join(", ", names), w, y, 0xFFFF55);
                        centered(g, font, "The Mods screen says why.", w, y + 12 * GUI, 0xFFFF55);
                        y += 24 * GUI;
                    }
                }
                if (squid != null && !squid.problems().isEmpty()) {
                    // A mod's hook kept breaking, so Squid switched it off. The game is fine, but that mod may not work.
                    List<String> names = squid.problems();
                    String who = names.size() == 1 ? names.get(0) + " had a problem" : String.join(", ", names) + " had problems";
                    centered(g, font, who + ", so Squid turned part of it off.", w, y, 0xFFFF55);
                }
                button.setLabel("Back");
            }
            case CLOSED -> {
                centered(g, font, name + " closed.", w, titleY, 0xFFFFFF);
                button.setLabel("Done");
            }
            case CRASHED -> {
                SquidReport squid = squidReport();
                if (squid != null && squid.status().equals("failed")) {
                    // Squid stopped the game before it opened, and says why
                    String who = squid.mod() != null ? squid.mod() + " broke while starting:" : "Squid couldn't start the game:";
                    centered(g, font, who, w, titleY, 0xFF5555);
                    int y = lineY;
                    for (String part : wrap(font, String.valueOf(squid.error()), 300 * GUI)) {
                        centered(g, font, part, w, y, 0xFFFFFF);
                        y += 12 * GUI;
                    }
                } else {
                    centered(g, font, name + " crashed (code " + exitCode + ")", w, titleY, 0xFF5555);
                    centered(g, font, "What it said is saved in:", w, lineY, 0xA0A0A0);
                    centered(g, font, "Kelp\\instances\\" + instance.id() + "\\kelp-output.log", w, lineY + 12 * GUI, 0xFFFFFF);
                }
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

    /** Squid's report for this game, read again at most once a second (or never, without Squid). */
    private SquidReport squidReport() {
        if (!withSquid) return null;
        double now = panel.getTime();
        if (reportCheckedAt < 0 || now - reportCheckedAt >= 1) {
            report = SquidReport.read(instance.folder());
            reportCheckedAt = now;
        }
        return report;
    }

    /** Splits text into lines that fit the width, breaking between words (and at line breaks). Shows 3 lines at most. */
    private static List<String> wrap(McFont font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            String line = "";
            for (String word : paragraph.split(" ")) {
                String longer = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(longer, GUI) > maxWidth) {
                    lines.add(line);
                    line = word;
                } else {
                    line = longer;
                }
            }
            lines.add(line);
        }
        return lines.size() > 3 ? lines.subList(0, 3) : lines;
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
