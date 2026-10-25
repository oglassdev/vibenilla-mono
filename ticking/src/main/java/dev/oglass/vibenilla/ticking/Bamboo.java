package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** Vanilla {@code BambooSaplingBlock} and {@code BambooStalkBlock} growth. */
final class Bamboo {
    private static final int MINIMUM_LIGHT = 9;
    private static final int MAX_HEIGHT = 16;
    private static final int LEAFY_HEIGHT = 11;
    private static final int LAST_HEIGHT = 15;
    private static final float TALL_STOP_ODDS = 0.25F;

    private Bamboo() {}

    static void register(RandomTicks ticks) {
        ticks.register(Block.BAMBOO_SAPLING, Bamboo::sapling);
        ticks.register(
                block -> block.compare(Block.BAMBOO) && "0".equals(block.getProperty("stage")),
                Bamboo::stalk);
    }

    static void sapling(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        if (random.nextInt(3) == 0 && canGrowAbove(instance, position)) {
            Level.set(instance, position, Block.BAMBOO);
            Level.setAndUpdate(
                    instance, position.add(0, 1, 0), Block.BAMBOO.withProperty("leaves", "small"));
        }
    }

    static void stalk(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        if (random.nextInt(3) != 0 || !canGrowAbove(instance, position)) {
            return;
        }

        var height = heightBelow(instance, position) + 1;
        if (height < MAX_HEIGHT) {
            grow(instance, position, block, height, random);
        }
    }

    private static boolean canGrowAbove(Instance instance, BlockVec position) {
        var above = position.add(0, 1, 0);
        return Level.block(instance, above).air()
                && Level.rawBrightness(instance, above.blockX(), above.blockY(), above.blockZ())
                        >= MINIMUM_LIGHT;
    }

    /** The number of stalks directly below, up to the maximum height. */
    private static int heightBelow(Instance instance, BlockVec position) {
        var height = 0;
        while (height < MAX_HEIGHT
                && Level.block(instance, position.add(0, -(height + 1), 0)).compare(Block.BAMBOO)) {
            height++;
        }
        return height;
    }

    private static void grow(
            Instance instance, BlockVec position, Block block, int height, RandomGenerator random) {
        var belowPosition = position.add(0, -1, 0);
        var belowBelowPosition = position.add(0, -2, 0);
        var below = Level.block(instance, belowPosition);
        var belowBelow = Level.block(instance, belowBelowPosition);
        var belowBelowIsBamboo = belowBelow.compare(Block.BAMBOO);

        var leaves = "small";
        if (below.compare(Block.BAMBOO) && !"none".equals(below.getProperty("leaves"))) {
            leaves = "large";
            if (belowBelowIsBamboo) {
                Level.setAndUpdate(instance, belowPosition, below.withProperty("leaves", "small"));
                Level.setAndUpdate(
                        instance, belowBelowPosition, belowBelow.withProperty("leaves", "none"));
            }
        }

        var age = "1".equals(block.getProperty("age")) || belowBelowIsBamboo ? 1 : 0;
        var last =
                height == LAST_HEIGHT
                        || (height >= LEAFY_HEIGHT && random.nextFloat() < TALL_STOP_ODDS);
        Level.setAndUpdate(
                instance,
                position.add(0, 1, 0),
                Block.BAMBOO
                        .withProperty("age", Integer.toString(age))
                        .withProperty("leaves", leaves)
                        .withProperty("stage", last ? "1" : "0"));
        if (age == 1 && Level.property(block, "age") == 0) {
            Level.set(instance, position, Level.withProperty(block, "age", 1));
        }
    }
}
