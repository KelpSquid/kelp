package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;

/** Every account added to Kelp. Pick one to play as, add a new one, or remove one. */
public class AccountsScreen extends Screen {
    private final Screen parent;
    private final McList<Account> list = new McList<>(220);
    private final McButton useButton = new McButton(t("Use Account"), this::use);
    private final McButton removeButton = new McButton(t("Remove"), this::remove);
    private final McButton microsoftButton = new McButton(t("Add Microsoft"), () -> panel.setScreen(new SignInScreen(panel, this)));
    private final McButton offlineButton = new McButton(t("Add Offline"), this::addOffline);
    private final McButton doneButton = new McButton(t("Done"), this::done);
    private final McButton deleteDataButton = new McButton(t("Delete Data..."), this::deleteData);
    private String problem;

    public AccountsScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(useButton);
        buttons.add(removeButton);
        buttons.add(microsoftButton);
        buttons.add(offlineButton);
        buttons.add(doneButton);
        buttons.add(deleteDataButton);
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
        String warning = account.microsoft() ? t("You can sign in again any time.") : t("You can add the name again any time.");
        panel.setScreen(new ConfirmScreen(panel, t("Remove {0}?", account.name()), warning, () -> {
            Accounts.remove(account);
            panel.setScreen(this);
        }, () -> panel.setScreen(this)));
    }

    /** Deletes everything Kelp and Squid keep about a player on this computer, after asking. */
    private void deleteData() {
        Account account = list.getSelected();
        if (account == null) return;
        panel.setScreen(new ConfirmScreen(panel, t("Delete all of {0}'s data?", account.name()),
                t("Their sign-in, emblem, Squid Count and skin and cape choices. Worlds and mods stay. This can't be undone!"), () -> {
            String result;
            try {
                PlayerData.delete(account);
                result = t("Deleted {0}'s data.", account.name());
            } catch (java.io.IOException e) {
                result = t("Couldn't delete it: {0}", e.getMessage());
            }
            panel.setScreen(this); // showing the screen again refreshes the list
            problem = result;
        }, () -> panel.setScreen(this)));
    }

    private void addOffline() {
        // Offline names are for people who own Minecraft, so once sign-in works, one Microsoft account comes first
        if (MicrosoftLogin.ready() && !Accounts.hasMicrosoft()) {
            problem = t("Add a Microsoft account that owns Minecraft first.");
            return;
        }
        panel.setScreen(new AddOfflineScreen(panel, this));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Accounts"), w, 12 * GUI, 0xFFFFFF);

        Account active = Accounts.active();
        int listBottom = h - 92 * GUI;
        list.draw(g, font, w, 32 * GUI, listBottom, null, (gg, account, x, y, width) -> {
            boolean playing = account.id().equals(active.id());
            EmblemScreen.drawSmall(gg, account.id(), x, y - GUI, 9 * GUI);
            font.draw(gg, account.name(), x + 12 * GUI, y, GUI, playing ? 0x55FF55 : 0xFFFFFF);
            Badges.draw(gg, account.id(), x + 16 * GUI + font.width(account.name(), GUI), y - GUI, 9 * GUI); // badges after the name
            String kind = (playing ? t("Playing") + " - " : "") + (account.microsoft() ? "Microsoft" : t("Offline"));
            font.draw(gg, kind, x + width - font.width(kind, GUI), y, GUI, playing ? 0x55FF55 : 0xA0A0A0);
        });
        if (problem != null) {
            centered(g, problem, w, listBottom + 4 * GUI, 0xFF5555);
        } else if (!MicrosoftLogin.ready()) {
            centered(g, t("Microsoft sign-in turns on once Mojang approves Kelp."), w, listBottom + 4 * GUI, 0x808080);
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
        deleteDataButton.setActive(selected != null);
        deleteDataButton.setBounds(left, y + 48 * GUI, 98 * GUI, 20 * GUI);
        doneButton.setBounds(right, y + 48 * GUI, 98 * GUI, 20 * GUI);
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
