package kelp;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Loads Kelp's textures and other images. */
public final class Textures {
    private Textures() {
    }

    /** One of Kelp's textures, like "water.png". */
    public static BufferedImage load(String name) {
        return readBundled("textures/" + name);
    }

    /**
     * An image that comes with Kelp, like "textures/water.png" or "branding/kelp.png". A packaged Kelp
     * keeps them inside kelp.jar; when running from source they're in the project folder.
     */
    public static BufferedImage readBundled(String path) {
        try (InputStream in = Textures.class.getResourceAsStream("/" + path)) {
            if (in != null) {
                BufferedImage image = ImageIO.read(in);
                if (image == null) throw new IOException("it is not a PNG");
                return image;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't load " + path + " (" + e.getMessage() + ")", e);
        }
        return read(new File(path));
    }

    /** Loads any PNG, like the Kelp logo in the branding folder. */
    public static BufferedImage read(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) throw new IOException("it is not a PNG");
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't load " + file + " (" + e.getMessage() + ")", e);
        }
    }

    /** Cuts an animation strip (frames stacked top to bottom, like water_still.png) into square frames. */
    public static BufferedImage[] frames(BufferedImage strip) {
        int size = strip.getWidth();
        BufferedImage[] frames = new BufferedImage[strip.getHeight() / size];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = strip.getSubimage(0, i * size, size, size);
        }
        return frames;
    }

    /** Shrinks a big image smoothly, halving it step by step so small sizes stay clean instead of jagged. */
    public static BufferedImage shrink(BufferedImage image, int size) {
        BufferedImage current = image;
        int w = image.getWidth();
        while (w > size) {
            w = Math.max(size, w / 2);
            BufferedImage half = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, w, w, null);
            g.dispose();
            current = half;
        }
        return current;
    }

    /** Multiplies every pixel by a color. That's how the gray water texture turns blue. */
    /** The same picture in another color: every pixel keeps how light and how colorful it is, with this hue (0-1). */
    public static BufferedImage hue(BufferedImage image, float hue) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float[] hsb = new float[3];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int p = image.getRGB(x, y);
                java.awt.Color.RGBtoHSB(p >> 16 & 0xFF, p >> 8 & 0xFF, p & 0xFF, hsb);
                out.setRGB(x, y, (p & 0xFF000000) | (java.awt.Color.HSBtoRGB(hue, hsb[1], hsb[2]) & 0xFFFFFF));
            }
        }
        return out;
    }

    public static BufferedImage tint(BufferedImage image, int rgb) {
        int tr = rgb >> 16 & 0xFF;
        int tg = rgb >> 8 & 0xFF;
        int tb = rgb & 0xFF;
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int p = image.getRGB(x, y);
                int a = p >>> 24;
                int r = (p >> 16 & 0xFF) * tr / 255;
                int g = (p >> 8 & 0xFF) * tg / 255;
                int b = (p & 0xFF) * tb / 255;
                out.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
            }
        }
        return out;
    }
}
