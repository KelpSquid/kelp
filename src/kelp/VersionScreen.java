package kelp;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** A scrolling list of every Minecraft version, downloaded from Mojang. */
public class VersionScreen extends Screen {
    private static final String[] FILTERS = {"Releases", "Snapshots", "Everything"};
    private static final int ROW = 14 * GUI;       // height of one row in the list
    private static final int ROW_WIDTH = 220 * GUI;

    private final TitleScreen parent;
    private final McButton filterButton = new McButton("", this::nextFilter);
    private final McButton doneButton = new McButton("Done", this::done);

    // The download happens on another thread, so these are volatile to be seen by the drawing thread
    private volatile List<VersionManifest.Version> allVersions;
    private volatile String status = "Loading versions...";

    private int filter = 0;
    private VersionManifest.Version selected;
    private double scroll = 0; // how far the list is scrolled down, in screen pixels

    // Where the list was drawn last frame, so clicks can find the row under the mouse
    private int listTop;
    private int listBottom;
    private int rowX;
    private int mouseX = -1;
    private int mouseY = -1;

    public VersionScreen(OceanPanel panel, TitleScreen parent) {
        super(panel);
        this.parent = parent;
        this.selected = parent.getVersion();
        buttons.add(filterButton);
        buttons.add(doneButton);
        updateFilterLabel();

        Thread download = new Thread(() -> {
            try {
                allVersions = VersionManifest.download();
                status = null;
            } catch (Exception e) {
                status = "Couldn't reach Mojang: " + e.getMessage();
            }
        }, "version list download");
        download.setDaemon(true); // don't keep Kelp running just for this
        download.start();
    }

    private void nextFilter() {
        filter = (filter + 1) % FILTERS.length;
        updateFilterLabel();
        scroll = 0;
    }

    private void updateFilterLabel() {
        filterButton.setLabel("Show: " + FILTERS[filter]);
    }

    private void done() {
        if (selected != null) parent.setVersion(selected);
        panel.setScreen(parent);
    }

    /** The versions that pass the current filter. */
    private List<VersionManifest.Version> shown() {
        List<VersionManifest.Version> result = new ArrayList<>();
        List<VersionManifest.Version> all = allVersions;
        if (all == null) return result;
        for (VersionManifest.Version v : all) {
            boolean release = v.type().equals("release");
            boolean snapshot = v.type().equals("snapshot");
            if (release || (snapshot && filter >= 1) || filter == 2) result.add(v);
        }
        return result;
    }

    private double maxScroll(int count) {
        return Math.max(0, count * ROW + 4 * GUI - (listBottom - listTop));
    }

    /** Which row the point is on, or -1 if it isn't on one. */
    private int rowAt(int x, int y, int count) {
        if (x < rowX || x >= rowX + ROW_WIDTH || y < listTop || y >= listBottom) return -1;
        int row = (int) ((y - listTop - 2 * GUI + scroll) / ROW);
        return row >= 0 && row < count ? row : -1;
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        List<VersionManifest.Version> shown = shown();

        listTop = 32 * GUI;
        listBottom = h - 36 * GUI;
        rowX = (w - ROW_WIDTH) / 2;
        scroll = Math.max(0, Math.min(scroll, maxScroll(shown.size())));

        // Header
        String header = "Select Version";
        font.draw(g, header, (w - font.width(header, GUI)) / 2, 12 * GUI, GUI, 0xFFFFFF);

        // A dark see-through box behind the list
        int boxX = rowX - 4 * GUI;
        int boxW = ROW_WIDTH + 8 * GUI;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(boxX, listTop, boxW, listBottom - listTop);

        String message = status;
        if (message != null) {
            font.draw(g, message, (w - font.width(message, GUI)) / 2, (listTop + listBottom) / 2 - 4 * GUI, GUI, 0xFFFFFF);
        } else {
            drawRows(g, font, shown, boxX, boxW);
            drawScrollbar(g, shown.size(), boxX + boxW);
        }

        // Buttons along the bottom
        int buttonsY = h - 28 * GUI;
        filterButton.setBounds(w / 2 - 100 * GUI, buttonsY, 98 * GUI, 20 * GUI);
        doneButton.setBounds(w / 2 + 2 * GUI, buttonsY, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    private void drawRows(Graphics2D g, McFont font, List<VersionManifest.Version> shown, int boxX, int boxW) {
        Graphics2D clip = (Graphics2D) g.create();
        clip.clipRect(boxX, listTop, boxW, listBottom - listTop); // rows scrolled out of the box get cut off
        int hovered = rowAt(mouseX, mouseY, shown.size());

        for (int i = 0; i < shown.size(); i++) {
            int y = (int) (listTop + 2 * GUI + i * ROW - scroll);
            if (y + ROW < listTop || y > listBottom) continue; // off screen, skip it
            VersionManifest.Version v = shown.get(i);

            if (v.equals(selected)) {
                // Minecraft's selection look: a white outline around a black row
                clip.setColor(Color.WHITE);
                clip.fillRect(rowX, y, ROW_WIDTH, ROW - GUI);
                clip.setColor(Color.BLACK);
                clip.fillRect(rowX + GUI, y + GUI, ROW_WIDTH - 2 * GUI, ROW - 3 * GUI);
            } else if (i == hovered) {
                clip.setColor(new Color(255, 255, 255, 40));
                clip.fillRect(rowX, y, ROW_WIDTH, ROW - GUI);
            }

            int textY = y + 3 * GUI;
            font.draw(clip, v.id(), rowX + 4 * GUI, textY, GUI, 0xFFFFFF);
            String details = typeName(v.type()) + "  " + v.releaseTime().substring(0, 10);
            font.draw(clip, details, rowX + ROW_WIDTH - 4 * GUI - font.width(details, GUI), textY, GUI, 0xA0A0A0);
        }
        clip.dispose();
    }

    private void drawScrollbar(Graphics2D g, int count, int x) {
        double max = maxScroll(count);
        if (max <= 0) return; // everything fits, no scrollbar needed
        int trackH = listBottom - listTop;
        int thumbH = Math.max(16 * GUI, (int) ((double) trackH * trackH / (trackH + max)));
        int thumbY = listTop + (int) ((trackH - thumbH) * scroll / max);
        int barW = 6 * GUI;
        g.setColor(Color.BLACK);
        g.fillRect(x, listTop, barW, trackH);
        g.setColor(new Color(0x808080));
        g.fillRect(x, thumbY, barW, thumbH);
        g.setColor(new Color(0xC0C0C0));
        g.fillRect(x, thumbY, barW - GUI, thumbH - GUI);
    }

    private static String typeName(String type) {
        return switch (type) {
            case "release" -> "Release";
            case "snapshot" -> "Snapshot";
            case "old_beta" -> "Beta";
            case "old_alpha" -> "Alpha";
            default -> type;
        };
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        List<VersionManifest.Version> shown = shown();
        int row = rowAt(x, y, shown.size());
        if (row >= 0) {
            selected = shown.get(row);
            return;
        }
        super.mousePressed(x, y);
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        scroll += notches * ROW * 3; // three rows per notch
    }
}
