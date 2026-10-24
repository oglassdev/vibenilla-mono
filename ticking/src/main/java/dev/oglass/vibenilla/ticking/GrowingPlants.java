package dev.oglass.vibenilla.ticking;

import java.util.List;
import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

/** Vanilla {@code SugarCaneBlock} and {@code CactusBlock} growth. */
final class GrowingPlants {
    private static final int MAX_AGE = 15;
    private static final int MAX_HEIGHT = 3;
    private static final int FLOWER_AGE = 8;

    private static final List<BlockFace> HORIZONTAL =
            List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);

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

    static void cactus(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        var above = position.add(0, 1, 0);
        if (!Level.block(instance, above).air()) {
            return;
        }

        var height = height(instance, position, block);
        var age = Level.property(block, "age");
        if (height >= MAX_HEIGHT && age == MAX_AGE) {
            return;
        }

        if (age == FLOWER_AGE && canSurvive(instance, above)) {
            if (random.nextDouble() <= (height >= MAX_HEIGHT ? 0.25 : 0.1)) {
                Level.setAndUpdate(instance, above, Block.CACTUS_FLOWER);
            }
        } else if (age == MAX_AGE && height < MAX_HEIGHT) {
            Level.setAndUpdate(instance, above, Block.CACTUS);
            Level.set(instance, position, Level.withProperty(block, "age", 0));
            if (!canSurvive(instance, above)) {
                breakCactus(instance, above);
            }
            return;
        }

        if (age < MAX_AGE) {
            Level.set(instance, position, Level.withProperty(block, "age", age + 1));
        }
    }

    /** Cacti stand on sand or cacti, and cannot touch solid blocks, lava or liquid above. */
    private static boolean canSurvive(Instance instance, BlockVec position) {
        for (var face : HORIZONTAL) {
            var neighbor = Level.block(instance, position.relative(face));
            if (neighbor.solid() || neighbor.compare(Block.LAVA)) {
                return false;
            }
        }

        var below = Level.block(instance, position.add(0, -1, 0));
        return (below.compare(Block.CACTUS) || Level.hasTag(below, "minecraft:supports_cactus"))
                && !Level.block(instance, position.add(0, 1, 0)).liquid();
    }

    private static void breakCactus(Instance instance, BlockVec position) {
        Level.setAndUpdate(instance, position, Block.AIR);
        var drop = new ItemEntity(ItemStack.of(Material.CACTUS));
        drop.setInstance(instance, position.add(0.5, 0.5, 0.5));
    }

    private static int height(Instance instance, BlockVec position, Block plant) {
        var height = 1;
        while (Level.block(instance, position.add(0, -height, 0)).compare(plant)) {
            height++;
        }
        return height;
    }
}
