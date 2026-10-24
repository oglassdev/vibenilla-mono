package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/** Vanilla {@code LavaFluid.randomTick}: lava sets nearby flammable blocks on fire. */
final class Lava {
    private Lava() {}

    static void register(RandomTicks ticks) {
        ticks.register(Block.LAVA, Lava::tick);
    }

    private static void tick(
            Instance instance, BlockVec position, Block block, RandomGenerator random) {
        var steps = random.nextInt(3);
        if (steps > 0) {
            var target = position;
            for (var step = 0; step < steps; step++) {
                target = target.add(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                if (!Level.isLoaded(instance, target)) {
                    return;
                }

                var current = Level.block(instance, target);
                if (current.air()) {
                    if (hasFlammableNeighbor(instance, target)) {
                        Fire.ignite(instance, target, random);
                        return;
                    }
                } else if (current.solid()) {
                    return;
                }
            }
            return;
        }

        for (var attempt = 0; attempt < 3; attempt++) {
            var target = position.add(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            if (!Level.isLoaded(instance, target)) {
                return;
            }

            var above = target.add(0, 1, 0);
            if (Level.block(instance, above).air()
                    && Flammability.canBurn(Level.block(instance, target))) {
                Fire.ignite(instance, above, random);
            }
        }
    }

    private static boolean hasFlammableNeighbor(Instance instance, BlockVec position) {
        for (var face : BlockFace.values()) {
            if (Flammability.canBurn(Level.block(instance, position.relative(face)))) {
                return true;
            }
        }
        return false;
    }
}
