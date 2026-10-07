package kelp;

import static kelp.Lang.t;

import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * An instance's numbers: how long and how often it's been played, and Minecraft's own statistics added up over its
 * worlds (blocks mined, mobs, distance walked...), plus the player's Squid Count.
 */
public class StatsScreen extends Screen {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final Instance instance;
    private final McButton doneButton = new McButton(t("Done"), this::back);
    private volatile GameStats stats; // read in the background: big instances have many worlds
    private Instance shown;           // read again once a second, since play time is saved when the game closes
    private double shownAt = -1;
    private int worlds;
    private int squidCount;

    public StatsScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        buttons.add(doneButton);
        Thread reader = new Thread(() -> stats = GameStats.read(instance), "read stats");
        reader.setDaemon(true);
        reader.start();
    }

    private void back() {
        panel.setScreen(parent);
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Stats for {0}", instance.name()), w, 12 * GUI, 0xFFFFFF);
        if (shown == null || panel.getTime() - shownAt > 1) {
            Instance fresh = Instance.find(instance.id());
            shown = fresh != null ? fresh : instance;
            worlds = Worlds.list(Worlds.saves(instance)).size();
            squidCount = SquidCount.points(Accounts.active().id());
            shownAt = panel.getTime();
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {t("Time played"), GameStats.duration(shown.playMillis())});
        rows.add(new String[] {t("Times played"), String.valueOf(shown.timesPlayed())});
        rows.add(new String[] {t("Last played"), shown.lastPlayed() == 0 ? "-" : WHEN.format(Instant.ofEpochMilli(shown.lastPlayed()))});
        rows.add(new String[] {t("Worlds"), String.valueOf(worlds)});
        rows.add(new String[] {t("Squid Count"), String.valueOf(squidCount)});
        GameStats s = stats;
        if (s == null) {
            rows.add(new String[] {t("Reading the worlds..."), ""});
        } else {
            rows.add(new String[] {t("Time in worlds"), GameStats.duration(s.ticksPlayed() * 50)});
            rows.add(new String[] {t("Blocks mined"), String.format("%,d", s.blocksMined())});
            rows.add(new String[] {t("Items crafted"), String.format("%,d", s.itemsCrafted())});
            rows.add(new String[] {t("Mobs defeated"), String.format("%,d", s.mobsKilled())});
            rows.add(new String[] {t("Distance traveled"), String.format("%,.1f km", s.walkedCm() / 100_000.0)});
            rows.add(new String[] {t("Jumps"), String.format("%,d", s.jumps())});
            rows.add(new String[] {t("Deaths"), String.format("%,d", s.deaths())});
        }

        int left = w / 2 - 110 * GUI;
        int right = w / 2 + 110 * GUI;
        int y = 36 * GUI;
        for (String[] row : rows) {
            font.draw(g, row[0], left, y, GUI, 0xA0A0A0);
            font.draw(g, row[1], right - font.width(row[1], GUI), y, GUI, 0xFFFFFF);
            y += 13 * GUI;
        }
        doneButton.setBounds(w / 2 - 100 * GUI, h - 28 * GUI, 200 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }
}
