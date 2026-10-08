package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;

/**
 * Parent Controls in Options. The first time, a parent picks a PIN (4 to 8 digits, typed twice). After that the
 * switches only open with the PIN: Multiplayer, Chat and Voice chat, plus changing the PIN or taking Parent Controls off.
 */
public class ParentControlsScreen extends Screen {
    private final Screen parent;
    private final McTextField pinField = new McTextField(8);
    private final McTextField againField = new McTextField(8);
    private final McButton okButton = new McButton("", this::ok);
    private final McButton multiplayerButton = new McButton("", () -> ParentControls.setMultiplayerAllowed(!ParentControls.multiplayerAllowed()));
    private final McButton chatButton = new McButton("", () -> ParentControls.setChatAllowed(!ParentControls.chatAllowed()));
    private final McButton voiceButton = new McButton("", () -> ParentControls.setVoiceAllowed(!ParentControls.voiceAllowed()));
    private final McButton changePinButton = new McButton(t("Change PIN"), this::changePin);
    private final McButton removeButton = new McButton(t("Turn Off Parent Controls"), this::removeControls);
    private final McButton doneButton = new McButton(t("Done"), this::done);
    private boolean unlocked;
    private boolean choosingPin;
    private String message;

    public ParentControlsScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        pinField.setHidden(true);
        againField.setHidden(true);
        pinField.setFocused(true);
        choosingPin = !ParentControls.hasPin();
        for (McButton b : new McButton[] {okButton, multiplayerButton, chatButton, voiceButton, changePinButton, removeButton, doneButton}) buttons.add(b);
    }

    private void done() {
        panel.setScreen(parent);
    }

    private void ok() {
        String pin = pinField.getText();
        if (choosingPin) {
            if (!ParentControls.validPin(pin)) {
                message = t("A PIN is 4 to 8 numbers.");
            } else if (!pin.equals(againField.getText())) {
                message = t("The two PINs don't match.");
            } else {
                ParentControls.setPin(pin);
                choosingPin = false;
                unlocked = true;
                message = t("Parent Controls are on. Don't forget the PIN!");
            }
        } else if (ParentControls.checkPin(pin)) {
            unlocked = true;
            message = null;
        } else {
            message = t("That's not the PIN.");
        }
        pinField.setText("");
        againField.setText("");
    }

    private void changePin() {
        choosingPin = true;
        unlocked = false;
        message = null;
    }

    private void removeControls() {
        ParentControls.remove();
        unlocked = false;
        choosingPin = true;
        message = t("Parent Controls are off.");
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Parent Controls"), w, 12 * GUI, 0xFFFFFF);
        int left = w / 2 - 100 * GUI;
        int y = 40 * GUI;
        boolean asking = choosingPin || !unlocked;
        for (McButton b : new McButton[] {okButton, multiplayerButton, chatButton, voiceButton, changePinButton, removeButton}) b.setBounds(-1000, -1000, 0, 0);
        if (asking) {
            centered(g, choosingPin ? t("Pick a PIN that only a parent knows.") : t("Type the parent PIN to change these."), w, y, 0xA0A0A0);
            pinField.setBounds(left, y + 14 * GUI, 200 * GUI, 20 * GUI);
            pinField.draw(g, font, GUI, panel.getTime());
            if (choosingPin) {
                font.draw(g, t("Again"), left, y + 38 * GUI, GUI, 0xA0A0A0);
                againField.setBounds(left, y + 48 * GUI, 200 * GUI, 20 * GUI);
                againField.draw(g, font, GUI, panel.getTime());
            }
            okButton.setLabel(choosingPin ? t("Set PIN") : t("Unlock"));
            okButton.setBounds(left, y + (choosingPin ? 74 : 40) * GUI, 200 * GUI, 20 * GUI);
        } else {
            multiplayerButton.setLabel(ParentControls.multiplayerAllowed() ? t("Multiplayer: Allowed") : t("Multiplayer: Off"));
            chatButton.setLabel(ParentControls.chatAllowed() ? t("Chat: Allowed") : t("Chat: Off"));
            multiplayerButton.setBounds(left, y, 200 * GUI, 20 * GUI);
            voiceButton.setLabel(ParentControls.voiceAllowed() ? t("Voice Chat: Allowed") : t("Voice Chat: Off"));
            chatButton.setBounds(left, y + 24 * GUI, 200 * GUI, 20 * GUI);
            voiceButton.setBounds(left, y + 48 * GUI, 200 * GUI, 20 * GUI);
            centered(g, t("These hold even with mods."), w, y + 74 * GUI, 0x808080);
            changePinButton.setBounds(left, y + 94 * GUI, 200 * GUI, 20 * GUI);
            removeButton.setBounds(left, y + 118 * GUI, 200 * GUI, 20 * GUI);
        }
        if (message != null) centered(g, message, w, h - 44 * GUI, 0xFFFF55);
        doneButton.setBounds(left, h - 28 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    /** Which box typing goes into: the PIN, or (while picking one) the PIN again. */
    private void focus(boolean again) {
        againFocused = again;
        againField.setFocused(again);
        pinField.setFocused(!again);
    }

    @Override
    public void mousePressed(int x, int y) {
        if (againField.contains(x, y) && choosingPin) focus(true);
        else if (pinField.contains(x, y)) focus(false);
        super.mousePressed(x, y);
    }

    @Override
    public void keyTyped(char c) {
        if (c == '\n' || c == '\r') {
            ok();
            focus(false);
            return;
        }
        if (Character.isDigit(c)) { // PINs are numbers; only the box being typed in takes it
            pinField.keyTyped(c);
            againField.keyTyped(c);
        }
    }

    @Override
    public void keyPressed(int keyCode, boolean ctrl) {
        if (keyCode == java.awt.event.KeyEvent.VK_TAB && choosingPin) {
            focus(!againFocused);
            return;
        }
        pinField.keyPressed(keyCode, ctrl);
        againField.keyPressed(keyCode, ctrl);
    }

    private boolean againFocused;
}

