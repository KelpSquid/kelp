package kelp;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The title screen background: Minecraft water and kelp, animated like in the game. */
public class OceanPanel extends JPanel {
    private static final int SCALE = 4;               // each Minecraft pixel becomes 4x4 screen pixels
    private static final int BLOCK = 16 * SCALE;      // so one block is 64 screen pixels
    private static final int OCEAN_COLOR = 0x3F76E4;  // Minecraft's normal ocean water color
    private static final Color DEEP = new Color(0x0B1633);
    private static final int GUI = 2;                 // buttons and their text are drawn at 2x, like Minecraft's GUI scale

    private final BufferedImage[] water = Textures.frames(Textures.tint(Textures.load("water_still.png"), OCEAN_COLOR));
    private final BufferedImage[] kelpTop = Textures.frames(Textures.load("kelp.png"));
    private final BufferedImage[] kelpStem = Textures.frames(Textures.load("kelp_plant.png"));
    private final BufferedImage bubble = Textures.load("bubble.png");
    private final McFont font = new McFont(Textures.load("ascii.png"));

    private final McButton play = new McButton("Play", () -> { });
    private final McButton instances = new McButton("Instances", () -> { });
    private final McButton settings = new McButton("Settings", () -> { });
    private final McButton quit = new McButton("Quit", () -> System.exit(0));
    private final List<McButton> buttons = List.of(play, instances, settings, quit);

    private final Random random = new Random();
    private final int[] kelpHeights = new int[40]; // how many blocks tall the kelp is in each column (0 = none)
    private final List<Bubble> bubbles = new ArrayList<>();
    private double time = 0;

    public OceanPanel() {
        for (int i = 0; i < kelpHeights.length; i++) {
            kelpHeights[i] = random.nextInt(10) < 7 ? 1 + random.nextInt(5) : 0;
        }
        for (int i = 0; i < 25; i++) bubbles.add(newBubble(true));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                for (McButton b : buttons) b.setHovered(b.contains(e.getX(), e.getY()));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                for (McButton b : buttons) {
                    if (b.contains(e.getX(), e.getY())) b.click();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        // Move everything about 60 times a second
        new Timer(16, e -> {
            tick();
            repaint();
        }).start();
    }

    private Bubble newBubble(boolean anywhere) {
        Bubble b = new Bubble();
        b.x = random.nextDouble();
        b.y = anywhere ? random.nextDouble() : 1.05; // new bubbles start below the bottom edge
        b.speed = 0.001 + random.nextDouble() * 0.002;
        b.wobble = random.nextDouble() * Math.PI * 2;
        return b;
    }

    private void tick() {
        time += 0.016;
        for (int i = 0; i < bubbles.size(); i++) {
            Bubble b = bubbles.get(i);
            b.y -= b.speed;
            if (b.y < -0.05) bubbles.set(i, newBubble(false)); // popped at the top, make a new one
        }
    }

    /** Minecraft runs 20 ticks a second and these textures change frame every 2 ticks: 10 frames a second. */
    private BufferedImage frame(BufferedImage[] frames) {
        return frames[(int) (time * 10) % frames.length];
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        // Keep the pixels sharp and blocky instead of blurry
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int w = getWidth();
        int h = getHeight();

        // 1. Water: the see-through water texture tiled over a deep blue
        g.setColor(DEEP);
        g.fillRect(0, 0, w, h);
        BufferedImage waterFrame = frame(water);
        for (int y = 0; y < h; y += BLOCK) {
            for (int x = 0; x < w; x += BLOCK) {
                g.drawImage(waterFrame, x, y, BLOCK, BLOCK, null);
            }
        }

        // 2. Get darker the deeper you go
        g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 20, 0), 0, h, new Color(0, 0, 20, 150)));
        g.fillRect(0, 0, w, h);

        // 3. Kelp growing up from the bottom, one block at a time
        for (int column = 0; column < kelpHeights.length; column++) {
            int height = kelpHeights[column];
            for (int i = 0; i < height; i++) {
                BufferedImage block = i == height - 1 ? frame(kelpTop) : frame(kelpStem);
                g.drawImage(block, column * BLOCK, h - (i + 1) * BLOCK, BLOCK, BLOCK, null);
            }
        }

        // 4. Bubbles
        for (Bubble b : bubbles) {
            int x = (int) (b.x * w + Math.sin(time * 2 + b.wobble) * 6);
            int y = (int) (b.y * h);
            g.drawImage(bubble, x, y, 8 * SCALE, 8 * SCALE, null);
        }

        // 5. The buttons, laid out like Minecraft's title screen (sizes are in GUI pixels, times GUI)
        int buttonsY = h / 4 + 48 * GUI;
        int left = w / 2 - 100 * GUI;
        play.setBounds(left, buttonsY, 200 * GUI, 20 * GUI);
        instances.setBounds(left, buttonsY + 24 * GUI, 200 * GUI, 20 * GUI);
        settings.setBounds(left, buttonsY + 60 * GUI, 98 * GUI, 20 * GUI);
        quit.setBounds(left + 102 * GUI, buttonsY + 60 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);

        // 6. The title, sitting above the buttons
        int titleScale = 12;
        String title = "Kelp";
        int titleX = (w - font.width(title, titleScale)) / 2;
        int titleY = buttonsY - 8 * titleScale - 40;
        font.draw(g, title, titleX, titleY, titleScale, 0xFFFFFF);

        // 7. A yellow splash, tilted and pulsing like the one on Minecraft's title screen.
        //    Minecraft's own formula: it pulses every second and long splashes get shrunk to fit.
        String splash = "A launcher from the deep!";
        double pulse = 1.8 - Math.abs(Math.sin(time % 1.0 * Math.PI * 2) * 0.1);
        double splashScale = pulse * 100 / (font.width(splash, 1) + 32) * GUI;
        Graphics2D s = (Graphics2D) g.create();
        s.translate(w / 2 + 90 * GUI, titleY + 6 * titleScale);
        s.rotate(Math.toRadians(-20));
        s.scale(splashScale, splashScale);
        font.draw(s, splash, -font.width(splash, 1) / 2, -4, 1, 0xFFFF00);
        s.dispose();

        g.dispose();
    }

    private static class Bubble {
        double x, y, speed, wobble;
    }
}
