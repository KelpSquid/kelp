package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;

/** Asks "are you sure?" before something that can't be undone, like Minecraft does before deleting a world. */
public class ConfirmScreen extends Screen {
    private final String title;
    private final String message;

    public ConfirmScreen(OceanPanel panel, String title, String message, Runnable yes, Runnable no) {
        super(panel);
        this.title = title;
        this.message = message;
        buttons.add(new McButton(t("Yes"), yes));
        buttons.add(new McButton(t("No"), no));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        int centerY = h / 2;
        centered(g, title, w, centerY - 40 * GUI, 0xFFFFFF);
        centered(g, message, w, centerY - 24 * GUI, 0xA0A0A0);
        buttons.get(0).setBounds(w / 2 - 100 * GUI, centerY, 98 * GUI, 20 * GUI);
        buttons.get(1).setBounds(w / 2 + 2 * GUI, centerY, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, panel.getMcFont(), GUI);
    }
}
