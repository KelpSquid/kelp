package kelp;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** A pixel-art button made of sea glass: it lights up when the mouse is over it and goes dark when it's off. */
public class McButton {
    private final BufferedImage normal = Textures.load("button.png");
    private final BufferedImage highlighted = Textures.load("button_highlighted.png");
    private final BufferedImage disabled = Textures.load("button_disabled.png");

    private String label;
    private final Runnable action;
    private final Rectangle bounds = new Rectangle();
    private boolean hovered;
    private boolean active = true;

    public McButton(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** Where the button sits on screen, in screen pixels. */
    public void setBounds(int x, int y, int width, int height) {
        bounds.setBounds(x, y, width, height);
    }

    /** A button that isn't active is grayed out and can't be clicked. */
    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean contains(int x, int y) {
        return bounds.contains(x, y);
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public void click() {
        if (active) action.run();
    }

    public void draw(Graphics2D g, McFont font, int scale) {
        BufferedImage texture = !active ? disabled : hovered ? highlighted : normal;
        int x = bounds.x;
        int y = bounds.y;
        int w = bounds.width;
        int h = bounds.height;

        // The left half comes from the texture's left end and the right half
        // from its right end, so a short button still gets both rounded edges
        int left = w / 2;
        int right = w - left;
        g.drawImage(texture, x, y, x + left, y + h, 0, 0, left / scale, 20, null);
        g.drawImage(texture, x + left, y, x + w, y + h, 200 - right / scale, 0, 200, 20, null);

        // The text turns yellow when you hover, and gray when the button is off
        int color = !active ? 0xA0A0A0 : hovered ? 0xFFFFA0 : 0xE0E0E0;
        font.draw(g, label, x + (w - font.width(label, scale)) / 2, y + (h - 8 * scale) / 2, scale, color);
    }
}
