package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Pick how Kelp looks. Clicking a theme switches to it right away, so the whole window is the preview. Make Your Own
 * opens the Theme Maker; Get Themes has the Store's.
 */
public class ThemeScreen extends Screen {
    private final Screen parent;
    private final McList<Theme> list = new McList<>(220);
    private final McButton makeButton = new McButton(t("Make Your Own"), () -> panel.setScreen(new ThemeMakerScreen(panel, this, null)));
    private final McButton getButton = new McButton(t("Get Themes"), () -> panel.setScreen(new ThemeStoreScreen(panel, this)));
    private final McButton editButton = new McButton(t("Edit"), this::edit);
    private final McButton deleteButton = new McButton(t("Delete"), this::delete);
    private final McButton doneButton = new McButton(t("Done"), this::done);

    public ThemeScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        for (McButton b : new McButton[] {makeButton, getButton, editButton, deleteButton, doneButton}) buttons.add(b);
    }

    @Override
    public void shown() {
        list.setItems(Theme.all());
        list.setSelected(Theme.current());
    }

    private void edit() {
        Theme theme = list.getSelected();
        if (theme != null && !theme.builtIn()) panel.setScreen(new ThemeMakerScreen(panel, this, theme));
    }

    private void delete() {
        Theme theme = list.getSelected();
        if (theme == null || theme.builtIn()) return;
        panel.setScreen(new ConfirmScreen(panel, t("Delete {0}?", theme.name()), t("It will be gone forever!"), () -> {
            try (Stream<java.nio.file.Path> walk = Files.walk(Theme.folder().resolve(theme.id()))) {
                for (java.nio.file.Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            } catch (IOException e) {
                System.err.println("Couldn't delete the theme: " + e.getMessage());
            }
            if (Theme.current().id().equals(theme.id())) Theme.use(Theme.OCEAN);
            panel.setScreen(this);
        }, () -> panel.setScreen(this)));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Theme: {0}", Theme.current().name()), w, 12 * GUI, 0xFFFFFF);
        list.draw(g, font, w, 32 * GUI, h - 84 * GUI, null, (gg, theme, x, y, width) -> {
            font.draw(gg, theme.name(), x, y, GUI, 0xFFFFFF);
            String kind = theme.builtIn() ? t(theme.scene().label) : t("Yours");
            font.draw(gg, kind, x + width - font.width(kind, GUI), y, GUI, 0xA0A0A0);
        });
        Theme selected = list.getSelected();
        boolean yours = selected != null && !selected.builtIn();
        editButton.setActive(yours);
        deleteButton.setActive(yours);
        int y = h - 76 * GUI;
        makeButton.setBounds(w / 2 - 100 * GUI, y, 98 * GUI, 20 * GUI);
        getButton.setBounds(w / 2 + 2 * GUI, y, 98 * GUI, 20 * GUI);
        editButton.setBounds(w / 2 - 100 * GUI, y + 24 * GUI, 98 * GUI, 20 * GUI);
        deleteButton.setBounds(w / 2 + 2 * GUI, y + 24 * GUI, 98 * GUI, 20 * GUI);
        doneButton.setBounds(w / 2 - 100 * GUI, y + 48 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        list.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        Theme picked = list.mousePressed(x, y);
        if (picked != null) Theme.use(picked); // the whole window shows it straight away
        else super.mousePressed(x, y);
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        list.mouseWheel(notches);
    }

    private void done() {
        panel.setScreen(parent);
    }
}
