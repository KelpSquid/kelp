package kelp;

/**
 * What runs the game in an instance. Vanilla and Squid are the ones Kelp recommends: Vanilla because there's
 * nothing to break, and Squid because it's made for Kelp. The others work too, for people who want their mods.
 */
public enum Loader {
    VANILLA("Vanilla", true, true, "Plain Minecraft. Nothing to break."),
    SQUID("Squid", true, true, "Made for Kelp: easy mods that just work."),
    SHADERS("Shaders", false, true, "Fabric with Sodium and Iris, for shader packs."),
    FABRIC("Fabric", false, true, "Runs Fabric mods. Less reliable than Squid."),
    QUILT("Quilt", false, true, "Runs Quilt and most Fabric mods. Less reliable."),
    NEOFORGE("NeoForge", false, true, "Runs NeoForge mods. Less reliable than Squid."),
    FORGE("Forge", false, true, "Runs Forge mods. Less reliable than Squid.");

    private final String label;
    private final boolean recommended;
    private final boolean ready;
    private final String about;

    Loader(String label, boolean recommended, boolean ready, String about) {
        this.label = label;
        this.recommended = recommended;
        this.ready = ready;
        this.about = about;
    }

    public String label() {
        return label;
    }

    /** Vanilla and Squid. */
    public boolean recommended() {
        return recommended;
    }

    /** Whether Kelp can install and start it yet. */
    public boolean ready() {
        return ready;
    }

    /** One short line saying what it's for. */
    public String about() {
        return about;
    }

    /** The next one in the list, for a button that goes through them. */
    public Loader next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** The next one Kelp can already run, for changing an instance that exists. */
    public Loader nextReady() {
        Loader next = next();
        while (!next.ready()) next = next.next();
        return next;
    }

    /** What an instance list shows after the version, like " + Squid". Nothing for Vanilla. */
    public String suffix() {
        return this == VANILLA ? "" : " + " + label;
    }

    /** What actually starts the game. Shaders is Fabric with two mods already in it. */
    public Loader runtime() {
        return this == SHADERS ? FABRIC : this;
    }

    /** Whether a mod made for this kind of loader runs on this one. Quilt runs most Fabric mods too. */
    public boolean runs(Loader modKind) {
        Loader runtime = runtime();
        return modKind == runtime || runtime == QUILT && modKind == FABRIC;
    }

    /** Reads a saved name, like "FABRIC". Unknown names are Vanilla, the safe choice. */
    public static Loader parse(String name) {
        if (name != null) {
            for (Loader loader : values()) {
                if (loader.name().equalsIgnoreCase(name)) return loader;
            }
        }
        return VANILLA;
    }
}
