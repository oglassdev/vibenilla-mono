package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** Vanilla {@code CropBlock} growth. */
final class Crops {
    private static final int MINIMUM_LIGHT = 9;

    private Crops() {}

    static void register(RandomTicks ticks) {
        ticks.register(
                Block.WHEAT,
                (instance, position, block, random) -> grow(instance, position, block, 7, random));
        ticks.register(
                Block.CARROTS,
                (instance, position, block, random) -> grow(instance, position, block, 7, random));
        ticks.register(
                Block.POTATOES,
                (instance, position, block, random) -> grow(instance, position, block, 7, random));
        ticks.register(
                Block.BEETROOTS,
                (instance, position, block, random) -> {
                    if (random.nextInt(3) != 0) {
                        grow(instance, position, block, 3, random);
                    }
                });
    }

    static void grow(
            Instance instance, BlockVec position, Block block, int maxAge, RandomGenerator random) {
        if (Level.rawBrightness(instance, position.blockX(), position.blockY(), position.blockZ())
                < MINIMUM_LIGHT) {
            return;
        }

        var age = Level.property(block, "age");
        if (age >= maxAge) {
            return;
        }

        var speed = growthSpeed(instance, position, block);
        if (random.nextInt((int) (25.0F / speed) + 1) == 0) {
            Level.set(instance, position, Level.withProperty(block, "age", age + 1));
        }
    }

    /** Vanilla {@code CropBlock.getGrowthSpeed}: farmland below helps, same-crop rows hurt. */
    private static float growthSpeed(Instance instance, BlockVec position, Block crop) {
        var speed = 1.0F;
        for (var x = -1; x <= 1; x++) {
            for (var z = -1; z <= 1; z++) {
                var soil = Level.block(instance, position.add(x, -1, z));
                var soilSpeed = 0.0F;
                if (soil.compare(Block.FARMLAND)) {
                    soilSpeed = Level.property(soil, "moisture") > 0 ? 3.0F : 1.0F;
                }
                speed += x != 0 || z != 0 ? soilSpeed / 4.0F : soilSpeed;
            }
        }

        var north = position.add(0, 0, -1);
        var south = position.add(0, 0, 1);
        var west = position.add(-1, 0, 0);
        var east = position.add(1, 0, 0);
        var eastWest = isSame(instance, west, crop) || isSame(instance, east, crop);
        var northSouth = isSame(instance, north, crop) || isSame(instance, south, crop);
        var diagonal =
                isSame(instance, west.add(0, 0, -1), crop)
                        || isSame(instance, east.add(0, 0, -1), crop)
                        || isSame(instance, east.add(0, 0, 1), crop)
                        || isSame(instance, west.add(0, 0, 1), crop);
        return (eastWest && northSouth) || diagonal ? speed / 2.0F : speed;
    }

    private static boolean isSame(Instance instance, BlockVec position, Block crop) {
        return Level.block(instance, position).compare(crop);
    }
}
