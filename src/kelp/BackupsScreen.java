package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One world's backups, newest first. Restore brings the picked one back as a new world next to the original, so
 * nothing is ever lost by restoring.
 */
public class BackupsScreen extends Screen {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final Instance instance;
    private final String world;
    private final McList<Backups.Backup> list = new McList<>(220);
    private final McButton restoreButton = new McButton(t("Restore"), this::restore);
    private final McButton doneButton = new McButton(t("Done"), this::back);
    private volatile String status;
    private volatile int statusColor = 0xA0A0A0;
    private volatile boolean working;

    public BackupsScreen(OceanPanel panel, Screen parent, Instance instance, String world) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        this.world = world;
        buttons.add(restoreButton);
        buttons.add(doneButton);
    }

    private void back() {
        panel.setScreen(parent);
    }

    @Override
    public void shown() {
        list.setItems(Backups.list(instance, world));
    }

    private void restore() {
        Backups.Backup backup = list.getSelected();
        if (backup == null || working) return;
        working = true;
        status = t("Restoring...");
        statusColor = 0xA0A0A0;
        Thread worker = new Thread(() -> {
            try {
                java.nio.file.Path restored = Backups.restore(instance, backup);
                status = t("Restored as \"{0}\". Your world is still there too.", restored.getFileName());
                statusColor = 0x55FF55;
            } catch (IOException e) {
                status = t("Couldn't restore it: {0}", e.getMessage());
                statusColor = 0xFF5555;
            } finally {
                working = false;
            }
        }, "restore backup");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Backups of {0}", world), w, 12 * GUI, 0xFFFFFF);
        int listBottom = h - 60 * GUI;
        String empty = list.getItems().isEmpty() ? t("No backups yet. They're made every 15 minutes while you play.") : null;
        list.draw(g, font, w, 32 * GUI, listBottom, empty, (gg, backup, x, y, width) -> {
            font.draw(gg, WHEN.format(Instant.ofEpochMilli(backup.time())), x, y, GUI, 0xFFFFFF);
            String size = String.format("%.1f MB", sizeOf(backup) / 1048576.0);
            font.draw(gg, size, x + width - font.width(size, GUI), y, GUI, 0xA0A0A0);
        });
        if (status != null) centered(g, status, w, listBottom + 6 * GUI, statusColor);
        restoreButton.setActive(list.getSelected() != null && !working);
        restoreButton.setBounds(w / 2 - 100 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        doneButton.setBounds(w / 2 + 2 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    private static long sizeOf(Backups.Backup backup) {
        try {
            return java.nio.file.Files.size(backup.file());
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        list.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        if (list.mousePressed(x, y) == null) super.mousePressed(x, y);
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        list.mouseWheel(notches);
    }
}
