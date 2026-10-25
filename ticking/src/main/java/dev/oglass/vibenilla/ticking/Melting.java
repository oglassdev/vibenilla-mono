package dev.oglass.vibenilla.ticking;

import io.github.togar2.fluids.FluidState;
import io.github.togar2.fluids.MinestomFluids;
import io.github.togar2.fluids.WaterFluid;
import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** Vanilla {@code IceBlock} and {@code SnowLayerBlock} melting next to bright block light. */
final class Melting {
    private static final int ICE_LIGHT = 11;
    private static final int SNOW_LIGHT = 11;

    private Melting() {}

    static void register(RandomTicks ticks) {
        ticks.register(Block.ICE, Melting::ice);
        ticks.register(Block.SNOW, Melting::snow);
    }

    static void ice(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        if (blockLight(instance, position) <= ICE_LIGHT - block.lightBlocked()) {
            return;
        }

        if (WaterFluid.evaporates(instance)) {
            Level.setAndUpdate(instance, position, Block.AIR);
        } else {
            Level.setAndUpdate(instance, position, Block.WATER);
            MinestomFluids.scheduleTick(instance, position, FluidState.of(Block.WATER));
        }
    }

    static void snow(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        if (blockLight(instance, position) > SNOW_LIGHT) {
            Level.setAndUpdate(instance, position, Block.AIR);
        }
    }

    private static int blockLight(Instance instance, BlockVec position) {
        return Level.blockLight(instance, position.blockX(), position.blockY(), position.blockZ());
    }
}
