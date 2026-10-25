package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/**
 * Vanilla {@code GrowingPlantHeadBlock}: kelp and the twisting, weeping and cave vines extend their
 * head, and the old head becomes a body block.
 */
final class GrowingPlantHeads {
    private static final int MAX_AGE = 25;
    private static final float BERRY_ODDS = 0.11F;

    record Plant(Block head, Block body, BlockFace direction, double probability) {}

    static final Plant KELP = new Plant(Block.KELP, Block.KELP_PLANT, BlockFace.TOP, 0.14);
    static final Plant TWISTING_VINES =
            new Plant(Block.TWISTING_VINES, Block.TWISTING_VINES_PLANT, BlockFace.TOP, 0.1);
    static final Plant WEEPING_VINES =
            new Plant(Block.WEEPING_VINES, Block.WEEPING_VINES_PLANT, BlockFace.BOTTOM, 0.1);
    static final Plant CAVE_VINES =
            new Plant(Block.CAVE_VINES, Block.CAVE_VINES_PLANT, BlockFace.BOTTOM, 0.1);

    private GrowingPlantHeads() {}

    static void register(RandomTicks ticks) {
        register(ticks, KELP);
        register(ticks, TWISTING_VINES);
        register(ticks, WEEPING_VINES);
        register(ticks, CAVE_VINES);
    }

    private static void register(RandomTicks ticks, Plant plant) {
        ticks.register(
                plant.head,
                (instance, position, block, random) ->
                        grow(instance, position, block, plant, random));
    }

    static void grow(
            Instance instance,
            BlockVec position,
            Block block,
            Plant plant,
            RandomGenerator random) {
        var age = Level.property(block, "age");
        if (age >= MAX_AGE || random.nextDouble() >= plant.probability) {
            return;
        }

        var target = position.relative(plant.direction);
        if (!canGrowInto(Level.block(instance, target), plant)) {
            return;
        }

        var head = Level.withProperty(block, "age", age + 1);
        var body = plant.body.withHandler(block.handler());
        if (block.properties().containsKey("berries")) {
            head = head.withProperty("berries", Boolean.toString(random.nextFloat() < BERRY_ODDS));
            body = body.withProperty("berries", block.getProperty("berries"));
        }
        Level.setAndUpdate(instance, target, head);
        Level.set(instance, position, body);
    }

    /** Kelp grows into water, the vines into air. */
    private static boolean canGrowInto(Block target, Plant plant) {
        return plant == KELP ? target.compare(Block.WATER) : target.air();
    }
}
