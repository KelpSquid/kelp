package kelp;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A slider for settings like volume: a dark bar with a handle you drag. Click anywhere on it to jump there. */
public class McSlider {

    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> label;
    private final DoubleConsumer onChange;
    private final Rectangle bounds = new Rectangle();
    private double value;
    private boolean hovered;
    private boolean dragging;

    /** value moves from min to max in steps of step. label turns a value into the text shown on the slider. */
    public McSlider(double min, double max, double step, double value, DoubleFunction<String> label, DoubleConsumer onChange) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
        this.label = label;
        this.onChange = onChange;
    }

    public void setBounds(int x, int y, int width, int height) {
        bounds.setBounds(x, y, width, height);
    }

    public void mouseMoved(int x, int y) {
        hovered = bounds.contains(x, y);
    }

    public boolean mousePressed(int x, int y) {
        if (!bounds.contains(x, y)) return false;
        dragging = true;
        slideTo(x);
        return true;
    }

    public void mouseDragged(int x, int y) {
        if (dragging) slideTo(x);
    }

    public void mouseReleased() {
        dragging = false;
    }

    private void slideTo(int mouseX) {
        int handleW = bounds.height * 2 / 5; // the handle is 8 wide on a 20 tall slider
        double k = (mouseX - bounds.x - handleW / 2.0) / (bounds.width - handleW);
        double picked = min + Math.max(0, Math.min(1, k)) * (max - min);
        picked = min + Math.round((picked - min) / step) * step; // snap to a step
        picked = Math.max(min, Math.min(max, picked));
        if (picked != value) {
            value = picked;
            onChange.accept(value);
        }
    }

    public void draw(Graphics2D g, McFont font, int scale) {
        int x = bounds.x;
        int y = bounds.y;
        int w = bounds.width;
        int h = bounds.height;
        BufferedImage[] look = McButton.textures(Theme.current().buttons()); // the same colors as the theme's buttons
        BufferedImage track = look[2];
        BufferedImage handle = look[0];
        BufferedImage handleLit = look[1];
        // The dark track, split in two halves like a button so both rounded ends show
        int left = w / 2;
        g.drawImage(track, x, y, x + left, y + h, 0, 0, left / scale, 20, null);
        g.drawImage(track, x + left, y, x + w, y + h, 200 - (w - left) / scale, 0, 200, 20, null);

        // The handle: the two ends of the button texture squeezed into 8 pixels
        BufferedImage knob = hovered || dragging ? handleLit : handle;
        int handleW = 8 * scale;
        int hx = x + (int) Math.round((value - min) / (max - min) * (w - handleW));
        g.drawImage(knob, hx, y, hx + handleW / 2, y + h, 0, 0, 4, 20, null);
        g.drawImage(knob, hx + handleW / 2, y, hx + handleW, y + h, 196, 0, 200, 20, null);

        String text = label.apply(value);
        int color = hovered || dragging ? 0xFFFFA0 : 0xE0E0E0;
        font.draw(g, text, x + (w - font.width(text, scale)) / 2, y + (h - 8 * scale) / 2, scale, color);
    }
}
