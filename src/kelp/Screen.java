package kelp;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** One page of Kelp, like the title screen or the version list. The ocean is always drawn behind it. */
public abstract class Screen {
    protected static final int GUI = 2; // everything on screens is drawn at 2x, like Minecraft's GUI scale

    protected final OceanPanel panel;
    protected final List<McButton> buttons = new ArrayList<>();

    protected Screen(OceanPanel panel) {
        this.panel = panel;
    }

    /** Draws the screen. w and h are the window's inside size in screen pixels. */
    public abstract void draw(Graphics2D g, int w, int h);

    /** Called every time the screen appears, including when coming back to it. */
    public void shown() {
    }

    public void mouseMoved(int x, int y) {
        for (McButton b : buttons) b.setHovered(b.contains(x, y));
    }

    public void mousePressed(int x, int y) {
        for (McButton b : buttons) {
            if (b.contains(x, y)) {
                b.click();
                return; // the click might have switched screens, so stop here
            }
        }
    }

    /** notches is positive when scrolling down and negative when scrolling up. */
    public void mouseWheel(int x, int y, int notches) {
    }

    /** A letter, number or symbol was typed. */
    public void keyTyped(char c) {
    }

    /** A key like Backspace was pressed. keyCode is one of KeyEvent's VK_ codes. */
    public void keyPressed(int keyCode, boolean ctrl) {
    }

    /** Draws text centered across the window, at GUI size. */
    protected void centered(Graphics2D g, String text, int w, int y, int rgb) {
        McFont font = panel.getMcFont();
        font.draw(g, text, (w - font.width(text, GUI)) / 2, y, GUI, rgb);
    }
}
