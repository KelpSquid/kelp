package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The first time Kelp opens, a few friendly steps instead of a wall of buttons: pick your language, say who's playing,
 * make your first Minecraft (with Squid, ready for mods), pick a look, and play. Every step can be skipped, and it
 * never shows again once it's done (or for anyone who already has an instance).
 */
public class WelcomeScreen extends Screen {
    /** The Minecraft version Squid is made for, which the first instance uses when Mojang's list has it. */
    static final String SQUID_VERSION = "26.3";

    private enum Step { HELLO, ACCOUNT, INSTANCE, THEME, READY }

    private Step step = Step.HELLO;
    private final McButton nextButton = new McButton(t("Next"), this::next);
    private final McButton backButton = new McButton(t("Back"), this::back);
    private final McButton skipButton = new McButton(t("Skip"), this::finish);
    private final McButton languageButton = new McButton("", () -> panel.setScreen(new LanguageScreen(panel, this, () -> remade(Step.HELLO))));
    private final McButton microsoftButton = new McButton(t("Sign in with Microsoft"), () -> panel.setScreen(new SignInScreen(panel, this)));
    private final McButton nameButton = new McButton(t("Pick a Name"), () -> panel.setScreen(new AddOfflineScreen(panel, this)));
    private final McButton makeButton = new McButton("", this::makeInstance);
    private final McButton otherButton = new McButton(t("Pick Another Version..."), this::pickVersion);
    private final McButton[] themeButtons = new McButton[Theme.BUILT_IN.size()];
    private final McButton playButton = new McButton(t("Let's Play!"), this::finish);
    private volatile List<VersionManifest.Version> versions;
    private volatile String versionsProblem;
    private Instance made;
    private String problem;

    public WelcomeScreen(OceanPanel panel) {
        super(panel);
        for (McButton b : new McButton[] {nextButton, backButton, skipButton, languageButton, microsoftButton, nameButton, makeButton, otherButton, playButton}) {
            buttons.add(b);
        }
        for (int i = 0; i < themeButtons.length; i++) {
            Theme theme = Theme.BUILT_IN.get(i);
            themeButtons[i] = new McButton(theme.label(), () -> Theme.use(theme));
            buttons.add(themeButtons[i]);
        }
        Thread loader = new Thread(() -> {
            try {
                versions = VersionManifest.download();
            } catch (Exception e) {
                versionsProblem = t("Couldn't get Minecraft's version list. Check your internet, or skip this for now.");
            }
        }, "welcome versions");
        loader.setDaemon(true);
        loader.start();
    }

    /** Whether to welcome someone: the first time Kelp opens, if they don't have any instances yet. */
    public static boolean needed() {
        return !"true".equals(Settings.get("welcomed", "false")) && Instance.all().isEmpty();
    }

    private WelcomeScreen remade(Step at) {
        WelcomeScreen again = new WelcomeScreen(panel); // made again, so every button is in the new language
        again.step = at;
        return again;
    }

    private void next() {
        problem = null;
        step = Step.values()[Math.min(Step.values().length - 1, step.ordinal() + 1)];
    }

    private void back() {
        problem = null;
        step = Step.values()[Math.max(0, step.ordinal() - 1)];
    }

    /** Done (or skipped): never again, and on to the title screen. */
    private void finish() {
        Settings.put("welcomed", "true");
        panel.setScreen(new TitleScreen(panel));
    }

    /** The version Squid is made for, or Mojang's newest release if the list doesn't have it. */
    VersionManifest.Version bestVersion() {
        List<VersionManifest.Version> all = versions;
        if (all == null) return null;
        for (VersionManifest.Version v : all) {
            if (v.id().equals(SQUID_VERSION)) return v;
        }
        for (VersionManifest.Version v : all) {
            if (v.type().equals("release")) return v;
        }
        return null;
    }

    private void makeInstance() {
        VersionManifest.Version version = bestVersion();
        if (version == null) return;
        try {
            Loader loader = version.id().equals(SQUID_VERSION) ? Loader.SQUID : Loader.VANILLA;
            made = Instance.create(t("My First World"), version, loader);
            Settings.setDefaultInstance(made.folder().getFileName().toString());
            next();
        } catch (java.io.IOException e) {
            problem = t("Couldn't make it: {0}", e.getMessage());
        }
    }

    private void pickVersion() {
        panel.setScreen(new VersionScreen(panel, this, bestVersion(), picked -> {
            try {
                made = Instance.create("Minecraft " + picked.id(), picked, picked.id().equals(SQUID_VERSION) ? Loader.SQUID : Loader.VANILLA);
                Settings.setDefaultInstance(made.folder().getFileName().toString());
                step = Step.THEME;
            } catch (java.io.IOException e) {
                problem = t("Couldn't make it: {0}", e.getMessage());
            }
        }));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        for (McButton b : buttons) b.setBounds(-1000, -1000, 0, 0);
        int left = w / 2 - 100 * GUI;
        int top = h / 4;
        int bottom = h - 28 * GUI;
        // Little dots along the top showing how far along you are
        int steps = Step.values().length;
        for (int i = 0; i < steps; i++) {
            g.setColor(new java.awt.Color(i <= step.ordinal() ? 0x55FF55 : 0x505050));
            g.fillRect(w / 2 - steps * 6 * GUI + i * 12 * GUI + 3 * GUI, 10 * GUI, 6 * GUI, 6 * GUI);
        }
        skipButton.setBounds(w - 64 * GUI, 4 * GUI, 60 * GUI, 20 * GUI);
        switch (step) {
            case HELLO -> {
                BufferedImage logo = Textures.readBundled("branding/kelp.png");
                int size = 64 * GUI;
                g.drawImage(logo, (w - size) / 2, top - 30 * GUI, size, size, null);
                centered(g, t("Welcome to Kelp!"), w, top + 42 * GUI, 0xFFFFFF);
                centered(g, t("Kelp starts Minecraft for you, and makes mods easy."), w, top + 56 * GUI, 0xA0A0A0);
                languageButton.setLabel(t("Language: {0}", Lang.current().name()));
                languageButton.setBounds(left, top + 74 * GUI, 200 * GUI, 20 * GUI);
                nextButton.setBounds(left, bottom, 200 * GUI, 20 * GUI);
            }
            case ACCOUNT -> {
                centered(g, t("Who's playing?"), w, top, 0xFFFFFF);
                Account account = Accounts.active();
                if (account.microsoft()) {
                    centered(g, t("You're signed in as {0}.", account.name()), w, top + 16 * GUI, 0x55FF55);
                } else if (MicrosoftLogin.ready()) {
                    centered(g, t("Sign in with the Microsoft account that owns Minecraft."), w, top + 16 * GUI, 0xA0A0A0);
                    microsoftButton.setBounds(left, top + 34 * GUI, 200 * GUI, 20 * GUI);
                } else {
                    centered(g, t("You're playing as {0}. Pick your own name if you like.", account.name()), w, top + 16 * GUI, 0xA0A0A0);
                    nameButton.setBounds(left, top + 34 * GUI, 200 * GUI, 20 * GUI);
                }
                navigation(w, bottom);
            }
            case INSTANCE -> {
                centered(g, t("Let's make your Minecraft!"), w, top, 0xFFFFFF);
                centered(g, t("An instance is your own copy of Minecraft, with its own worlds and mods."), w, top + 16 * GUI, 0xA0A0A0);
                VersionManifest.Version best = bestVersion();
                if (best != null) {
                    boolean squid = best.id().equals(SQUID_VERSION);
                    makeButton.setLabel(squid ? t("Minecraft {0} with Squid (best)", best.id()) : t("Minecraft {0}", best.id()));
                    makeButton.setBounds(left, top + 34 * GUI, 200 * GUI, 20 * GUI);
                    otherButton.setBounds(left, top + 58 * GUI, 200 * GUI, 20 * GUI);
                    if (squid) centered(g, t("Squid lets you add mods from the Store, or make your own."), w, top + 84 * GUI, 0x55FF55);
                } else {
                    centered(g, versionsProblem != null ? versionsProblem : t("Getting Minecraft's version list..."), w, top + 40 * GUI,
                            versionsProblem != null ? 0xFF5555 : 0xA0A0A0);
                }
                navigation(w, bottom);
            }
            case THEME -> {
                centered(g, t("Pick a look for Kelp"), w, top, 0xFFFFFF);
                centered(g, t("You can change it, or make your own, in Options > Theme."), w, top + 16 * GUI, 0xA0A0A0);
                for (int i = 0; i < themeButtons.length; i++) {
                    themeButtons[i].setLabel((Theme.current().id().equals(Theme.BUILT_IN.get(i).id()) ? "> " : "") + Theme.BUILT_IN.get(i).label());
                    themeButtons[i].setBounds(left + (i % 2) * 102 * GUI, top + 34 * GUI + (i / 2) * 24 * GUI, 98 * GUI, 20 * GUI);
                }
                navigation(w, bottom);
            }
            case READY -> {
                centered(g, t("You're all set!"), w, top, 0xFFFFFF);
                centered(g, made != null ? t("Press Play to start {0}. The first time takes a minute to download.", made.name())
                        : t("Make an instance in Instances whenever you're ready."), w, top + 16 * GUI, 0xA0A0A0);
                centered(g, t("Have fun!"), w, top + 30 * GUI, 0x55FF55);
                playButton.setBounds(left, bottom - 24 * GUI, 200 * GUI, 20 * GUI);
                backButton.setBounds(left, bottom, 200 * GUI, 20 * GUI);
            }
        }
        if (problem != null) centered(g, problem, w, bottom - 14 * GUI, 0xFF5555);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    private void navigation(int w, int y) {
        backButton.setBounds(w / 2 - 100 * GUI, y, 98 * GUI, 20 * GUI);
        nextButton.setBounds(w / 2 + 2 * GUI, y, 98 * GUI, 20 * GUI);
    }
}
