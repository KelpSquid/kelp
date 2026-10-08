package kelp;

import static kelp.Lang.t;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The mods installed in one instance, with a switch for each. Click a mod to turn it on or off.
 * New Mod makes a mod of your own, and Edit opens it again.
 */
public class ModsScreen extends Screen {
    private final Screen parent;
    private final Instance instance;
    private final McList<InstalledMod> list = new McList<>(220);
    private final McButton newButton = new McButton(t("New Mod"), this::newMod);
    private final McButton openButton = new McButton(t("Open Folder"), this::openFolder);
    private final McButton shaderButton = new McButton(t("Shader Packs"), this::openShaderPacks);
    private final McButton addButton = new McButton(t("Add Mod..."), this::pickMods);
    private final McButton doneButton = new McButton(t("Done"), this::back);

    private double listedAt = -1; // the folder is checked again every second, in case mods were added
    private String problem;
    private String notice; // good news, like "Added X!"; problems take its place
    private boolean wrongLoader; // the notice is a heads-up (yellow), not good news (green)
    private int mouseX = -1;
    private int mouseY = -1;
    private int editLeft; // where the "Edit" on the rows of your own mods is, to tell clicks on it apart
    private int editRight;
    private int packLeft; // and "Pack", on the rows of projects
    private int packRight;

    public ModsScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        buttons.add(newButton);
        buttons.add(openButton);
        buttons.add(shaderButton);
        buttons.add(addButton);
        buttons.add(doneButton);
    }

    private void newMod() {
        panel.setScreen(new NewModScreen(panel, this, instance));
    }

    private void back() {
        panel.setScreen(parent);
    }

    /** Packs a project into one .squid file in Downloads, and shows it there. */
    private void pack(InstalledMod mod) {
        try {
            Path file = ModProject.pack(mod.file(), Path.of(System.getProperty("user.home"), "Downloads"));
            notice = t("Packed {0} into Downloads! Share it, or send it to the Store.", file.getFileName());
            wrongLoader = false;
            problem = null;
            show(file);
        } catch (IOException e) {
            problem = t("Couldn't pack it: {0}", e.getMessage());
        }
    }

    /** Opens the file's folder with the file picked, so it's easy to find. */
    private static void show(Path file) {
        try {
            if (Rules.osName().equals("windows")) new ProcessBuilder("explorer.exe", "/select," + file).start();
            else Desktop.getDesktop().open(file.getParent().toFile());
        } catch (IOException | RuntimeException e) {
            System.err.println("Couldn't show " + file + ": " + e.getMessage()); // the notice still says where it is
        }
    }

    private void openFolder() {
        try {
            Files.createDirectories(instance.mods());
            Desktop.getDesktop().open(instance.mods().toFile());
        } catch (IOException e) {
            problem = t("Couldn't open the folder: {0}", e.getMessage());
        }
    }

    /** Shader packs go in their own folder, next to mods. Iris lists them in Options > Video Settings > Shader Packs. */
    private void openShaderPacks() {
        try {
            Path folder = instance.folder().resolve("shaderpacks");
            Files.createDirectories(folder);
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException e) {
            problem = t("Couldn't open the folder: {0}", e.getMessage());
        }
    }

    /** Asks which mod files to add, with the computer's own file window. */
    private void pickMods() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(downloads());
        chooser.setDialogTitle(t("Pick mods to add"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(t("Mods (.jar, .squid, .java)"), "jar", "squid", "java"));
        if (chooser.showDialog(javax.swing.SwingUtilities.getWindowAncestor(panel), t("Add")) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        filesDropped(java.util.Arrays.stream(chooser.getSelectedFiles()).map(java.io.File::toPath).toList());
    }

    /** The Downloads folder, where most mods end up, if there is one. */
    private static java.io.File downloads() {
        java.io.File folder = new java.io.File(System.getProperty("user.home"), "Downloads");
        return folder.isDirectory() ? folder : null;
    }

    /**
     * Mods dropped onto the window (or picked with Add Mod) are copied into this instance's mods folder, so the
     * originals can be deleted from Downloads. A mod with the same file name is replaced, like an update, and so is
     * an older download of the same Squid mod under another name ("MegaMod (1).squid"). Your own projects and
     * .java mods are never replaced: Kelp says there are two copies instead.
     */
    @Override
    public void filesDropped(java.util.List<Path> files) {
        java.util.List<String> added = new java.util.ArrayList<>();
        String twoCopies = null;
        wrongLoader = false;
        try {
            Files.createDirectories(instance.mods());
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".jar") && !name.endsWith(".squid") && !name.endsWith(".java")) {
                    problem = t("{0} isn't a mod. Mods are .jar, .squid or .java files.", name);
                    continue;
                }
                String id = InstalledMod.squidId(file);
                String sameAsYours = null;
                if (id != null) {
                    try (java.util.stream.Stream<Path> old = Files.list(instance.mods())) {
                        for (Path other : old.toList()) {
                            String otherName = other.getFileName().toString();
                            if (otherName.equals(name) || !id.equals(InstalledMod.squidId(other))) continue;
                            boolean packed = otherName.matches(".*\\.(jar|squid)(\\.disabled)?");
                            if (packed) Files.delete(other); // an older download of the same mod
                            else sameAsYours = otherName;
                        }
                    }
                }
                Files.copy(file, instance.mods().resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                added.add(name);
                if (sameAsYours != null) twoCopies = t("{0} is the same mod as your {1}, so Squid will only load one of them.", name, sameAsYours);
            }
        } catch (IOException e) {
            problem = t("Couldn't add it: {0}", e.getMessage());
        }
        listedAt = -1; // show them right away
        if (!added.isEmpty()) {
            problem = null;
            notice = added.size() == 1 ? t("Added {0}!", added.get(0)) : t("Added {0} mods!", added.size());
            if (twoCopies != null) notice = twoCopies;
            // A mod for another loader still goes in, but say so, so nobody wonders why it doesn't load
            for (String name : added) {
                InstalledMod mod = InstalledMod.list(instance.mods()).stream()
                        .filter(m -> m.file().getFileName().toString().equals(name)).findFirst().orElse(null);
                if (mod != null && mod.kind() != null && !instance.loader().runs(mod.kind())) {
                    notice = t("{0} is a {1} mod, so it won't load in this {2} instance.", name, mod.kind().label(), instance.loader().label());
                    wrongLoader = true;
                }
            }
        }
    }

    private void refresh() {
        double now = panel.getTime();
        if (listedAt < 0 || now - listedAt >= 1) {
            list.setItems(InstalledMod.list(instance.mods()));
            listedAt = now;
        }
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        refresh();
        centered(g, t("Mods for {0}", instance.name()), w, 12 * GUI, 0xFFFFFF);

        int listBottom = h - 84 * GUI;
        // New Mod makes Squid mods, so only Squid (and Vanilla) instances point people to it
        boolean canMakeMods = instance.loader() == Loader.SQUID || instance.loader() == Loader.VANILLA;
        String empty = !list.getItems().isEmpty() ? null : canMakeMods ? t("No mods yet. Click New Mod!") : t("No mods yet. Click Open Folder!");
        list.draw(g, font, w, 32 * GUI, listBottom, empty, (gg, mod, x, y, width) -> {
            // On the right: ON or OFF, like a Minecraft options button. Mods for another loader say which one.
            String state = mod.enabled() ? t("ON") : t("OFF");
            int color = mod.enabled() ? 0x55FF55 : 0xFF5555;
            if (mod.kind() == null) {
                state = t("Not a mod");
                color = 0xA0A0A0;
            } else if (!instance.loader().runs(mod.kind())) {
                state = t("For {0}", mod.kind().label());
                color = 0xA0A0A0;
            }
            if (mod.squidMod() && mod.enabled() && !mod.worksOn(minecraftVersion())) {
                state = t("Wrong version");
                color = 0xFFFF55;
            }
            font.draw(gg, state, x + width - font.width(state, GUI), y, GUI, color);
            if (mod.source()) {
                editRight = x + width - Math.max(font.width(t("OFF"), GUI), font.width(t("ON"), GUI)) - 10 * GUI;
                editLeft = editRight - font.width(t("Edit"), GUI);
                boolean over = mouseX >= editLeft && mouseX < editRight && list.itemAt(mouseX, mouseY) == mod;
                font.draw(gg, t("Edit"), editLeft, y, GUI, over ? 0xFFFFA0 : 0x55FFFF);
            }
            if (mod.project()) {
                packRight = editLeft - 8 * GUI;
                packLeft = packRight - font.width(t("Pack"), GUI);
                boolean over = mouseX >= packLeft && mouseX < packRight && list.itemAt(mouseX, mouseY) == mod;
                font.draw(gg, t("Pack"), packLeft, y, GUI, over ? 0xFFFFA0 : 0x55FFFF);
            }
            // Its icon, in front of the name (pixel art stays sharp)
            java.awt.image.BufferedImage icon = ModIcons.of(mod);
            if (icon != null) {
                Object hint = gg.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
                gg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                gg.drawImage(icon, x, y - GUI, 9 * GUI, 9 * GUI, null);
                if (hint != null) gg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, hint);
            }
            int nameX = x + 12 * GUI;
            // The name gets what's left of the row, and a long one ends in "..." instead of running under the links
            int room = (mod.project() ? packLeft : mod.source() ? editLeft : x + width - font.width(state, GUI)) - nameX - 6 * GUI;
            String name = mod.version().isEmpty() ? mod.name() : mod.name() + " " + mod.version();
            if (font.width(name, GUI) > room) {
                while (name.length() > 1 && font.width(name.stripTrailing() + "...", GUI) > room) name = name.substring(0, name.length() - 1);
                name = name.stripTrailing() + "...";
            }
            font.draw(gg, name, nameX, y, GUI, mod.enabled() ? 0xFFFFFF : 0x808080);
        });

        // Under the list: who made the mod under the mouse and what it does, or a problem
        String info = problem;
        InstalledMod hovered = list.itemAt(mouseX, mouseY);
        if (info == null && hovered != null) {
            info = hovered.description();
            if (!hovered.authors().isEmpty()) {
                info = t("By {0}", String.join(", ", hovered.authors())) + (info.isEmpty() ? "" : ". " + info);
            }
            if (hovered.kind() != null && !instance.loader().runs(hovered.kind())) {
                info = t("This is a {0} mod, so it only loads in a {0} instance.", hovered.kind().label());
            } else if (hovered.squidMod() && !hovered.worksOn(minecraftVersion())) {
                info = t("Made for Minecraft {0}, so Squid will skip it on {1}.", String.join(" / ", hovered.minecraft()), minecraftVersion());
            }
        }
        boolean good = false;
        if ((info == null || info.isEmpty()) && notice != null) {
            info = notice;
            good = !wrongLoader;
        }
        if (info != null && !info.isEmpty()) {
            while (info.length() > 3 && font.width(info, GUI) > w - 8 * GUI) info = info.substring(0, info.length() - 4) + "...";
            int color = problem != null ? 0xFF5555 : info == notice ? (good ? 0x55FF55 : 0xFFFF55) : 0xA0A0A0;
            centered(g, info, w, listBottom + 6 * GUI, color);
        }

        if (instance.loader() == Loader.VANILLA) {
            centered(g, t("Vanilla doesn't load mods. Pick Squid in Instances."), w, listBottom + 18 * GUI, 0xFFFF55);
        } else if (RunningGames.isRunning(instance)) {
            centered(g, t("Changes are used the next time the game starts."), w, listBottom + 18 * GUI, 0xFFFF55);
        }

        // Your own mods are Squid mods, so New Mod is for Squid instances (and Vanilla ones, which it switches to Squid).
        // A Shaders instance shows a Shader Packs button in its place.
        boolean shaders = instance.loader() == Loader.SHADERS;
        McButton left = shaders ? shaderButton : newButton;
        (shaders ? newButton : shaderButton).setBounds(-1000, -1000, 0, 0); // out of sight, so it can't be clicked
        newButton.setActive(instance.loader() == Loader.SQUID || instance.loader() == Loader.VANILLA);
        int buttonsY = h - 52 * GUI;
        left.setBounds(w / 2 - 100 * GUI, buttonsY, 98 * GUI, 20 * GUI);
        addButton.setBounds(w / 2 + 2 * GUI, buttonsY, 98 * GUI, 20 * GUI);
        openButton.setBounds(w / 2 - 100 * GUI, buttonsY + 24 * GUI, 98 * GUI, 20 * GUI);
        doneButton.setBounds(w / 2 + 2 * GUI, buttonsY + 24 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) {
            if (b != (shaders ? newButton : shaderButton)) b.draw(g, font, GUI);
        }
    }

    private String minecraftVersion() {
        return instance.version().id();
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        list.mouseMoved(x, y);
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        InstalledMod mod = list.itemAt(x, y);
        if (mod == null) {
            super.mousePressed(x, y);
            return;
        }
        if (mod.kind() == null) {
            problem = t("Kelp can't tell what this jar is, so no loader will load it.");
            return;
        }
        if (mod.project() && x >= packLeft && x < packRight) {
            pack(mod);
            return;
        }
        if (mod.source() && x >= editLeft && x < editRight) {
            try {
                Editors.open(mod.file());
                problem = null;
            } catch (IOException e) {
                problem = t("Couldn't open it: {0}", e.getMessage());
            }
            return;
        }
        try {
            mod.toggle();
            listedAt = -1; // read the folder again now, to show the change
            problem = null;
        } catch (IOException e) {
            problem = t("Couldn't switch it. Is the game still running? ({0})", e.getMessage());
        }
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        list.mouseWheel(notches);
    }
}
