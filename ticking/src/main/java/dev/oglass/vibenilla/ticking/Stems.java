package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/**
 * Vanilla {@code StemBlock}: stems grow like crops, then ripe stems place their fruit next to them
 * and turn into the attached stem facing it.
 */
final class Stems {
    private static final int MAX_AGE = 7;

    private Stems() {}

    static void register(RandomTicks ticks) {
        ticks.register(
                Block.PUMPKIN_STEM,
                (instance, position, block, random) ->
                        tick(
                                instance,
                                position,
                                block,
                                Block.PUMPKIN,
                                Block.ATTACHED_PUMPKIN_STEM,
                                "minecraft:supports_pumpkin_stem_fruit",
                                random));
        ticks.register(
                Block.MELON_STEM,
                (instance, position, block, random) ->
                        tick(
                                instance,
                                position,
                                block,
                                Block.MELON,
                                Block.ATTACHED_MELON_STEM,
                                "minecraft:supports_melon_stem_fruit",
                                random));
    }

    static void tick(
            Instance instance,
            BlockVec position,
            Block block,
            Block fruit,
            Block attached,
            String fruitSupport,
            RandomGenerator random) {
        if (!Crops.rollsGrowth(instance, position, block, random)) {
            return;
        }

        var age = Level.property(block, "age");
        if (age < MAX_AGE) {
            Level.set(instance, position, Level.withProperty(block, "age", age + 1));
            return;
        }

        var face = Level.HORIZONTAL.get(random.nextInt(Level.HORIZONTAL.size()));
        var target = position.relative(face);
        var soil = Level.block(instance, target.add(0, -1, 0));
        if (Level.block(instance, target).air() && Level.hasTag(soil, fruitSupport)) {
            Level.setAndUpdate(instance, target, fruit);
            Level.setAndUpdate(
                    instance, position, attached.withProperty("facing", face.name().toLowerCase()));
        }
    }
}
