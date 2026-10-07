package kelp;

import java.awt.Graphics2D;

/** Every account added to Kelp. Pick one to play as, add a new one, or remove one. */
public class AccountsScreen extends Screen {
    private final Screen parent;
    private final McList<Account> list = new McList<>(220);
    private final McButton useButton = new McButton("Use Account", this::use);
    private final McButton removeButton = new McButton("Remove", this::remove);
    private final McButton microsoftButton = new McButton("Add Microsoft", () -> panel.setScreen(new SignInScreen(panel, this)));
    private final McButton offlineButton = new McButton("Add Offline", this::addOffline);
    private final McButton doneButton = new McButton("Done", this::done);
    private String problem;

    public AccountsScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(useButton);
        buttons.add(removeButton);
        buttons.add(microsoftButton);
        buttons.add(offlineButton);
        buttons.add(doneButton);
    }

    @Override
    public void shown() {
        list.setItems(Accounts.all());
        list.setSelected(Accounts.active());
        problem = null;
    }

    private void done() {
        panel.setScreen(parent);
    }

    private void use() {
        Accounts.setActive(list.getSelected());
    }

    private void remove() {
        Account account = list.getSelected();
        String warning = account.microsoft() ? "You can sign in again any time." : "You can add the name again any time.";
        panel.setScreen(new ConfirmScreen(panel, "Remove " + account.name() + "?", warning, () -> {
            Accounts.remove(account);
            panel.setScreen(this);
        }, () -> panel.setScreen(this)));
    }

    private void addOffline() {
        // Offline names are for people who own Minecraft, so once sign-in works, one Microsoft account comes first
        if (MicrosoftLogin.ready() && !Accounts.hasMicrosoft()) {
            problem = "Add a Microsoft account that owns Minecraft first.";
            return;
        }
        panel.setScreen(new AddOfflineScreen(panel, this));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, "Accounts", w, 12 * GUI, 0xFFFFFF);

        Account active = Accounts.active();
        int listBottom = h - 92 * GUI;
        list.draw(g, font, w, 32 * GUI, listBottom, null, (gg, account, x, y, width) -> {
            boolean playing = account.id().equals(active.id());
            font.draw(gg, account.name(), x, y, GUI, playing ? 0x55FF55 : 0xFFFFFF);
            String kind = (playing ? "Playing - " : "") + (account.microsoft() ? "Microsoft" : "Offline");
            font.draw(gg, kind, x + width - font.width(kind, GUI), y, GUI, playing ? 0x55FF55 : 0xA0A0A0);
        });
        if (problem != null) {
            centered(g, problem, w, listBottom + 4 * GUI, 0xFF5555);
        } else if (!MicrosoftLogin.ready()) {
            centered(g, "Microsoft sign-in turns on once Mojang approves Kelp.", w, listBottom + 4 * GUI, 0x808080);
        }

        Account selected = list.getSelected();
        useButton.setActive(selected != null && !selected.id().equals(active.id()));
        removeButton.setActive(selected != null);
        int y = h - 76 * GUI;
        int left = w / 2 - 100 * GUI;
        int right = w / 2 + 2 * GUI;
        useButton.setBounds(left, y, 98 * GUI, 20 * GUI);
        removeButton.setBounds(right, y, 98 * GUI, 20 * GUI);
        microsoftButton.setBounds(left, y + 24 * GUI, 98 * GUI, 20 * GUI);
        offlineButton.setBounds(right, y + 24 * GUI, 98 * GUI, 20 * GUI);
        doneButton.setBounds(left, y + 48 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        list.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        problem = null;
        if (list.mousePressed(x, y) == null) super.mousePressed(x, y);
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        list.mouseWheel(notches);
    }
}
