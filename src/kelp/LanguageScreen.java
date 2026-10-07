package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;

/**
 * Every language Kelp speaks, each in its own words. Picking one switches Kelp right away. All but English are
 * marked BETA: an AI wrote them, and no native speaker has checked them yet.
 */
public class LanguageScreen extends Screen {
    private final Screen parent;
    private final McList<Lang.Language> list = new McList<>(220);
    private final McButton doneButton = new McButton(t("Done"), this::back);

    public LanguageScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        list.setItems(Lang.ALL);
        list.setSelected(Lang.current());
        buttons.add(doneButton);
    }

    private void back() {
        panel.setScreen(parent);
    }

    private void pick(Lang.Language language) {
        Settings.setLanguage(language.code());
        // Every screen is made again, so all their buttons are in the new language
        panel.setScreen(new LanguageScreen(panel, new SettingsScreen(panel, new TitleScreen(panel))));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Language: {0}", Lang.current().name()), w, 12 * GUI, 0xFFFFFF);
        list.draw(g, font, w, 32 * GUI, h - 48 * GUI, null, (gg, language, x, y, width) -> {
            font.draw(gg, language.name(), x, y, GUI, 0xFFFFFF);
            if (language.beta()) font.draw(gg, "BETA", x + width - font.width("BETA", GUI), y, GUI, 0xFFFF55);
        });
        if (Lang.current().beta()) {
            centered(g, "BETA: not checked by a native speaker yet.", w, h - 42 * GUI, 0xA0A0A0);
        }
        doneButton.setBounds(w / 2 - 100 * GUI, h - 28 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        list.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        Lang.Language picked = list.mousePressed(x, y);
        if (picked == null) {
            super.mousePressed(x, y);
        } else if (!picked.equals(Lang.current())) {
            pick(picked);
        }
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        list.mouseWheel(notches);
    }
}
