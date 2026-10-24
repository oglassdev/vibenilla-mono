package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** Vanilla {@code FarmBlock} moisture. */
final class Farmland {
    private static final int MAX_MOISTURE = 7;
    private static final int WATER_RADIUS = 4;

    private Farmland() {}

    static void register(RandomTicks ticks) {
        ticks.register(Block.FARMLAND, Farmland::tick);
    }

    static void tick(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        var moisture = Level.property(block, "moisture");
        var above = position.add(0, 1, 0);
        if (!isNearWater(instance, position) && !Level.isRainingAt(instance, above)) {
            if (moisture > 0) {
                Level.set(instance, position, Level.withProperty(block, "moisture", moisture - 1));
            } else if (!Level.hasTag(
                    Level.block(instance, above), "minecraft:maintains_farmland")) {
                Level.setAndUpdate(instance, position, Block.DIRT);
            }
        } else if (moisture < MAX_MOISTURE) {
            Level.set(instance, position, Level.withProperty(block, "moisture", MAX_MOISTURE));
        }
    }

    private static boolean isNearWater(Instance instance, BlockVec position) {
        for (var x = -WATER_RADIUS; x <= WATER_RADIUS; x++) {
            for (var y = 0; y <= 1; y++) {
                for (var z = -WATER_RADIUS; z <= WATER_RADIUS; z++) {
                    if (Level.containsWater(Level.block(instance, position.add(x, y, z)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
