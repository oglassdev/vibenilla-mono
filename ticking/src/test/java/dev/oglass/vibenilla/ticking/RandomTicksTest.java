package dev.oglass.vibenilla.ticking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

@EnvTest
final class RandomTicksTest {
    private static final BlockVec NEAR = new BlockVec(8, TestWorld.GROUND + 1, 8);
    private static final BlockVec FAR = new BlockVec(8 + 16 * 40, TestWorld.GROUND + 1, 8);

    private static void plant(net.minestom.server.instance.Instance instance, BlockVec crop) {
        instance.setBlock(crop.add(0, -1, 0), Block.FARMLAND.withProperty("moisture", "7"));
        instance.setBlock(crop, Block.WHEAT);
    }

    @Test
    void onlyBlocksNearPlayersAreTicked(Env env) {
        var instance = env.createFlatInstance();
        TestWorld.load(instance, 0, 0);
        TestWorld.load(instance, 40, 0);
        plant(instance, NEAR);
        plant(instance, FAR);

        var ticks = new RandomTicks(4096, 2);
        VanillaRandomTicks.register(ticks);
        env.process().eventHandler().addChild(ticks.events());
        env.createPlayer(instance, new Pos(8, TestWorld.GROUND + 1, 8));

        for (var tick = 0; tick < 300; tick++) env.tick();

        assertTrue(Integer.parseInt(instance.getBlock(NEAR).getProperty("age")) > 0);
        assertEquals("0", instance.getBlock(FAR).getProperty("age"));
    }
}
