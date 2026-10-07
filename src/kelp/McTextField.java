package kelp;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/** A Minecraft-style text box: black, with a gray border that turns white while you type in it, and a blinking _. */
public class McTextField {
    private final int maxLength;
    private final Rectangle bounds = new Rectangle();
    private String text = "";
    private boolean focused;
    private boolean hidden;
    private Consumer<String> onChange = text -> { };

    public McTextField(int maxLength) {
        this.maxLength = maxLength;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    /** Runs every time the text changes from typing. */
    public void onChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    public void setBounds(int x, int y, int width, int height) {
        bounds.setBounds(x, y, width, height);
    }

    public boolean contains(int x, int y) {
        return bounds.contains(x, y);
    }

    /** Shows * in place of each letter, for PINs. */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public void keyTyped(char c) {
        if (!focused || c < ' ' || c == 127) return; // only letters, numbers, symbols and spaces
        if (text.length() < maxLength) {
            text += c;
            onChange.accept(text);
        }
    }

    public void keyPressed(int keyCode, boolean ctrl) {
        if (!focused) return;
        if (keyCode == KeyEvent.VK_BACK_SPACE && !text.isEmpty()) {
            text = ctrl ? "" : text.substring(0, text.length() - 1); // Ctrl+Backspace clears it all
            onChange.accept(text);
        } else if (keyCode == KeyEvent.VK_V && ctrl) {
            try {
                String pasted = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                for (char c : pasted.toCharArray()) keyTyped(c);
            } catch (Exception e) {
                // nothing to paste, or it wasn't text
            }
        }
    }

    public void draw(Graphics2D g, McFont font, int scale, double time) {
        g.setColor(new Color(focused ? 0xFFFFFF : 0xA0A0A0));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(Color.BLACK);
        g.fillRect(bounds.x + scale, bounds.y + scale, bounds.width - 2 * scale, bounds.height - 2 * scale);

        // If the text is too long to fit, show its end, like Minecraft does while you type
        String shown = hidden ? "*".repeat(text.length()) : text;
        int room = bounds.width - 10 * scale;
        while (!shown.isEmpty() && font.width(shown, scale) > room) shown = shown.substring(1);
        boolean cursorOn = focused && (int) (time * 3) % 2 == 0; // blinks about three times a second
        int textY = bounds.y + (bounds.height - 8 * scale) / 2;
        font.draw(g, shown + (cursorOn ? "_" : ""), bounds.x + 4 * scale, textY, scale, 0xE0E0E0);
    }
}
