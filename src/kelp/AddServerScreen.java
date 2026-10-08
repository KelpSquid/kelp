package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.io.IOException;

/** Adds a server to an instance's list: a name to show, and its address. */
public class AddServerScreen extends Screen {
    private final Screen parent;
    private final Instance instance;
    private final McTextField nameField = new McTextField(32);
    private final McTextField addressField = new McTextField(253);
    private final McButton addButton = new McButton(t("Add"), this::add);
    private final McButton cancelButton = new McButton(t("Cancel"), this::back);
    private String problem;

    public AddServerScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        buttons.add(addButton);
        buttons.add(cancelButton);
        nameField.setFocused(true);
    }

    private void back() {
        panel.setScreen(parent);
    }

    private void add() {
        String address = addressField.getText().strip();
        if (!Servers.validAddress(address)) return;
        try {
            Servers.add(Servers.file(instance), nameField.getText(), address);
            back();
        } catch (IOException e) {
            problem = t("Couldn't add it: {0}", e.getMessage());
        }
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int left = w / 2 - 100 * GUI;
        int top = h / 4;
        centered(g, t("Add Server"), w, 12 * GUI, 0xFFFFFF);
        font.draw(g, t("Server name"), left, top, GUI, 0xA0A0A0);
        nameField.setBounds(left, top + 11 * GUI, 200 * GUI, 20 * GUI);
        nameField.draw(g, font, GUI, panel.getTime());
        font.draw(g, t("Server address"), left, top + 40 * GUI, GUI, 0xA0A0A0);
        addressField.setBounds(left, top + 51 * GUI, 200 * GUI, 20 * GUI);
        addressField.draw(g, font, GUI, panel.getTime());
        String address = addressField.getText().strip();
        boolean valid = Servers.validAddress(address);
        if (problem != null) {
            centered(g, problem, w, top + 76 * GUI, 0xFF5555);
        } else if (!address.isEmpty() && !valid) {
            centered(g, t("An address looks like play.example.com or 192.168.1.5:25565"), w, top + 76 * GUI, 0xFF5555);
        } else {
            centered(g, t("Ask whoever runs the server for its address."), w, top + 76 * GUI, 0x808080);
        }
        addButton.setActive(valid);
        addButton.setBounds(left, h - 28 * GUI, 98 * GUI, 20 * GUI);
        cancelButton.setBounds(w / 2 + 2 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mousePressed(int x, int y) {
        if (nameField.contains(x, y)) addressFocused = false;
        else if (addressField.contains(x, y)) addressFocused = true;
        nameField.setFocused(!addressFocused);
        addressField.setFocused(addressFocused);
        super.mousePressed(x, y);
    }

    @Override
    public void keyTyped(char c) {
        if (c == '\n') {
            add(); // Enter adds it
        } else if (c == '\t') {
            boolean onName = !addressFocused;
            addressFocused = onName;
            nameField.setFocused(!onName);
            addressField.setFocused(onName);
        } else if (addressFocused) {
            addressField.keyTyped(c);
        } else {
            nameField.keyTyped(c);
        }
    }

    private boolean addressFocused; // which box is being typed in (Tab or a click switches)

    @Override
    public void keyPressed(int keyCode, boolean ctrl) {
        if (addressFocused) addressField.keyPressed(keyCode, ctrl);
        else nameField.keyPressed(keyCode, ctrl);
    }
}
