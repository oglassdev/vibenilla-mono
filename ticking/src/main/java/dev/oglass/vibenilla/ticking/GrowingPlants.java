package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** Vanilla {@code SugarCaneBlock} and {@code CactusBlock} growth. */
final class GrowingPlants {
    private static final int MAX_AGE = 15;
    private static final int MAX_HEIGHT = 3;
    private static final int FLOWER_AGE = 8;

    private GrowingPlants() {}

    static void register(RandomTicks ticks) {
        ticks.register(Block.SUGAR_CANE, GrowingPlants::sugarCane);
        ticks.register(Block.CACTUS, GrowingPlants::cactus);
    }

    static void sugarCane(
            Instance instance, BlockVec position, Block block, RandomGenerator random) {
        var above = position.add(0, 1, 0);
        if (!Level.block(instance, above).air()
                || height(instance, position, block) >= MAX_HEIGHT) {
            return;
        }

        var age = Level.property(block, "age");
        if (age == MAX_AGE) {
            Level.setAndUpdate(instance, above, Block.SUGAR_CANE);
            Level.set(instance, position, Level.withProperty(block, "age", 0));
        } else {
            Level.set(instance, position, Level.withProperty(block, "age", age + 1));
        }
    }

    private static void cactus(
            Instance instance, BlockVec position, Block block, RandomGenerator random) {
        var above = position.add(0, 1, 0);
        if (!Level.block(instance, above).air()) {
            return;
        }

        var height = height(instance, position, block);
        var age = Level.property(block, "age");
        if (height >= MAX_HEIGHT && age == MAX_AGE) {
            return;
        }

        if (age == FLOWER_AGE && canSurviveAt(instance, above)) {
            if (random.nextDouble() <= (height >= MAX_HEIGHT ? 0.25 : 0.1)) {
                Level.setAndUpdate(instance, above, Block.CACTUS_FLOWER);
            }
        } else if (age == MAX_AGE && height < MAX_HEIGHT) {
            Level.setAndUpdate(instance, above, Block.CACTUS);
            Level.set(instance, position, Level.withProperty(block, "age", 0));
            return;
        }

        if (age < MAX_AGE) {
            Level.set(instance, position, Level.withProperty(block, "age", age + 1));
        }
    }

    /** Cacti cannot stand next to solid blocks. */
    private static boolean canSurviveAt(Instance instance, BlockVec position) {
        return !Level.block(instance, position.add(1, 0, 0)).solid()
                && !Level.block(instance, position.add(-1, 0, 0)).solid()
                && !Level.block(instance, position.add(0, 0, 1)).solid()
                && !Level.block(instance, position.add(0, 0, -1)).solid();
    }

    private static int height(Instance instance, BlockVec position, Block plant) {
        var height = 1;
        while (Level.block(instance, position.add(0, -height, 0)).compare(plant)) {
            height++;
        }
        return height;
    }
}
