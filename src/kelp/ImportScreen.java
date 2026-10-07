package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.nio.file.Path;

/** Brings a modpack in as a new instance (see {@link ModpackImport}), with its progress, then says how it went. */
public class ImportScreen extends Screen {
    private final Screen parent;
    private final Downloader downloader = new Downloader();
    private final McButton button = new McButton(t("Cancel"), this::leave);
    private volatile String stage = t("Reading the modpack...");
    private volatile String problem;
    private volatile ModpackImport.Result result;

    public ImportScreen(OceanPanel panel, Screen parent, Path pack) {
        super(panel);
        this.parent = parent;
        buttons.add(button);
        Thread worker = new Thread(() -> {
            try {
                result = ModpackImport.importPack(pack, downloader, text -> stage = text);
            } catch (Exception e) {
                if (!downloader.isCancelled()) problem = e.getMessage() != null ? e.getMessage() : e.toString();
            }
        }, "import modpack");
        worker.setDaemon(true);
        worker.start();
    }

    private void leave() {
        if (result == null && problem == null) downloader.cancel();
        panel.setScreen(parent);
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int y = h / 2 - 30 * GUI;
        if (problem != null) {
            centered(g, t("Couldn't import it:"), w, y, 0xFF5555);
            centered(g, problem, w, y + 14 * GUI, 0xFFFFFF);
            button.setLabel(t("Back"));
        } else if (result != null) {
            centered(g, t("Imported {0}!", result.instance().name()), w, y, 0x55FF55);
            if (result.missing().isEmpty()) {
                centered(g, t("It's in your instances, ready to play."), w, y + 14 * GUI, 0xA0A0A0);
            } else {
                centered(g, t("{0} mods couldn't be downloaded: their authors only allow it on CurseForge.", result.missing().size()), w,
                        y + 14 * GUI, 0xFFFF55);
                centered(g, t("Get them there and add them with Add Mod."), w, y + 26 * GUI, 0xFFFF55);
            }
            button.setLabel(t("Done"));
        } else {
            centered(g, stage, w, y, 0xFFFFFF);
            if (downloader.getFilesTotal() > 0) {
                centered(g, downloader.getFilesDone() + " / " + downloader.getFilesTotal(), w, y + 14 * GUI, 0xA0A0A0);
            }
            button.setLabel(t("Cancel"));
        }
        button.setBounds(w / 2 - 100 * GUI, h / 2 + 30 * GUI, 200 * GUI, 20 * GUI);
        button.draw(g, font, GUI);
    }
}
