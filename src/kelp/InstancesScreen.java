package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Path;

/** Every instance, with buttons to play, make, change and delete them. */
public class InstancesScreen extends Screen {
    private final Screen parent;
    private final McList<Instance> list = new McList<>(220, 2); // the name, with its version and loader under it
    private final McButton playButton = new McButton(t("Play"), this::play);
    private final McButton newButton = new McButton(t("New Instance"), () -> panel.setScreen(new NewInstanceScreen(panel, this)));
    private final McButton loaderButton = new McButton("", this::nextLoader);
    private final McButton modsButton = new McButton(t("Mods"), this::openMods);
    private final McButton optionsButton = new McButton(t("Game Options"), this::openOptions);
    private final McButton deleteButton = new McButton(t("Delete"), this::delete);
    private final McButton defaultButton = new McButton("", this::toggleDefault);
    private final McButton worldsButton = new McButton(t("Worlds"), this::openWorlds);
    private final McButton statsButton = new McButton(t("Stats"), () -> panel.setScreen(new StatsScreen(panel, this, list.getSelected())));
    private final McButton galleryButton = new McButton(t("Gallery"), () -> panel.setScreen(new GalleryScreen(panel, this, list.getSelected())));
    private final McButton backButton = new McButton(t("Back"), this::back);
    private final McButton shareButton = new McButton(t("Share"), this::share);
    private String notice; // good news, like "Saved X to Downloads!"
    /** How many mods in each instance Mod Doctor would fix, looked at again every few seconds. */
    private final java.util.Map<String, Integer> toFix = new java.util.HashMap<>();
    private double checkedAt = -1;
    private String problem;

    public InstancesScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(playButton);
        buttons.add(newButton);
        buttons.add(loaderButton);
        buttons.add(modsButton);
        buttons.add(optionsButton);
        buttons.add(deleteButton);
        buttons.add(defaultButton);
        buttons.add(worldsButton);
        buttons.add(statsButton);
        buttons.add(galleryButton);
        buttons.add(backButton);
        buttons.add(shareButton);
    }

    private void back() {
        panel.setScreen(parent);
    }

    /** Selects an instance, like a new one that was just made. */
    public void select(Instance instance) {
        list.setSelected(instance);
    }

    @Override
    public void shown() {
        Instance selected = list.getSelected();
        list.setItems(Instance.all()); // they might have changed while another screen was open
        if (selected != null && list.getItems().contains(selected)) list.setSelected(selected);
        else if (list.getSelected() == null) list.setSelected(Instance.find(Settings.lastInstance()));
    }

    private void play() {
        panel.setScreen(new DownloadScreen(panel, this, list.getSelected()));
    }

    private void nextLoader() {
        Instance instance = list.getSelected();
        if (RunningGames.isRunning(instance)) {
            problem = t("Close the game first, then change its loader.");
            return;
        }
        try {
            instance.setLoader(instance.loader().nextReady());
        } catch (IOException e) {
            problem = t("Couldn't save that: {0}", e.getMessage());
        }
    }

    /** Makes the picked instance the one the title screen's Play button starts, or stops it being that. */
    private void toggleDefault() {
        Instance instance = list.getSelected();
        Settings.setDefaultInstance(instance.isDefault() ? null : instance.id());
    }

    private void openWorlds() {
        panel.setScreen(new WorldsScreen(panel, this, list.getSelected()));
    }

    private void openMods() {
        panel.setScreen(new ModsScreen(panel, this, list.getSelected()));
    }

    private void openOptions() {
        panel.setScreen(new GameOptionsScreen(panel, this, list.getSelected()));
    }

    /** Packs the instance into a .mrpack in Downloads, for a friend to bring in with Import. Worlds stay home. */
    private void share() {
        Instance instance = list.getSelected();
        try {
            Path file = ModpackExport.export(instance, Path.of(System.getProperty("user.home"), "Downloads"), false);
            notice = t("Saved {0} to Downloads! A friend can bring it in with Import.", file.getFileName());
            problem = null;
        } catch (IOException e) {
            problem = t("Couldn't share it: {0}", e.getMessage());
            notice = null;
        }
    }

    private void delete() {
        Instance instance = list.getSelected();
        if (RunningGames.isRunning(instance)) {
            problem = t("Close the game first, then delete it.");
            return;
        }
        panel.setScreen(new ConfirmScreen(panel, t("Delete {0}?", instance.name()),
                t("Its worlds, settings and mods will be gone forever!"), () -> {
            try {
                if (instance.isDefault()) Settings.setDefaultInstance(null);
                instance.delete();
                problem = null;
            } catch (IOException e) {
                problem = t("Couldn't delete all of it. Is the game still running?");
            }
            panel.setScreen(this);
        }, () -> panel.setScreen(this)));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Instances"), w, 12 * GUI, 0xFFFFFF);

        int listBottom = h - 156 * GUI;
        String empty = list.getItems().isEmpty() ? t("No instances yet. Click New Instance!") : null;
        if (checkedAt < 0 || panel.getTime() - checkedAt >= 3) {
            toFix.clear();
            for (Instance instance : list.getItems()) {
                if (instance.loader() != Loader.SQUID) continue; // Mod Doctor knows Squid mods
                int count = ModDoctor.check(InstalledMod.list(instance.mods())).size();
                if (count > 0) toFix.put(instance.id(), count);
            }
            checkedAt = panel.getTime();
        }
        list.draw(g, font, w, 32 * GUI, listBottom, empty, (gg, instance, x, y, width) -> {
            // Like Minecraft's world list: the name, and under it in grey, its version and loader.
            // The default instance is yellow, with a star, like it's been picked out.
            boolean isDefault = instance.isDefault();
            String name = fit(font, isDefault ? "* " + instance.name() : instance.name(), width);
            font.draw(gg, name, x, y, GUI, isDefault ? 0xFFFF55 : 0xFFFFFF);
            String details = t("Minecraft {0}", instance.version().id()) + instance.loader().suffix() + (instance.loaderIsBeta() ? " " + t("beta") : "");
            // Mods that Mod Doctor can help with: a yellow note on the right, so it's noticed before playing
            Integer count = toFix.get(instance.id());
            String fix = count == null ? null : count == 1 ? t("1 mod to fix") : t("{0} mods to fix", count);
            int room = fix == null ? width : width - font.width(fix, GUI) - 6 * GUI;
            font.draw(gg, fit(font, details, room), x, y + 10 * GUI, GUI, 0x808080);
            if (fix != null) font.draw(gg, fix, x + width - font.width(fix, GUI), y + 10 * GUI, GUI, 0xFFFF55);
        });
        if (problem != null) centered(g, problem, w, listBottom + 2 * GUI, 0xFF5555);
        else if (notice != null) centered(g, notice, w, listBottom + 2 * GUI, 0x55FF55);

        // Buttons: everything but New Instance and Back is for one instance, so they only show once one is picked
        Instance selected = list.getSelected();
        McButton[] forOne = {playButton, loaderButton, modsButton, optionsButton, deleteButton, defaultButton, worldsButton, statsButton, galleryButton, shareButton};
        for (McButton b : forOne) b.setActive(selected != null);
        if (selected == null) centered(g, t("Click an instance to play it or change it."), w, h - 112 * GUI, 0xA0A0A0);
        loaderButton.setLabel(selected == null ? t("Loader") : selected.loader().label());
        defaultButton.setLabel(selected != null && selected.isDefault() ? t("Not Default") : t("Make Default"));
        int y = h - 148 * GUI;
        int left = w / 2 - 100 * GUI;
        int right = w / 2 + 2 * GUI;
        playButton.setBounds(left, y, 98 * GUI, 20 * GUI);
        newButton.setBounds(right, y, 98 * GUI, 20 * GUI);
        loaderButton.setBounds(left, y + 24 * GUI, 98 * GUI, 20 * GUI);
        modsButton.setBounds(right, y + 24 * GUI, 98 * GUI, 20 * GUI);
        optionsButton.setBounds(left, y + 48 * GUI, 98 * GUI, 20 * GUI);
        deleteButton.setBounds(right, y + 48 * GUI, 98 * GUI, 20 * GUI);
        defaultButton.setBounds(left, y + 72 * GUI, 98 * GUI, 20 * GUI);
        worldsButton.setBounds(right, y + 72 * GUI, 98 * GUI, 20 * GUI);
        statsButton.setBounds(left, y + 96 * GUI, 98 * GUI, 20 * GUI);
        galleryButton.setBounds(right, y + 96 * GUI, 98 * GUI, 20 * GUI);
        shareButton.setBounds(left, y + 120 * GUI, 98 * GUI, 20 * GUI);
        backButton.setBounds(selected == null ? left : right, y + 120 * GUI, selected == null ? 200 * GUI : 98 * GUI, 20 * GUI);
        if (selected == null) newButton.setBounds(left, y + 96 * GUI, 200 * GUI, 20 * GUI); // on its own, above Back
        for (McButton b : buttons) {
            if (selected != null || !java.util.Arrays.asList(forOne).contains(b)) b.draw(g, font, GUI);
        }
    }

    /** Cuts text down with "..." until it fits. */
    private static String fit(McFont font, String text, int maxWidth) {
        if (font.width(text, GUI) <= maxWidth) return text;
        while (text.length() > 1 && font.width(text + "...", GUI) > maxWidth) text = text.substring(0, text.length() - 1);
        return text + "...";
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
