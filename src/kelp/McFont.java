package kelp;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws text with Kelp's pixel font. font.png is a 16x16 grid of 8x8 characters, in character order.
 * font-extra.png has the letters other languages need (accents, and the Russian alphabet), in the order of EXTRA.
 * Its cells are 10 tall: the top 2 rows are above the line, where capitals like É and Ö wear their accents.
 *
 * Text with letters neither sheet has (Chinese, Arabic, Hindi...) is drawn whole with the computer's own font, the way
 * Minecraft does it: as 16-pixel letters with smoothing off, shown at half size, so they stay crisp and blocky like
 * the rest. Drawing the whole text at once lets right-to-left languages and joined-up letters (like Arabic's) come out
 * right. No font files come with Kelp for this.
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
        if (!pixelOnly(text)) return (int) Math.ceil(layout(text).getAdvance() * scale / 2);
        int width = 0;
        for (char c : text.toCharArray()) width += advance(c);
        return (width - 1) * scale; // no gap after the last letter
    }

    /** Draws text with a drop shadow: the same color at a quarter brightness, one pixel down-right. */
    public void draw(Graphics2D g, String text, int x, int y, int scale, int rgb) {
        if (!pixelOnly(text)) {
            drawWithComputerFont(g, text, x, y, scale, rgb);
            return;
        }
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

    /** Whether every letter is in Kelp's own pixel font. */
    boolean pixelOnly(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 128 && (extra == null || EXTRA.indexOf(c) < 0)) return false;
        }
        return true;
    }

    // ---- Letters Kelp's font doesn't have ----

    private static final FontRenderContext PLAIN_CONTEXT = new FontRenderContext(null, false, false);
    /** Fonts that Windows, Mac and Linux usually have, for scripts the standard font can't draw. */
    private static final String[] WIDE_FONTS = {"Nirmala UI", "Leelawadee UI", "Segoe UI", "Segoe UI Historic", "Microsoft YaHei",
            "Yu Gothic", "Malgun Gothic", "Microsoft JhengHei", "Ebrima", "Noto Sans", "Arial Unicode MS", "Noto Sans CJK SC"};
    private static final Map<Character.UnicodeScript, Font> fontsByScript = new HashMap<>();
    private static List<Font> installedFonts;

    /** The text laid out with a font that can draw it, twice as tall as Kelp's own 8-pixel letters. */
    private static TextLayout layout(String text) {
        return new TextLayout(text, fontFor(text).deriveFont(Font.BOLD, 18f), PLAIN_CONTEXT); // bold, to be as chunky as Kelp's letters
    }

    /** Texts already drawn as 16-pixel pictures, by text and color, so they aren't drawn again every frame. */
    private static final Map<String, BufferedImage> pictures = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 300;
        }
    };

    /** The text drawn unsmoothed into a picture, its baseline 16 pixels down (Kelp's letters stand on row 7 of 8). */
    private static synchronized BufferedImage picture(String text, int rgb) {
        String key = rgb + "|" + text;
        BufferedImage known = pictures.get(key);
        if (known != null) return known;
        TextLayout layout = layout(text);
        int width = Math.max(1, (int) Math.ceil(layout.getAdvance()) + 2);
        BufferedImage image = new BufferedImage(width, 22, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new java.awt.Color(rgb & 0xFFFFFF));
        layout.draw(g, 0, 16);
        g.dispose();
        pictures.put(key, image);
        return image;
    }

    /** A font that has every letter in the text: the standard one if it can, or an installed one that can. */
    private static synchronized Font fontFor(String text) {
        Font standard = new Font(Font.DIALOG, Font.PLAIN, 12);
        if (standard.canDisplayUpTo(text) < 0) return standard;
        Character.UnicodeScript script = Character.UnicodeScript.COMMON;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            Character.UnicodeScript s = Character.UnicodeScript.of(c);
            if (c >= 128 && s != Character.UnicodeScript.COMMON && s != Character.UnicodeScript.INHERITED) {
                script = s;
                break;
            }
            i += Character.charCount(c);
        }
        Font known = fontsByScript.get(script);
        if (known != null) return known;
        Font best = standard;
        int bestReach = standard.canDisplayUpTo(text);
        for (String name : WIDE_FONTS) {
            Font font = new Font(name, Font.PLAIN, 12);
            if (!font.getFamily().equals(name)) continue; // not installed: Java gave back a stand-in
            int reach = font.canDisplayUpTo(text);
            if (reach < 0) {
                best = font;
                bestReach = -1;
                break;
            }
            if (reach > bestReach) {
                best = font;
                bestReach = reach;
            }
        }
        if (bestReach >= 0) {
            if (installedFonts == null) installedFonts = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts());
            for (Font font : installedFonts) {
                if (font.canDisplayUpTo(text) < 0) {
                    best = font.deriveFont(Font.PLAIN, 12f);
                    break;
                }
            }
        }
        fontsByScript.put(script, best);
        return best;
    }

    /**
     * Draws the whole text as 16-pixel letters shown at half size, blown up without smoothing like Kelp's own letters,
     * with the same drop shadow.
     */
    private static void drawWithComputerFont(Graphics2D g, String text, int x, int y, int scale, int rgb) {
        Object oldScaling = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (int pass = 0; pass < 2; pass++) {
            BufferedImage image = picture(text, pass == 0 ? (rgb & 0xFCFCFC) >> 2 : rgb);
            int offset = pass == 0 ? scale : 0;
            int top = y + 7 * scale - 8 * scale + offset; // the picture's baseline (row 16) lands on Kelp's (row 7)
            g.drawImage(image, x + offset, top, x + offset + image.getWidth() * scale / 2, top + image.getHeight() * scale / 2, 0, 0,
                    image.getWidth(), image.getHeight(), null);
        }
        if (oldScaling != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldScaling);
    }

    private int advance(char c) {
        if (c < 128) return widths[c] + 1; // one pixel of space between letters
        int i = EXTRA.indexOf(c);
        return (extra != null && i >= 0 ? extraWidths[i] : 0) + 1;
    }
}
