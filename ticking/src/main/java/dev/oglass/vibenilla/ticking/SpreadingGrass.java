package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/**
 * Vanilla {@code SpreadingSnowyDirtBlock}: grass and mycelium spread to dirt and die in the dark.
 */
final class SpreadingGrass {
    private static final int MINIMUM_LIGHT = 9;
    private static final int SPREAD_ATTEMPTS = 4;

    private SpreadingGrass() {}

    static void register(RandomTicks ticks) {
        ticks.register(Block.GRASS_BLOCK, SpreadingGrass::tick);
        ticks.register(Block.MYCELIUM, SpreadingGrass::tick);
    }

    static void tick(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        if (!canBeGrass(instance, position)) {
            Level.setAndUpdate(instance, position, Block.DIRT);
            return;
        }

        if (Level.brightness(instance, position.blockX(), position.blockY() + 1, position.blockZ())
                < MINIMUM_LIGHT) {
            return;
        }

        for (var attempt = 0; attempt < SPREAD_ATTEMPTS; attempt++) {
            var target =
                    position.add(
                            random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
            if (Level.block(instance, target).compare(Block.DIRT)
                    && canPropagate(instance, target)) {
                var snowy =
                        Level.hasTag(Level.block(instance, target.add(0, 1, 0)), "minecraft:snow");
                Level.setAndUpdate(
                        instance,
                        target,
                        block.defaultState().withProperty("snowy", String.valueOf(snowy)));
            }
        }
    }

    private static boolean canBeGrass(Instance instance, BlockVec position) {
        var above = Level.block(instance, position.add(0, 1, 0));
        if (above.compare(Block.SNOW) && Level.property(above, "layers") == 1) {
            return true;
        }
        return !Level.isFullFluid(above)
                && above.lightBlocked() < 15
                && !(above.occludes() && above.occlusionShape().isFaceFull(BlockFace.BOTTOM));
    }

    private static boolean canPropagate(Instance instance, BlockVec position) {
        var above = Level.block(instance, position.add(0, 1, 0));
        return canBeGrass(instance, position) && !Level.containsWater(above);
    }
}
