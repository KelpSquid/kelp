package kelp;

import static kelp.Lang.t;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** One instance's worlds, with a button to bring in a world from somewhere else (a folder or a .zip). */
public class WorldsScreen extends Screen {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final Instance instance;
    private final McList<Worlds.World> list = new McList<>(220);
    private final McButton importButton = new McButton(t("Import World"), this::pickWorld);
    private final McButton openButton = new McButton(t("Open Folder"), this::openFolder);
    private final McButton doneButton = new McButton(t("Done"), this::back);
    private volatile String status;
    private volatile int statusColor = 0xA0A0A0;
    private volatile boolean importing;

    public WorldsScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        buttons.add(importButton);
        buttons.add(openButton);
        buttons.add(doneButton);
    }

    @Override
    public void shown() {
        list.setItems(Worlds.list(Worlds.saves(instance)));
    }

    private void back() {
        panel.setScreen(parent);
    }

    private void openFolder() {
        try {
            Files.createDirectories(Worlds.saves(instance));
            Desktop.getDesktop().open(Worlds.saves(instance).toFile());
        } catch (IOException e) {
            show(t("Couldn't open the folder: {0}", e.getMessage()), 0xFF5555);
        }
    }

    /** Asks which world to bring in. It starts in the official launcher's saves folder, where most worlds are. */
    private void pickWorld() {
        if (importing) return;
        Path start = officialSaves();
        JFileChooser chooser = new JFileChooser(start != null ? start.toFile() : null);
        chooser.setDialogTitle(t("Pick a world folder, or a world .zip"));
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new FileNameExtensionFilter(t("World folders and .zip files"), "zip"));
        if (chooser.showDialog(SwingUtilities.getWindowAncestor(panel), t("Import")) != JFileChooser.APPROVE_OPTION) return;
        File picked = chooser.getSelectedFile();
        importWorld(picked.toPath());
    }

    /** Copies the world in, in the background so the window stays smooth for big worlds. */
    void importWorld(Path source) {
        importing = true;
        show(t("Importing {0}...", source.getFileName()), 0xA0A0A0);
        Thread worker = new Thread(() -> {
            try {
                Path world = Worlds.importWorld(source, Worlds.saves(instance));
                show(t("Imported {0}! It's in the world list when you play.", world.getFileName()), 0x55FF55);
                SwingUtilities.invokeLater(this::shown);
            } catch (IOException e) {
                show(e.getMessage(), 0xFF5555);
            } finally {
                importing = false;
            }
        }, "import world");
        worker.setDaemon(true);
        worker.start();
    }

    /** Worlds (folders or .zips) dropped onto the window are imported, one after another. */
    @Override
    public void filesDropped(java.util.List<Path> files) {
        if (!files.isEmpty() && !importing) importWorld(files.get(0));
    }

    private void show(String text, int color) {
        status = text;
        statusColor = color;
    }

    /** %APPDATA%\.minecraft\saves (or the Mac and Linux places), if it's there. */
    private static Path officialSaves() {
        String os = Rules.osName();
        Path minecraft;
        if (os.equals("windows")) {
            String appData = System.getenv("APPDATA");
            minecraft = appData == null ? null : Path.of(appData, ".minecraft");
        } else if (os.equals("osx")) {
            minecraft = Path.of(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        } else {
            minecraft = Path.of(System.getProperty("user.home"), ".minecraft");
        }
        Path saves = minecraft == null ? null : minecraft.resolve("saves");
        return saves != null && Files.isDirectory(saves) ? saves : null;
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Worlds in {0}", instance.name()), w, 12 * GUI, 0xFFFFFF);

        int listBottom = h - 72 * GUI;
        String empty = list.getItems().isEmpty() ? t("No worlds yet. Play, or Import World!") : null;
        list.draw(g, font, w, 32 * GUI, listBottom, empty, (gg, world, x, y, width) -> {
            font.draw(gg, world.name(), x, y, GUI, 0xFFFFFF);
            String when = WHEN.format(Instant.ofEpochMilli(world.lastPlayed()));
            font.draw(gg, when, x + width - font.width(when, GUI), y, GUI, 0xA0A0A0);
        });
        if (status != null) {
            String text = status;
            while (text.length() > 4 && font.width(text, GUI) > w - 8 * GUI) text = text.substring(0, text.length() - 4) + "...";
            centered(g, text, w, listBottom + 6 * GUI, statusColor);
        } else if (RunningGames.isRunning(instance)) {
            centered(g, t("Imported worlds show up after you leave to the title screen."), w, listBottom + 6 * GUI, 0xFFFF55);
        }

        importButton.setActive(!importing);
        int y = h - 52 * GUI;
        importButton.setBounds(w / 2 - 100 * GUI, y, 98 * GUI, 20 * GUI);
        openButton.setBounds(w / 2 + 2 * GUI, y, 98 * GUI, 20 * GUI);
        doneButton.setBounds(w / 2 - 100 * GUI, y + 24 * GUI, 200 * GUI, 20 * GUI);
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
