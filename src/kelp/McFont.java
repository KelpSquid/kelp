package kelp;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws text with Kelp's pixel font. font.png is a 16x16 grid of 8x8 characters, in character order.
 * font-extra.png has the letters other languages need (accents, and the Russian alphabet), in the order of EXTRA.
 * Its cells are 10 tall: the top 2 rows are above the line, where capitals like É and Ö wear their accents.
 */
public class McFont {
    /** The letters in font-extra.png, in order. Made by the same script that draws them. */
    static final String EXTRA = "áéíóúýàèùâêîôûäëïöüÿñåÁÉÍÓÚÝÀÈÙÂÊÎÔÛÄËÏÖÜÑÅçÇßæÆœŒþÞðÐ¿¡«»БГДЖЗИЛПУФЦЧШЩЪЫЬЭЮЯбвгджзиклмнптфцчшщъыьэюяАВЕКМНОРСТХаеорсухЙйЁё";

    /** Rows above the line in font-extra.png. */
    private static final int ABOVE = 2;
    private static final int EXTRA_HEIGHT = 8 + ABOVE;

    private final BufferedImage sheet;
    private final BufferedImage extra;
    private final int[] widths = new int[128];
    private final int[] extraWidths = new int[EXTRA.length()];
    private final Map<Integer, BufferedImage> colored = new HashMap<>();
    private final Map<Integer, BufferedImage> coloredExtra = new HashMap<>();

    public McFont(BufferedImage sheet) {
        this(sheet, null);
    }

    public McFont(BufferedImage sheet, BufferedImage extra) {
        this.sheet = sheet;
        this.extra = extra;
        // A character is as wide as its rightmost pixel that isn't see-through
        for (int c = 0; c < 128; c++) widths[c] = measure(sheet, c, 8);
        if (extra != null) {
            for (int i = 0; i < EXTRA.length(); i++) extraWidths[i] = measure(extra, i, EXTRA_HEIGHT);
        }
        widths[' '] = 3; // space has no pixels, so it gets its width by hand
    }

    private static int measure(BufferedImage sheet, int index, int height) {
        int cx = index % 16 * 8;
        int cy = index / 16 * height;
        int width = 0;
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < height; y++) {
                if ((sheet.getRGB(cx + x, cy + y) >>> 24) != 0) width = x + 1;
            }
        }
        return width;
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
            } else if (extra != null && EXTRA.indexOf(c) >= 0) {
                int i = EXTRA.indexOf(c);
                BufferedImage more = coloredExtra.computeIfAbsent(rgb, color -> Textures.tint(extra, color));
                int sx = i % 16 * 8;
                int sy = i / 16 * EXTRA_HEIGHT;
                int top = y - ABOVE * scale;
                g.drawImage(more, x, top, x + 8 * scale, top + EXTRA_HEIGHT * scale, sx, sy, sx + 8, sy + EXTRA_HEIGHT, null);
            }
            x += advance(c) * scale;
        }
    }

    private int advance(char c) {
        if (c < 128) return widths[c] + 1; // one pixel of space between letters
        int i = EXTRA.indexOf(c);
        return (extra != null && i >= 0 ? extraWidths[i] : 0) + 1;
    }
}
