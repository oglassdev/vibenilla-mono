package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

@FunctionalInterface
public interface RandomTickHandler {
    void tick(Instance instance, BlockVec position, Block block, RandomGenerator random);
}
