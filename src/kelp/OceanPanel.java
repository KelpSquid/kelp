package kelp;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Kelp's window contents: an animated ocean of water, kelp and bubbles, with the current screen on top. */
public class OceanPanel extends JPanel {
    private static final int BLOCK = 128;             // water tiles and kelp pieces are 32 pixels, drawn at 4x
    private static final int BUBBLE = 48;             // bubbles are 16 pixels, drawn at 3x
    private final BufferedImage waterGray = Textures.load("water.png"); // gray, so a theme can tint it any color
    private BufferedImage[] water;      // the water tiles in the theme's color
    private Theme drawn;                // the theme the tiles and picture were made for
    private BufferedImage picture;      // the theme's own background picture, if it has one
    private final BufferedImage[] kelpTop = Textures.frames(Textures.load("kelp_top.png"));
    private final BufferedImage[] kelpStem = Textures.frames(Textures.load("kelp_stem.png"));
    private final BufferedImage bubble = Textures.load("bubble.png");
    private final McFont font = new McFont(Textures.load("font.png"), Textures.load("font-extra.png"));

    private final Random random = new Random();
    private final int[] kelpHeights = new int[20]; // how many pieces tall the kelp is in each column (0 = none)
    private final List<Bubble> bubbles = new ArrayList<>();
    private double time = 0;

    private Screen screen = new TitleScreen(this);
    private Point mouse = new Point(-1, -1);
    private int quietFrames;

    public OceanPanel() {
        for (int i = 0; i < kelpHeights.length; i++) {
            kelpHeights[i] = random.nextInt(10) < 7 ? 1 + random.nextInt(3) : 0;
        }
        for (int i = 0; i < 25; i++) bubbles.add(newBubble(true));

        // Pass the mouse along to whichever screen is showing
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                screen.mouseMoved(e.getX(), e.getY());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow(); // so typing goes to Kelp
                if (e.getButton() == MouseEvent.BUTTON1) screen.mousePressed(e.getX(), e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouse = e.getPoint();
                screen.mouseDragged(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) screen.mouseReleased(e.getX(), e.getY());
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                screen.mouseWheel(e.getX(), e.getY(), e.getWheelRotation());
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);

        // Files dragged onto the window go to the screen that's showing (mods onto Mods, worlds onto Worlds)
        setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.List<java.io.File> files = (java.util.List<java.io.File>)
                            support.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    screen.filesDropped(files.stream().map(java.io.File::toPath).toList());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        // Pass the keyboard along too, for typing in text boxes
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                screen.keyTyped(e.getKeyChar());
            }

            @Override
            public void keyPressed(KeyEvent e) {
                screen.keyPressed(e.getKeyCode(), e.isControlDown());
            }
        });

        screen.shown(); // the first screen appears without setScreen, so tell it here

        // Move everything about 60 times a second
        new Timer(16, e -> {
            tick();
            // Kelp doesn't need to draw while it's minimized, and barely while it's behind another window
            // (like the game), so it takes it easy then and saves the computer's effort
            Window window = SwingUtilities.getWindowAncestor(this);
            boolean minimized = window instanceof Frame frame && (frame.getExtendedState() & Frame.ICONIFIED) != 0;
            boolean behind = window != null && !window.isActive();
            if (minimized || behind && ++quietFrames % 4 != 0) return;
            repaint();
        }).start();
    }

    public McFont getMcFont() {
        return font;
    }

    /** Seconds since Kelp opened. */
    public double getTime() {
        return time;
    }

    public Screen getScreen() {
        return screen;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
        screen.shown();
        screen.mouseMoved(mouse.x, mouse.y); // so a button already under the mouse lights up right away
    }

    private Bubble newBubble(boolean anywhere) {
        Bubble b = new Bubble();
        Theme.Particles kind = Theme.current().scene().particles;
        b.x = random.nextDouble();
        b.y = anywhere ? random.nextDouble() : kind == Theme.Particles.ASH ? -0.05 : 1.05; // ash starts above the top
        if (kind == Theme.Particles.CLOUDS) {
            b.x = anywhere ? random.nextDouble() : -0.3; // clouds come in from the left
            b.y = random.nextDouble() * 0.6;
        }
        b.speed = 0.001 + random.nextDouble() * 0.002;
        b.wobble = random.nextDouble() * Math.PI * 2;
        return b;
    }

    private void tick() {
        time += 0.016;
        Theme.Particles kind = Theme.current().scene().particles;
        for (int i = 0; i < bubbles.size(); i++) {
            Bubble b = bubbles.get(i);
            switch (kind) {
                case BUBBLES, EMBERS -> b.y -= b.speed;      // float up
                case ASH -> b.y += b.speed * 0.5;            // drift down
                case CLOUDS -> b.x += b.speed * 0.15;        // drift across
                default -> {
                }
            }
            if (b.y < -0.05 || b.y > 1.1 || b.x > 1.2) bubbles.set(i, newBubble(false)); // gone: a new one comes in
        }
    }

    /** Remakes the background's pieces when the theme changes. */
    private void prepare(Theme theme) {
        if (theme == drawn) return;
        boolean particlesChanged = drawn == null || drawn.scene().particles != theme.scene().particles;
        drawn = theme;
        water = theme.water() < 0 ? null : Textures.frames(Textures.tint(waterGray, theme.water()));
        picture = theme.picture() == null ? null : Textures.read(theme.picture().toFile());
        if (particlesChanged) {
            bubbles.clear();
            for (int i = 0; i < 25; i++) bubbles.add(newBubble(true));
        }
        ThemeMusic.play(theme.music());
    }

    /** The water and kelp animations play at 10 frames a second. */
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

        Theme theme = Theme.current();
        prepare(theme);

        // 1. The theme's base color, then its tiles (like the ocean's water), or its own picture
        g.setColor(new Color(theme.base()));
        g.fillRect(0, 0, w, h);
        if (picture != null) {
            double cover = Math.max(w / (double) picture.getWidth(), h / (double) picture.getHeight());
            int pw = (int) Math.ceil(picture.getWidth() * cover);
            int ph = (int) Math.ceil(picture.getHeight() * cover);
            Graphics2D smooth = (Graphics2D) g.create();
            smooth.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            smooth.drawImage(picture, (w - pw) / 2, (h - ph) / 2, pw, ph, null);
            smooth.dispose();
        } else if (water != null) {
            BufferedImage waterFrame = frame(water);
            for (int y = 0; y < h; y += BLOCK) {
                for (int x = 0; x < w; x += BLOCK) {
                    g.drawImage(waterFrame, x, y, BLOCK, BLOCK, null);
                }
            }
        }

        // 2. Get darker toward the bottom (the sky gets a little lighter instead)
        Theme.Particles kind = theme.scene().particles;
        if (kind == Theme.Particles.CLOUDS) {
            g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 0), 0, h, new Color(255, 255, 255, 90)));
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 20, 0), 0, h, new Color(0, 0, 20, 150)));
        }
        g.fillRect(0, 0, w, h);

        // 3. Kelp growing up from the bottom, one block at a time (only under the sea)
        for (int column = 0; theme.scene().kelp && column < kelpHeights.length; column++) {
            int height = kelpHeights[column];
            for (int i = 0; i < height; i++) {
                BufferedImage block = i == height - 1 ? frame(kelpTop) : frame(kelpStem);
                g.drawImage(block, column * BLOCK, h - (i + 1) * BLOCK, BLOCK, BLOCK, null);
            }
        }

        // 4. What floats around: bubbles, embers, ash, clouds or stars
        for (Bubble b : bubbles) {
            int x = (int) (b.x * w + Math.sin(time * 2 + b.wobble) * 6);
            int y = (int) (b.y * h);
            switch (kind) {
                case BUBBLES -> g.drawImage(bubble, x, y, BUBBLE, BUBBLE, null);
                case EMBERS -> {
                    int size = 6 + (int) (b.wobble * 2);
                    int glow = (int) (180 + 75 * Math.sin(time * 6 + b.wobble * 3));
                    g.setColor(new Color(255, 120 + (int) (b.wobble * 20), 20, Math.max(0, Math.min(255, glow))));
                    g.fillRect(x, y, size, size);
                }
                case ASH -> {
                    g.setColor(new Color(90, 80, 80, 170));
                    g.fillRect(x, y, 6, 6);
                }
                case CLOUDS -> {
                    int cx = (int) (b.x * w);
                    int size = 24 + (int) (b.wobble * 4);
                    g.setColor(new Color(255, 255, 255, 200));
                    g.fillRect(cx, y, size * 6, size);
                    g.fillRect(cx + size, y - size / 2, size * 3, size);
                }
                case STARS -> {
                    int twinkle = (int) (130 + 125 * Math.sin(time * 2 + b.wobble * 5));
                    g.setColor(new Color(255, 245, 255, Math.max(0, Math.min(255, twinkle))));
                    int size = b.wobble > 3 ? 6 : 4;
                    g.fillRect((int) (b.x * w), y, size, size);
                }
                default -> {
                }
            }
        }

        // 5. Whatever screen is showing, on top of the ocean
        screen.draw(g, w, h);

        g.dispose();
    }

    private static class Bubble {
        double x, y, speed, wobble;
    }
}
