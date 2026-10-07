package kelp;

import java.awt.Graphics2D;

/** Adds an offline name. It only shows up in single player and on LAN, since there's no Microsoft sign-in behind it. */
public class AddOfflineScreen extends Screen {
    private final Screen parent;
    private final McTextField nameField = new McTextField(16);
    private final McButton addButton = new McButton("Add", this::add);
    private final McButton cancelButton = new McButton("Cancel", this::back);

    public AddOfflineScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(addButton);
        buttons.add(cancelButton);
        nameField.setFocused(true);
    }

    private void back() {
        panel.setScreen(parent);
    }

    private void add() {
        if (!Settings.isValidName(nameField.getText())) return;
        Accounts.add(Account.offline(nameField.getText()));
        back();
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int left = w / 2 - 100 * GUI;
        int top = h / 4;
        centered(g, "Add Offline", w, 12 * GUI, 0xFFFFFF);

        font.draw(g, "Player name", left, top, GUI, 0xA0A0A0);
        nameField.setBounds(left, top + 11 * GUI, 200 * GUI, 20 * GUI);
        nameField.draw(g, font, GUI, panel.getTime());
        boolean valid = Settings.isValidName(nameField.getText());
        if (valid || nameField.getText().isEmpty()) {
            centered(g, "Offline names show in single player and LAN.", w, top + 35 * GUI, 0x808080);
        } else {
            centered(g, "Use 3-16 letters, numbers or _", w, top + 35 * GUI, 0xFF5555);
        }

        addButton.setActive(valid);
        addButton.setBounds(left, h - 28 * GUI, 98 * GUI, 20 * GUI);
        cancelButton.setBounds(w / 2 + 2 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mousePressed(int x, int y) {
        nameField.setFocused(nameField.contains(x, y));
        super.mousePressed(x, y);
    }

    @Override
    public void keyTyped(char c) {
        if (c == '\n') add(); // Enter adds it
        else nameField.keyTyped(c);
    }

    @Override
    public void keyPressed(int keyCode, boolean ctrl) {
        nameField.keyPressed(keyCode, ctrl);
    }
}
