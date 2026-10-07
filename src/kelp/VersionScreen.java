package kelp;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A scrolling list of every Minecraft version, downloaded from Mojang, for picking one. */
public class VersionScreen extends Screen {
    private static final String[] FILTERS = {"Releases", "Snapshots", "Everything"};

    private final Screen parent;
    private final Consumer<VersionManifest.Version> onPick;
    private final McList<VersionManifest.Version> list = new McList<>(220);
    private final McButton useButton = new McButton("Use This Version", this::use);
    private final McButton filterButton = new McButton("", this::nextFilter);
    private final McButton cancelButton = new McButton("Cancel", this::back);

    // The download happens on another thread, so these are volatile to be seen by the drawing thread
    private volatile List<VersionManifest.Version> allVersions;
    private volatile String status = "Loading versions...";
    private int filter = 0;

    /** current is the version picked before, or null. onPick gets the version chosen. */
    public VersionScreen(OceanPanel panel, Screen parent, VersionManifest.Version current,
                         Consumer<VersionManifest.Version> onPick) {
        super(panel);
        this.parent = parent;
        this.onPick = onPick;
        list.setSelected(current);
        buttons.add(useButton);
        buttons.add(filterButton);
        buttons.add(cancelButton);

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

    private void back() {
        panel.setScreen(parent);
    }

    private void nextFilter() {
        filter = (filter + 1) % FILTERS.length;
        list.scrollToTop();
    }

    private void use() {
        onPick.accept(list.getSelected());
        panel.setScreen(parent);
    }

    /** The versions that pass the current filter. */
    private List<VersionManifest.Version> filtered() {
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

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, "Select Version", w, 12 * GUI, 0xFFFFFF);

        VersionManifest.Version selected = list.getSelected();
        list.setItems(filtered());
        if (selected != null && list.getSelected() == null) list.setSelected(selected); // keep it while it's filtered out
        list.draw(g, font, w, 32 * GUI, h - 60 * GUI, status, (gg, v, x, y, width) -> {
            font.draw(gg, v.id(), x, y, GUI, 0xFFFFFF);
            String details = typeName(v.type()) + "  " + v.releaseTime().substring(0, 10);
            font.draw(gg, details, x + width - font.width(details, GUI), y, GUI, 0xA0A0A0);
        });

        // Use This Version stays grayed out until a version is picked
        int buttonsY = h - 52 * GUI;
        useButton.setActive(list.getSelected() != null);
        filterButton.setLabel("Show: " + FILTERS[filter]);
        useButton.setBounds(w / 2 - 100 * GUI, buttonsY, 200 * GUI, 20 * GUI);
        filterButton.setBounds(w / 2 - 100 * GUI, buttonsY + 24 * GUI, 98 * GUI, 20 * GUI);
        cancelButton.setBounds(w / 2 + 2 * GUI, buttonsY + 24 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
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
