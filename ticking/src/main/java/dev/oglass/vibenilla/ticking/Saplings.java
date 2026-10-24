package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla {@code SaplingBlock}: the first growth step advances the stage, the second grows a tree.
 */
final class Saplings {
    private static final int MINIMUM_LIGHT = 9;
    private static final int GROWTH_ODDS = 7;

    private Saplings() {}

    static void register(RandomTicks ticks, @Nullable WorldgenTrees trees) {
        ticks.register(
                Saplings::isSapling,
                (instance, position, block, random) ->
                        tick(instance, position, block, trees, random));
    }

    private static boolean isSapling(Block block) {
        return block.key().value().endsWith("_sapling") && block.properties().containsKey("stage");
    }

    private static void tick(
            Instance instance,
            BlockVec position,
            Block block,
            @Nullable WorldgenTrees trees,
            RandomGenerator random) {
        if (Level.brightness(instance, position.blockX(), position.blockY() + 1, position.blockZ())
                        < MINIMUM_LIGHT
                || random.nextInt(GROWTH_ODDS) != 0) {
            return;
        }

        if (Level.property(block, "stage") == 0) {
            Level.set(instance, position, Level.withProperty(block, "stage", 1));
        } else if (trees != null) {
            trees.grow(instance, position, block, random);
        }
    }
}
