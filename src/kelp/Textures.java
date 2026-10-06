package kelp;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Loads the Minecraft textures in the textures folder. */
public final class Textures {
    private Textures() {
    }

    public static BufferedImage load(String name) {
        File file = new File("textures", name);
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

    /** Multiplies every pixel by a color, the same way Minecraft turns its gray water texture blue. */
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
