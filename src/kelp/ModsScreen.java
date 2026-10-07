package kelp;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;

/**
 * The mods installed in one instance, with a switch for each. Click a mod to turn it on or off.
 * New Mod makes a mod of your own, and Edit opens it again.
 */
public class ModsScreen extends Screen {
    private final Screen parent;
    private final Instance instance;
    private final McList<InstalledMod> list = new McList<>(220);
    private final McButton newButton = new McButton("New Mod", this::newMod);
    private final McButton openButton = new McButton("Open Folder", this::openFolder);
    private final McButton doneButton = new McButton("Done", this::back);

    private double listedAt = -1; // the folder is checked again every second, in case mods were added
    private String problem;
    private int mouseX = -1;
    private int mouseY = -1;
    private int editLeft; // where the "Edit" on the rows of .java mods is, to tell clicks on it apart
    private int editRight;

    public ModsScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        buttons.add(newButton);
        buttons.add(openButton);
        buttons.add(doneButton);
    }

    private void newMod() {
        panel.setScreen(new NewModScreen(panel, this, instance));
    }

    private void back() {
        panel.setScreen(parent);
    }

    private void openFolder() {
        try {
            Files.createDirectories(instance.mods());
            Desktop.getDesktop().open(instance.mods().toFile());
        } catch (IOException e) {
            problem = "Couldn't open the folder: " + e.getMessage();
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
        centered(g, "Mods for " + instance.name(), w, 12 * GUI, 0xFFFFFF);

        int listBottom = h - 84 * GUI;
        String empty = list.getItems().isEmpty() ? "No mods yet. Click New Mod!" : null;
        list.draw(g, font, w, 32 * GUI, listBottom, empty, (gg, mod, x, y, width) -> {
            String name = mod.version().isEmpty() ? mod.name() : mod.name() + " " + mod.version();
            font.draw(gg, name, x, y, GUI, mod.enabled() ? 0xFFFFFF : 0x808080);
            // On the right: ON or OFF, like a Minecraft options button
            String state = !mod.squidMod() ? "Not a Squid mod" : mod.enabled() ? "ON" : "OFF";
            int color = !mod.squidMod() ? 0xA0A0A0 : mod.enabled() ? 0x55FF55 : 0xFF5555;
            if (mod.squidMod() && mod.enabled() && !mod.worksOn(minecraftVersion())) {
                state = "Wrong version";
                color = 0xFFFF55;
            }
            font.draw(gg, state, x + width - font.width(state, GUI), y, GUI, color);
            if (mod.source()) {
                editRight = x + width - font.width("OFF", GUI) - 10 * GUI;
                editLeft = editRight - font.width("Edit", GUI);
                boolean over = mouseX >= editLeft && mouseX < editRight && list.itemAt(mouseX, mouseY) == mod;
                font.draw(gg, "Edit", editLeft, y, GUI, over ? 0xFFFFA0 : 0x55FFFF);
            }
        });

        // Under the list: who made the mod under the mouse and what it does, or a problem
        String info = problem;
        InstalledMod hovered = list.itemAt(mouseX, mouseY);
        if (info == null && hovered != null) {
            info = hovered.description();
            if (!hovered.authors().isEmpty()) {
                info = "By " + String.join(", ", hovered.authors()) + (info.isEmpty() ? "" : ". " + info);
            }
            if (hovered.squidMod() && !hovered.worksOn(minecraftVersion())) {
                info = "Made for Minecraft " + String.join(" or ", hovered.minecraft()) + ", so Squid will skip it on "
                        + minecraftVersion() + ".";
            }
        }
        if (info != null && !info.isEmpty()) {
            while (info.length() > 3 && font.width(info, GUI) > w - 8 * GUI) info = info.substring(0, info.length() - 4) + "...";
            centered(g, info, w, listBottom + 6 * GUI, problem != null ? 0xFF5555 : 0xA0A0A0);
        }

        if (!instance.squid()) {
            centered(g, "Squid is off for this instance, so these won't load.", w, listBottom + 18 * GUI, 0xFFFF55);
        } else if (RunningGames.isRunning(instance)) {
            centered(g, "Changes are used the next time the game starts.", w, listBottom + 18 * GUI, 0xFFFF55);
        }

        int buttonsY = h - 52 * GUI;
        newButton.setBounds(w / 2 - 100 * GUI, buttonsY, 98 * GUI, 20 * GUI);
        openButton.setBounds(w / 2 + 2 * GUI, buttonsY, 98 * GUI, 20 * GUI);
        doneButton.setBounds(w / 2 - 100 * GUI, buttonsY + 24 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
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
        if (!mod.squidMod()) {
            problem = "That jar has no squid.json, so Squid skips it either way.";
            return;
        }
        if (mod.source() && x >= editLeft && x < editRight) {
            try {
                Editors.open(mod.file());
                problem = null;
            } catch (IOException e) {
                problem = "Couldn't open it: " + e.getMessage();
            }
            return;
        }
        try {
            mod.toggle();
            listedAt = -1; // read the folder again now, to show the change
            problem = null;
        } catch (IOException e) {
            problem = "Couldn't switch it. Is the game still running? (" + e.getMessage() + ")";
        }
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        list.mouseWheel(notches);
    }
}
