package kelp;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** A pixel-art button made of sea glass: it lights up when the mouse is over it and goes dark when it's off. */
public class McButton {
    private static final BufferedImage[] SEA_GLASS = {Textures.load("button.png"), Textures.load("button_highlighted.png"),
            Textures.load("button_disabled.png")};
    private static final java.util.Map<Integer, BufferedImage[]> THEMED = new java.util.concurrent.ConcurrentHashMap<>();

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

    /** The sea-glass buttons recolored to the theme's hue (-1 keeps them teal), made once per hue. */
    static BufferedImage[] textures(int hue) {
        if (hue < 0) return SEA_GLASS;
        return THEMED.computeIfAbsent(hue, h -> {
            BufferedImage[] out = new BufferedImage[3];
            for (int i = 0; i < 3; i++) out[i] = Textures.hue(SEA_GLASS[i], h / 360f);
            return out;
        });
    }

    public void draw(Graphics2D g, McFont font, int scale) {
        BufferedImage[] look = textures(Theme.current().buttons());
        BufferedImage texture = !active ? look[2] : hovered ? look[1] : look[0];
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
        // Some languages have long words: a label that doesn't fit is shortened with "..." instead of spilling out
        String text = label;
        int room = w - 6 * scale;
        if (font.width(text, scale) > room) {
            while (text.length() > 1 && font.width(text.stripTrailing() + "...", scale) > room) {
                text = text.substring(0, text.length() - 1);
            }
            text = text.stripTrailing() + "...";
        }
        font.draw(g, text, x + (w - font.width(text, scale)) / 2, y + (h - 8 * scale) / 2, scale, color);
    }
}
