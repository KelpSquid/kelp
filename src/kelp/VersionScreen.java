package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A scrolling list of Minecraft's stable releases, downloaded from Mojang, for picking one. Snapshots aren't supported. */
public class VersionScreen extends Screen {
    private final Screen parent;
    private final Consumer<VersionManifest.Version> onPick;
    private final McList<VersionManifest.Version> list = new McList<>(220);
    private final McButton useButton = new McButton(t("Use Version"), this::use);
    private final McButton cancelButton = new McButton(t("Cancel"), this::back);

    // The download happens on another thread, so these are volatile to be seen by the drawing thread
    private volatile List<VersionManifest.Version> allVersions;
    private volatile String status = t("Loading versions...");

    /** current is the version picked before, or null. onPick gets the version chosen. */
    public VersionScreen(OceanPanel panel, Screen parent, VersionManifest.Version current,
                         Consumer<VersionManifest.Version> onPick) {
        super(panel);
        this.parent = parent;
        this.onPick = onPick;
        list.setSelected(current);
        buttons.add(useButton);
        buttons.add(cancelButton);

        Thread download = new Thread(() -> {
            try {
                allVersions = VersionManifest.download();
                status = null;
            } catch (Exception e) {
                status = t("Couldn't reach Mojang: {0}", e.getMessage());
            }
        }, "version list download");
        download.setDaemon(true); // don't keep Kelp running just for this
        download.start();
    }

    private void back() {
        panel.setScreen(parent);
    }

    private void use() {
        onPick.accept(list.getSelected());
        panel.setScreen(parent);
    }

    /** Only the stable releases: Kelp and Squid don't support snapshots or old alphas and betas. */
    private List<VersionManifest.Version> filtered() {
        List<VersionManifest.Version> result = new ArrayList<>();
        List<VersionManifest.Version> all = allVersions;
        if (all == null) return result;
        for (VersionManifest.Version v : all) {
            if (v.type().equals("release")) result.add(v);
        }
        return result;
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Select Version"), w, 12 * GUI, 0xFFFFFF);

        VersionManifest.Version selected = list.getSelected();
        list.setItems(filtered());
        if (selected != null && list.getSelected() == null) list.setSelected(selected); // keep it even if it's not in the list
        list.draw(g, font, w, 32 * GUI, h - 36 * GUI, status, (gg, v, x, y, width) -> {
            font.draw(gg, v.id(), x, y, GUI, 0xFFFFFF);
            String details = v.releaseTime().substring(0, 10); // the day it came out
            font.draw(gg, details, x + width - font.width(details, GUI), y, GUI, 0xA0A0A0);
        });

        // Use Version stays grayed out until a version is picked
        int buttonsY = h - 28 * GUI;
        useButton.setActive(list.getSelected() != null);
        useButton.setBounds(w / 2 - 100 * GUI, buttonsY, 98 * GUI, 20 * GUI);
        cancelButton.setBounds(w / 2 + 2 * GUI, buttonsY, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
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
