package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.io.IOException;

/**
 * One instance's servers (the same list as Minecraft's Multiplayer screen). Join opens the game straight onto a
 * server; Add Server puts a friend's server in the list; Remove takes one out.
 */
public class ServersScreen extends Screen {
    private final Screen parent;
    private final Instance instance;
    private final McList<Servers.Server> list = new McList<>(220, 2); // two lines: the name, then the address
    private final McButton joinButton = new McButton(t("Join"), this::join);
    private final McButton addButton = new McButton(t("Add Server"), this::add);
    private final McButton removeButton = new McButton(t("Remove"), this::remove);
    private final McButton doneButton = new McButton(t("Done"), this::back);
    private String problem;
    private Boolean canJoin; // whether this version can join a server by itself (1.20 and newer), looked up once

    public ServersScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        for (McButton b : new McButton[] {joinButton, addButton, removeButton, doneButton}) buttons.add(b);
    }

    private void back() {
        panel.setScreen(parent);
    }

    @Override
    public void shown() {
        list.setItems(Servers.list(Servers.file(instance)));
    }

    /** Starts the game straight onto the picked server. */
    private void join() {
        Servers.Server server = list.getSelected();
        if (server == null || !joinable()) return;
        if (!Servers.validAddress(server.address())) {
            problem = t("Kelp can't join that address by itself. Join it from Minecraft's Multiplayer screen.");
            return;
        }
        panel.setScreen(new DownloadScreen(panel, this, instance, null, server.address()));
    }

    private boolean joinable() {
        if (canJoin == null) canJoin = Launcher.canOpenWorld(instance.version().id());
        return canJoin && ParentControls.multiplayerAllowed() && !RunningGames.isRunning(instance);
    }

    private void add() {
        if (RunningGames.isRunning(instance)) return;
        panel.setScreen(new AddServerScreen(panel, this, instance));
    }

    private void remove() {
        Servers.Server server = list.getSelected();
        if (server == null || RunningGames.isRunning(instance)) return;
        int index = list.getItems().indexOf(server);
        panel.setScreen(new ConfirmScreen(panel, t("Remove {0}?", server.name()), t("It's taken out of this instance's server list."), () -> {
            try {
                Servers.remove(Servers.file(instance), index);
                problem = null;
            } catch (IOException e) {
                problem = t("Couldn't remove it: {0}", e.getMessage());
            }
            panel.setScreen(this);
        }, () -> panel.setScreen(this)));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Servers in {0}", instance.name()), w, 12 * GUI, 0xFFFFFF);
        int listBottom = h - 72 * GUI;
        String empty = list.getItems().isEmpty() ? t("No servers yet. Add a friend's server!") : null;
        list.draw(g, font, w, 32 * GUI, listBottom, empty, (gg, server, x, y, width) -> {
            font.draw(gg, server.name(), x, y, GUI, 0xFFFFFF);
            font.draw(gg, server.address(), x, y + 10 * GUI, GUI, 0x808080);
        });
        boolean running = RunningGames.isRunning(instance);
        String note = problem != null ? problem
                : !ParentControls.multiplayerAllowed() ? t("A parent turned multiplayer off in Parent Controls.")
                : running ? t("Close Minecraft to change the list (it keeps its own copy while it's open).") : null;
        if (note != null) centered(g, note, w, listBottom + 6 * GUI, problem != null ? 0xFF5555 : 0xFFFF55);

        boolean picked = list.getSelected() != null;
        joinButton.setActive(picked && joinable());
        addButton.setActive(!running);
        removeButton.setActive(picked && !running);
        int y = h - 52 * GUI;
        joinButton.setBounds(w / 2 - 100 * GUI, y, 98 * GUI, 20 * GUI);
        addButton.setBounds(w / 2 + 2 * GUI, y, 98 * GUI, 20 * GUI);
        removeButton.setBounds(w / 2 - 100 * GUI, y + 24 * GUI, 98 * GUI, 20 * GUI);
        doneButton.setBounds(w / 2 + 2 * GUI, y + 24 * GUI, 98 * GUI, 20 * GUI);
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
