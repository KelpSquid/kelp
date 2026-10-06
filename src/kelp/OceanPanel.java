package kelp;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The animated underwater background behind everything in Kelp. */
public class OceanPanel extends JPanel {
    private static final Color SURFACE = new Color(0x1B6E7A);
    private static final Color DEEP = new Color(0x04121C);
    private static final Color KELP_BACK = new Color(0x184A2A);
    private static final Color KELP_FRONT = new Color(0x2F8A45);

    private final Random random = new Random();
    private final List<Bubble> bubbles = new ArrayList<>();
    private final List<Stalk> stalks = new ArrayList<>();
    private double time = 0;

    public OceanPanel() {
        for (int i = 0; i < 40; i++) bubbles.add(newBubble(true));

        // Two rows of kelp: a dark one in the back, a bright one in front
        for (int i = 0; i < 14; i++) stalks.add(newStalk(false));
        for (int i = 0; i < 9; i++) stalks.add(newStalk(true));

        // Move everything about 60 times a second
        new Timer(16, e -> {
            tick();
            repaint();
        }).start();
    }

    private Stalk newStalk(boolean front) {
        Stalk s = new Stalk();
        s.x = random.nextDouble();
        s.height = front ? 0.35 + random.nextDouble() * 0.35 : 0.45 + random.nextDouble() * 0.4;
        s.phase = random.nextDouble() * Math.PI * 2;
        s.front = front;
        return s;
    }

    private Bubble newBubble(boolean anywhere) {
        Bubble b = new Bubble();
        b.x = random.nextDouble();
        b.y = anywhere ? random.nextDouble() : 1.05; // new bubbles start below the bottom edge
        b.size = 3 + random.nextDouble() * 9;
        b.speed = 0.0008 + random.nextDouble() * 0.002;
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

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        // 1. Water: bright near the surface, almost black at the bottom
        g.setPaint(new GradientPaint(0, 0, SURFACE, 0, h, DEEP));
        g.fillRect(0, 0, w, h);

        // 2. Sunlight rays coming down from the surface
        for (int i = 0; i < 5; i++) {
            double x = w * (0.1 + i * 0.2) + Math.sin(time * 0.3 + i) * 40;
            int alpha = (int) (16 + 10 * Math.sin(time * 0.8 + i * 1.7));
            // Each ray fades out before it reaches the seafloor
            g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, alpha), 0, h * 0.8f, new Color(255, 255, 255, 0)));
            Path2D ray = new Path2D.Double();
            ray.moveTo(x - 30, 0);
            ray.lineTo(x + 30, 0);
            ray.lineTo(x + 170, h);
            ray.lineTo(x + 40, h);
            ray.closePath();
            g.fill(ray);
        }

        // 3. Kelp, back row first so the front row covers it
        for (Stalk s : stalks) if (!s.front) drawStalk(g, s, w, h);
        for (Stalk s : stalks) if (s.front) drawStalk(g, s, w, h);

        // 4. Bubbles
        for (Bubble b : bubbles) {
            double x = b.x * w + Math.sin(time * 2 + b.wobble) * 6;
            double y = b.y * h;
            int d = (int) b.size;
            g.setColor(new Color(200, 240, 255, 50));
            g.fillOval((int) x, (int) y, d, d);
            g.setColor(new Color(220, 250, 255, 140));
            g.setStroke(new BasicStroke(1.2f));
            g.drawOval((int) x, (int) y, d, d);
        }

        // 5. The title
        g.setFont(new Font("Segoe UI", Font.BOLD, 84));
        FontMetrics fm = g.getFontMetrics();
        String title = "Kelp";
        int tx = (w - fm.stringWidth(title)) / 2;
        int ty = h / 3;
        g.setColor(new Color(0, 0, 0, 90));
        g.drawString(title, tx + 3, ty + 4); // shadow
        g.setColor(new Color(0xE6FFF4));
        g.drawString(title, tx, ty);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        fm = g.getFontMetrics();
        String sub = "a launcher from the deep";
        g.setColor(new Color(0xA8DCCB));
        g.drawString(sub, (w - fm.stringWidth(sub)) / 2, ty + 38);

        g.dispose();
    }

    private void drawStalk(Graphics2D g, Stalk s, int w, int h) {
        double baseX = s.x * w;
        double height = s.height * h;
        Path2D path = new Path2D.Double();
        int segments = 24;
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments; // 0 at the seafloor, 1 at the tip
            double sway = Math.sin(time * 1.1 + s.phase + t * 3) * 22 * t; // tips sway more than roots
            double x = baseX + sway;
            double y = h - t * height;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        g.setColor(s.front ? KELP_FRONT : KELP_BACK);
        g.setStroke(new BasicStroke(s.front ? 12f : 8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
    }

    private static class Bubble {
        double x, y, size, speed, wobble;
    }

    private static class Stalk {
        double x, height, phase;
        boolean front;
    }
}
