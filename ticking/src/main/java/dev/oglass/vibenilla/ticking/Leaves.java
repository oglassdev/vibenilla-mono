package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/**
 * Vanilla {@code LeavesBlock} decay. The {@code distance} property is kept up to date by the leaves
 * placement rule, so only leaves that are not persistent and are cut off from every log (distance
 * 7) decay.
 */
final class Leaves {
    private static final int DECAYING_DISTANCE = 7;

    private Leaves() {}

    static void register(RandomTicks ticks) {
        ticks.register(Leaves::isDecaying, Leaves::decay);
    }

    static boolean isDecaying(Block block) {
        return Level.hasTag(block, "minecraft:leaves")
                && "false".equals(block.getProperty("persistent"))
                && Level.property(block, "distance") == DECAYING_DISTANCE;
    }

    static void decay(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        var event = new LeavesDecayEvent(instance, position, block);
        EventDispatcher.call(event);
        if (event.isCancelled()) {
            return;
        }

        var waterlogged = "true".equals(block.getProperty("waterlogged"));
        Level.setAndUpdate(instance, position, waterlogged ? Block.WATER : Block.AIR);
    }
}
