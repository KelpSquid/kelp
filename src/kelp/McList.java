package kelp;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * A Minecraft-style scrolling list: a dark box of rows with a scrollbar, where you can click a row to select it.
 * Screens say how to draw what's inside each row.
 */
public class McList<T> {
    /** Draws one row's contents. x and y are where its text should start. */
    public interface RowPainter<T> {
        void paint(Graphics2D g, T item, int x, int y, int width);
    }

    private static final int GUI = Screen.GUI;
    private static final int ROW = 14 * GUI;

    private final int rowWidth;
    private List<T> items = List.of();
    private T selected;
    private double scroll = 0; // how far the list is scrolled down, in screen pixels

    // Where the list was drawn last frame, so clicks can find the row under the mouse
    private int top;
    private int bottom;
    private int rowX;
    private int mouseX = -1;
    private int mouseY = -1;

    public McList(int rowWidth) {
        this.rowWidth = rowWidth * GUI;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
        if (selected != null && !items.contains(selected)) selected = null;
    }

    public T getSelected() {
        return selected;
    }

    public void setSelected(T item) {
        selected = item;
    }

    public void scrollToTop() {
        scroll = 0;
    }

    /** Draws the list between top and bottom. If message isn't null, it's shown in the box instead of rows. */
    public void draw(Graphics2D g, McFont font, int w, int top, int bottom, String message, RowPainter<T> painter) {
        this.top = top;
        this.bottom = bottom;
        rowX = (w - rowWidth) / 2;
        double maxScroll = maxScroll();
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int boxX = rowX - 4 * GUI;
        int boxW = rowWidth + 8 * GUI;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(boxX, top, boxW, bottom - top);

        if (message != null) {
            font.draw(g, message, (w - font.width(message, GUI)) / 2, (top + bottom) / 2 - 4 * GUI, GUI, 0xA0A0A0);
            return;
        }

        Graphics2D clip = (Graphics2D) g.create();
        clip.clipRect(boxX, top, boxW, bottom - top); // rows scrolled out of the box get cut off
        T hovered = itemAt(mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            int y = (int) (top + 2 * GUI + i * ROW - scroll);
            if (y + ROW < top || y > bottom) continue; // off screen, skip it
            T item = items.get(i);
            if (item.equals(selected)) {
                // Minecraft's selection look: a white outline around a black row
                clip.setColor(Color.WHITE);
                clip.fillRect(rowX, y, rowWidth, ROW - GUI);
                clip.setColor(Color.BLACK);
                clip.fillRect(rowX + GUI, y + GUI, rowWidth - 2 * GUI, ROW - 3 * GUI);
            } else if (item.equals(hovered)) {
                clip.setColor(new Color(255, 255, 255, 40));
                clip.fillRect(rowX, y, rowWidth, ROW - GUI);
            }
            painter.paint(clip, item, rowX + 4 * GUI, y + 3 * GUI, rowWidth - 8 * GUI);
        }
        clip.dispose();

        if (maxScroll > 0) {
            int x = boxX + boxW;
            int trackH = bottom - top;
            int thumbH = Math.max(16 * GUI, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = top + (int) ((trackH - thumbH) * scroll / maxScroll);
            int barW = 6 * GUI;
            g.setColor(Color.BLACK);
            g.fillRect(x, top, barW, trackH);
            g.setColor(new Color(0x808080));
            g.fillRect(x, thumbY, barW, thumbH);
            g.setColor(new Color(0xC0C0C0));
            g.fillRect(x, thumbY, barW - GUI, thumbH - GUI);
        }
    }

    private double maxScroll() {
        return Math.max(0, items.size() * ROW + 4 * GUI - (bottom - top));
    }

    /** The item under the point, or null. */
    public T itemAt(int x, int y) {
        if (x < rowX || x >= rowX + rowWidth || y < top || y >= bottom) return null;
        int row = (int) ((y - top - 2 * GUI + scroll) / ROW);
        return row >= 0 && row < items.size() ? items.get(row) : null;
    }

    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    /** Selects the row under the mouse. Returns the item clicked, or null if the click wasn't on a row. */
    public T mousePressed(int x, int y) {
        T item = itemAt(x, y);
        if (item != null) selected = item;
        return item;
    }

    public void mouseWheel(int notches) {
        scroll += notches * ROW * 3; // three rows per notch
    }
}
