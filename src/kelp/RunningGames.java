package kelp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The games Kelp started that are still open, so one instance can't be started twice at the same time. */
public final class RunningGames {
    private static final Map<String, Process> GAMES = new ConcurrentHashMap<>();

    private RunningGames() {
    }

    /** Remembers a game Kelp just started. It's forgotten again as soon as the game closes. */
    public static void add(Instance instance, Process game) {
        GAMES.put(instance.id(), game);
        Backups.watch(instance, game); // worlds get backed up while it's played
        long started = System.currentTimeMillis();
        game.onExit().thenRun(() -> {
            try {
                instance.addPlayTime(System.currentTimeMillis() - started);
            } catch (java.io.IOException e) {
                System.err.println("Couldn't save the play time: " + e.getMessage());
            }
        });
        game.onExit().thenRun(() -> GAMES.remove(instance.id(), game));
    }

    /** The instance's game if it's open right now, or null. */
    public static Process get(Instance instance) {
        Process game = GAMES.get(instance.id());
        return game != null && game.isAlive() ? game : null;
    }

    /** Whether any game Kelp started is still open. */
    public static boolean any() {
        return GAMES.values().stream().anyMatch(Process::isAlive);
    }

    public static boolean isRunning(Instance instance) {
        return get(instance) != null;
    }
}
