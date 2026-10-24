package dev.oglass.vibenilla.server;

import java.util.concurrent.ThreadLocalRandom;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ExperienceOrb;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PickupExperienceEvent;

/** Experience points: the level curve, collecting orbs, and dropping them on death. */
final class Experience {
    private static final int[] ORB_VALUES = {2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1};
    private static final int MAX_DEATH_DROP = 100;

    private Experience() {}

    record Progress(int level, float progress) {}

    static EventNode<Event> events() {
        return EventNode.all("experience")
                .addListener(
                        PickupExperienceEvent.class,
                        event -> give(event.getPlayer(), event.getExperienceCount()));
    }

    static int pointsToNextLevel(int level) {
        if (level < 16) {
            return 2 * level + 7;
        }
        if (level < 31) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    static Progress add(int level, float progress, int points) {
        progress += points / (float) pointsToNextLevel(level);
        while (progress >= 1.0F) {
            progress = (progress - 1.0F) * pointsToNextLevel(level);
            level++;
            progress /= pointsToNextLevel(level);
        }
        return new Progress(level, progress);
    }

    static int deathReward(int level) {
        return Math.min(level * 7, MAX_DEATH_DROP);
    }

    static void give(Player player, int points) {
        var result = add(player.getLevel(), player.getExp(), points);
        player.setLevel(result.level());
        player.setExp(result.progress());
    }

    /** Spawns the player's death reward as orbs and resets their level and progress. */
    static void dropAndReset(Player player, Point position) {
        var remaining = deathReward(player.getLevel());
        var random = ThreadLocalRandom.current();
        for (var value : ORB_VALUES) {
            while (remaining >= value) {
                remaining -= value;
                var orb = new ExperienceOrb((short) value);
                orb.setInstance(player.getInstance(), position);
                orb.setVelocity(
                        new Vec(
                                random.nextDouble(-1, 1),
                                random.nextDouble(1, 3),
                                random.nextDouble(-1, 1)));
            }
        }
        player.setLevel(0);
        player.setExp(0.0F);
    }
}
