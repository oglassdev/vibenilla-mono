package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** Vanilla {@code NetherWartBlock}, {@code CocoaBlock} and {@code SweetBerryBushBlock} growth. */
final class AgingPlants {
    private static final int BERRY_MINIMUM_LIGHT = 9;

    private AgingPlants() {}

    static void register(RandomTicks ticks) {
        ticks.register(
                Block.NETHER_WART,
                (instance, position, block, random) ->
                        age(instance, position, block, 3, 10, random));
        ticks.register(
                Block.COCOA,
                (instance, position, block, random) ->
                        age(instance, position, block, 2, 5, random));
        ticks.register(Block.SWEET_BERRY_BUSH, AgingPlants::berryBush);
    }

    private static void berryBush(
            Instance instance, BlockVec position, Block block, RandomGenerator random) {
        if (Level.rawBrightness(
                        instance, position.blockX(), position.blockY() + 1, position.blockZ())
                >= BERRY_MINIMUM_LIGHT) {
            age(instance, position, block, 3, 5, random);
        }
    }

    /** Ages the plant up to {@code maxAge} with the chance of one in {@code odds}. */
    static void age(
            Instance instance,
            BlockVec position,
            Block block,
            int maxAge,
            int odds,
            RandomGenerator random) {
        var age = Level.property(block, "age");
        if (age < maxAge && random.nextInt(odds) == 0) {
            Level.set(instance, position, Level.withProperty(block, "age", age + 1));
        }
    }
}
