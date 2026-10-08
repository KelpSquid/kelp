package kelp;

import java.util.List;

/**
 * Starter mods for New Mod: instead of a blank mod, start from one that already does something fun, then change it.
 * Each one is short, explains itself in comments, and has a number or two at the top that are fun to change.
 *
 * In the code, %1$s is the mod's name and %2$s its class name. Some use commands, which need cheats on in that world;
 * the comment at the top says so.
 */
public final class ModStarters {
    private ModStarters() {
    }

    /** A starter: its name in New Mod, a line about it, and its code. */
    public record Starter(String name, String about, String code) {
        /** The code for a mod with this name and class name. */
        public String codeFor(String modName, String className) {
            return code.formatted(modName.replace("\\", "").replace("\"", "'"), className);
        }
    }

    public static final List<Starter> ALL = List.of(
            new Starter("Rocket Boots", "Press R to blast into the sky.", """
                    // %1$s: press R to blast into the sky!
                    // Try changing POWER: 0.5 is a hop, 3 is the clouds. (Careful: falling still hurts.)

                    import net.minecraft.client.Minecraft;

                    public class %2$s extends EasyMod {
                        double POWER = 1.2;

                        void start() {
                            onKey("R", () -> {
                                var player = Minecraft.getInstance().player;
                                if (player == null) return;
                                // Keep going the same way sideways, and shoot up
                                player.setDeltaMovement(player.getDeltaMovement().x, POWER, player.getDeltaMovement().z);
                                playSound("entity.firework_rocket.launch");
                                showText("Whoosh!");
                            });
                        }
                    }
                    """),
            new Starter("Creeper Alarm", "Warns you when a creeper sneaks up.", """
                    // %1$s: warns you when a creeper sneaks up on you.
                    // Try changing DISTANCE (in blocks), or the sound. Sound names are in the /playsound command.

                    import net.minecraft.client.Minecraft;
                    import net.minecraft.world.entity.monster.Creeper;

                    public class %2$s extends EasyMod {
                        int DISTANCE = 12;
                        boolean warned;

                        void start() {
                            every(0.5, () -> {
                                var game = Minecraft.getInstance();
                                if (game.player == null || game.level == null) return;
                                // Every creeper in a box around you
                                var creepers = game.level.getEntitiesOfClass(Creeper.class, game.player.getBoundingBox().inflate(DISTANCE));
                                if (creepers.isEmpty()) {
                                    warned = false;
                                    return;
                                }
                                int closest = (int) creepers.stream().mapToDouble(c -> c.distanceTo(game.player)).min().orElse(0);
                                showText("Creeper " + closest + " blocks away!");
                                if (!warned) {
                                    playSound("block.note_block.bell");
                                    warned = true; // the bell rings once, not every half second
                                }
                            });
                        }
                    }
                    """),
            new Starter("Day Night Switch", "Press N to flip between day and night (cheats on).", """
                    // %1$s: press N to flip between day and night.
                    // It uses the /time command, so cheats need to be on in that world.

                    public class %2$s extends EasyMod {
                        boolean night;

                        void start() {
                            onKey("N", () -> {
                                night = !night;
                                command(night ? "time set night" : "time set day");
                                showText(night ? "Goodnight!" : "Good morning!");
                            });
                        }
                    }
                    """),
            new Starter("Where Am I", "Always shows where you are, above the hotbar.", """
                    // %1$s: always shows where you are, just above your hotbar.
                    // Try adding your health: + "  Health " + health()

                    public class %2$s extends EasyMod {
                        void start() {
                            every(0.25, () -> {
                                if (inWorld()) showText("X " + x() + "   Y " + y() + "   Z " + z());
                            });
                        }
                    }
                    """),
            new Starter("Health Alarm", "Beeps when your health gets low.", """
                    // %1$s: beeps when your health gets low.
                    // Health goes from 0 to 20 (each heart is 2). Try changing LOW.

                    public class %2$s extends EasyMod {
                        int LOW = 6;

                        void start() {
                            every(1, () -> {
                                if (inWorld() && health() > 0 && health() <= LOW) {
                                    showText("Low health! Eat something!");
                                    playSound("block.note_block.bass");
                                }
                            });
                        }
                    }
                    """),
            new Starter("Lucky Button", "Press G for a random gift (cheats on).", """
                    // %1$s: press G for a random gift!
                    // It uses the /give command, so cheats need to be on. Add your own gifts to the list.

                    public class %2$s extends EasyMod {
                        String[] GIFTS = {"diamond", "golden_apple", "cake", "ender_pearl", "cookie", "emerald", "firework_rocket"};

                        void start() {
                            onKey("G", () -> {
                                String gift = GIFTS[random(0, GIFTS.length - 1)];
                                giveItem(gift);
                                say("Lucky! You got " + gift.replace('_', ' ') + "!");
                                playSound("entity.player.levelup");
                            });
                        }
                    }
                    """));
}
