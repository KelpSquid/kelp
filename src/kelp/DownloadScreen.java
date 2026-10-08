package kelp;

import static kelp.Lang.t;

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
    private final McButton button = new McButton(t("Cancel"), this::leave);
    // After a crash: what to do about it
    private final McButton copyButton = new McButton(t("Copy Report"), this::copyReport);
    private final McButton logButton = new McButton(t("Open Log"), this::openLog);
    private final McButton turnOffButton = new McButton("", this::turnOffCulprit);
    // After a crash with Squid: play anyway, with every mod off this time
    private final McButton safeButton = new McButton(t("Play Without Mods"), this::playWithoutMods);
    private volatile long startedAt = System.currentTimeMillis();
    private CrashHelper.Diagnosis diagnosis; // worked out once, the first time the crash is shown
    private String crashNote; // what happened after pressing one of the crash buttons

    // Set by the background thread, read while drawing
    private volatile Phase phase = Phase.DOWNLOADING;
    private volatile String error;
    private volatile int exitCode;
    private volatile boolean cancelled; // Cancel was pressed before the game started, so it mustn't start
    private SquidReport report;        // what Squid last said, checked about once a second
    private double reportCheckedAt = -1;

    public DownloadScreen(OceanPanel panel, Screen parent, Instance instance) {
        this(panel, parent, instance, null);
    }

    /** Like the other; world (a world's folder name, or null) is opened straight away, skipping the title screen. */
    public DownloadScreen(OceanPanel panel, Screen parent, Instance instance, String world) {
        this(panel, parent, instance, world, null);
    }

    /** Like the others; server (an address, or null) is joined straight away. */
    public DownloadScreen(OceanPanel panel, Screen parent, Instance instance, String world, String server) {
        this(panel, parent, instance, world, server, false);
    }

    /** Like the others; safe starts Squid with every mod off (Play Without Mods). */
    public DownloadScreen(OceanPanel panel, Screen parent, Instance instance, String world, String server, boolean safe) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        this.version = instance.version();
        this.withSquid = instance.squid();
        buttons.add(button);
        buttons.add(copyButton);
        buttons.add(logButton);
        buttons.add(turnOffButton);
        buttons.add(safeButton);
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
                    if (cancelled) return;
                    phase = Phase.STARTING;
                    startedAt = System.currentTimeMillis();
                    Account account = Accounts.readyToPlay();
                    if (cancelled) return;
                    game = Launcher.launch(version.id(), instance.folder(), account, instance.loader(),
                            instance.loaderVersion(), Settings.memoryGb(), true, world, server, safe);
                    if (cancelled) { // pressed in the split second while the game was starting
                        game.destroy();
                        return;
                    }
                    RunningGames.add(instance, game);
                    instance.markPlayed();
                }
                phase = Phase.RUNNING;
                exitCode = game.waitFor();
                if (exitCode == FastBoot.RESTART && alreadyOpen == null && !cancelled) {
                    // Squid's fast boot didn't match the mods anymore: start it the normal way (which makes a new one)
                    FastBoot.forget(instance.folder());
                    game = Launcher.launch(version.id(), instance.folder(), Accounts.readyToPlay(), instance.loader(),
                            instance.loaderVersion(), Settings.memoryGb(), false, world, server, safe);
                    RunningGames.add(instance, game);
                    exitCode = game.waitFor();
                }
                phase = exitCode == 0 ? Phase.CLOSED : Phase.CRASHED;
            } catch (Exception e) {
                if (cancelled) return; // stopping because of Cancel isn't a problem to show
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
        throw new IllegalStateException(t("Mojang doesn't list Minecraft {0} anymore", version.id()));
    }

    /** Copies what went wrong, with the important part of the log, so it can be pasted when asking for help. */
    private void copyReport() {
        if (diagnosis == null) return;
        String report = "Kelp " + (Updates.current() == null ? "dev" : Updates.current()) + ", Minecraft " + version.id() + ", "
                + instance.loader().label() + "\n" + diagnosis.what() + "\n" + diagnosis.fix() + "\n\n" + diagnosis.details();
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(report), null);
            crashNote = t("Copied! Paste it wherever you're asking for help.");
        } catch (RuntimeException e) {
            crashNote = t("Couldn't copy it: {0}", e.getMessage());
        }
    }

    private void openLog() {
        try {
            java.awt.Desktop.getDesktop().open(instance.folder().resolve("kelp-output.log").toFile());
        } catch (Exception e) {
            crashNote = t("Couldn't open it: {0}", e.getMessage());
        }
    }

    /** Turns off the mod the crash helper blamed, so the game can be tried again without it. */
    /** Starts the game again with every mod off (Squid's safe mode), so a broken mod can't keep it from opening. */
    private void playWithoutMods() {
        if (RunningGames.isRunning(instance)) return;
        panel.setScreen(new DownloadScreen(panel, parent, instance, null, null, true));
    }

    private void turnOffCulprit() {
        if (diagnosis == null || diagnosis.culprit() == null) return;
        try {
            diagnosis.culprit().toggle();
            crashNote = t("Turned off {0}. Try playing again!", diagnosis.culprit().name());
            diagnosis = new CrashHelper.Diagnosis(diagnosis.what(), diagnosis.fix(), null, diagnosis.details());
        } catch (java.io.IOException e) {
            crashNote = t("Couldn't switch it. Is the game still running? ({0})", e.getMessage());
        }
    }

    private void leave() {
        // Before the game has started, leaving means cancel. Once it's running, it keeps running.
        if (phase == Phase.DOWNLOADING || phase == Phase.STARTING) {
            cancelled = true;
            installer.getDownloader().cancel();
        }
        panel.setScreen(parent);
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
                centered(g, font, t("Downloading Minecraft {0}", version.id()), w, titleY, 0xFFFFFF);
                drawProgress(g, font, w, lineY);
                button.setLabel(t("Cancel"));
            }
            case STARTING -> {
                centered(g, font, t("Starting {0}...", name), w, titleY, 0xFFFFFF);
                if (instance.loaderIsBeta()) centered(g, font, betaNotice(), w, lineY, 0xFFFF55);
                button.setLabel(t("Cancel"));
            }
            case RUNNING -> {
                centered(g, font, t("{0} is running!", name), w, titleY, 0xFFFFFF);
                SquidReport squid = squidReport();
                String line = t("Have fun. Kelp will wait down here.");
                if (squid != null && squid.status().equals("loading")) line = t("Squid is starting your mods...");
                if (squid != null && squid.status().equals("running")) {
                    line = squid.modCount() == 1 ? t("Squid loaded 1 mod. Have fun!") : t("Squid loaded {0} mods. Have fun!", squid.modCount());
                }
                centered(g, font, line, w, lineY, 0xA0A0A0);
                int y = lineY + 12 * GUI;
                if (instance.loaderIsBeta()) {
                    centered(g, font, betaNotice(), w, y, 0xFFFF55);
                    y += 12 * GUI;
                }
                if (squid != null && !squid.skipped().isEmpty()) {
                    // Mods that couldn't work this time. The game still opened without them.
                    List<SquidReport.Skipped> skipped = squid.skipped();
                    if (skipped.size() == 1) {
                        for (String part : wrap(font, t("Squid skipped {0}: {1}", skipped.get(0).mod(), skipped.get(0).reason()), 300 * GUI)) {
                            centered(g, font, part, w, y, 0xFFFF55);
                            y += 12 * GUI;
                        }
                    } else {
                        List<String> names = skipped.stream().map(SquidReport.Skipped::mod).toList();
                        centered(g, font, t("Squid skipped {0} mods: {1}", skipped.size(), String.join(", ", names)), w, y, 0xFFFF55);
                        centered(g, font, t("The Mods screen says why."), w, y + 12 * GUI, 0xFFFF55);
                        y += 24 * GUI;
                    }
                }
                if (squid != null && !squid.problems().isEmpty()) {
                    // A mod's hook kept breaking, so Squid switched it off. The game is fine, but that mod may not work.
                    List<String> names = squid.problems();
                    centered(g, font, t("{0} had a problem, so Squid turned part of it off.", String.join(", ", names)), w, y, 0xFFFF55);
                }
                button.setLabel(t("Back"));
            }
            case CLOSED -> {
                centered(g, font, t("{0} closed.", name), w, titleY, 0xFFFFFF);
                button.setLabel(t("Done"));
            }
            case CRASHED -> {
                SquidReport squid = squidReport();
                if (squid != null && squid.status().equals("failed")) {
                    // Squid stopped the game before it opened, and says why
                    String who = squid.mod() != null ? t("{0} broke while starting:", squid.mod()) : t("Squid couldn't start the game:");
                    centered(g, font, who, w, titleY, 0xFF5555);
                    int y = lineY;
                    for (String part : wrap(font, String.valueOf(squid.error()), 300 * GUI)) {
                        centered(g, font, part, w, y, 0xFFFFFF);
                        y += 12 * GUI;
                    }
                    for (McButton b : new McButton[] {copyButton, logButton, turnOffButton}) b.setBounds(-1000, -1000, 0, 0);
                    safeButton.setBounds(w / 2 - 100 * GUI, centerY + 30 * GUI, 200 * GUI, 20 * GUI);
                    safeButton.draw(g, font, GUI);
                    button.setLabel(t("Back"));
                    button.setBounds(w / 2 - 100 * GUI, centerY + 54 * GUI, 200 * GUI, 20 * GUI);
                    button.draw(g, font, GUI);
                    return;
                } else {
                    // The crash helper says why, in plain words, and what to try
                    if (diagnosis == null) diagnosis = CrashHelper.diagnose(instance, exitCode, startedAt);
                    centered(g, font, t("{0} crashed.", name), w, titleY, 0xFF5555);
                    int y = lineY;
                    for (String part : firstTwo(wrap(font, diagnosis.what(), 300 * GUI))) {
                        centered(g, font, part, w, y, 0xFFFFFF);
                        y += 12 * GUI;
                    }
                    for (String part : firstTwo(wrap(font, diagnosis.fix(), 300 * GUI))) {
                        centered(g, font, part, w, y, 0xFFFF55);
                        y += 12 * GUI;
                    }
                    if (crashNote != null) centered(g, font, crashNote, w, y + 2 * GUI, 0x55FF55);
                    int buttonsY = centerY + 40 * GUI;
                    copyButton.setBounds(w / 2 - 100 * GUI, buttonsY, 98 * GUI, 20 * GUI);
                    logButton.setBounds(w / 2 + 2 * GUI, buttonsY, 98 * GUI, 20 * GUI);
                    copyButton.draw(g, font, GUI);
                    logButton.draw(g, font, GUI);
                    if (diagnosis.culprit() != null && diagnosis.culprit().enabled()) {
                        turnOffButton.setLabel(t("Turn Off {0}", diagnosis.culprit().name()));
                        turnOffButton.setBounds(w / 2 - 100 * GUI, buttonsY + 24 * GUI, 200 * GUI, 20 * GUI);
                        turnOffButton.draw(g, font, GUI);
                    } else {
                        turnOffButton.setBounds(-1000, -1000, 0, 0);
                    }
                    int backY = buttonsY + 48 * GUI;
                    if (withSquid) {
                        safeButton.setBounds(w / 2 - 100 * GUI, backY, 200 * GUI, 20 * GUI);
                        safeButton.draw(g, font, GUI);
                        backY += 24 * GUI;
                    } else {
                        safeButton.setBounds(-1000, -1000, 0, 0);
                    }
                    button.setLabel(t("Back"));
                    button.setBounds(w / 2 - 100 * GUI, backY, 200 * GUI, 20 * GUI);
                    button.draw(g, font, GUI);
                    return;
                }
            }
            case FAILED -> {
                centered(g, font, t("Something went wrong:"), w, titleY, 0xFF5555);
                centered(g, font, error, w, lineY, 0xFFFFFF);
                button.setLabel(t("Back"));
            }
        }

        for (McButton b : new McButton[] {copyButton, logButton, turnOffButton, safeButton}) b.setBounds(-1000, -1000, 0, 0); // only after a crash
        button.setBounds(w / 2 - 100 * GUI, centerY + 30 * GUI, 200 * GUI, 20 * GUI);
        button.draw(g, font, GUI);
    }

    private static List<String> firstTwo(List<String> lines) {
        return lines.subList(0, Math.min(2, lines.size()));
    }

    /** For a loader that only has a beta for this Minecraft version yet. */
    private String betaNotice() {
        return t("{0} for Minecraft {1} is still a beta, so it might crash.", instance.loader().label(), version.id());
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
