package kelp;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Draws text with Kelp's pixel font. font.png is a 16x16 grid of 8x8 characters, in character order. */
public class McFont {
    private final BufferedImage sheet;
    private final int[] widths = new int[128];
    private final Map<Integer, BufferedImage> colored = new HashMap<>();

    public McFont(BufferedImage sheet) {
        this.sheet = sheet;
        // A character is as wide as its rightmost pixel that isn't see-through
        for (int c = 0; c < 128; c++) {
            int cx = c % 16 * 8;
            int cy = c / 16 * 8;
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    if ((sheet.getRGB(cx + x, cy + y) >>> 24) != 0) widths[c] = x + 1;
                }
            }
        }
        widths[' '] = 3; // space has no pixels, so it gets its width by hand
    }

    /** How wide some text is, in screen pixels, at this scale. */
    public int width(String text, int scale) {
        int width = 0;
        for (char c : text.toCharArray()) width += advance(c);
        return (width - 1) * scale; // no gap after the last letter
    }

    /** Draws text with a drop shadow: the same color at a quarter brightness, one pixel down-right. */
    public void draw(Graphics2D g, String text, int x, int y, int scale, int rgb) {
        int shadow = (rgb & 0xFCFCFC) >> 2;
        drawPlain(g, text, x + scale, y + scale, scale, shadow);
        drawPlain(g, text, x, y, scale, rgb);
    }

    private void drawPlain(Graphics2D g, String text, int x, int y, int scale, int rgb) {
        BufferedImage letters = colored.computeIfAbsent(rgb, color -> Textures.tint(sheet, color));
        for (char c : text.toCharArray()) {
            if (c < 128) {
                int sx = c % 16 * 8;
                int sy = c / 16 * 8;
                g.drawImage(letters, x, y, x + 8 * scale, y + 8 * scale, sx, sy, sx + 8, sy + 8, null);
            }
            x += advance(c) * scale;
        }
    }

    private int advance(char c) {
        return (c < 128 ? widths[c] : 0) + 1; // one pixel of space between letters
    }
}
