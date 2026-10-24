package io.github.togar2.pvp.utils;

import net.minestom.server.coordinate.Pos;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

@EnvTest
public final class FluidUtilTest {
    @Test
    public void ignoresUnloadedChunksWhenCheckingWater(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
        var unloadedChunk = instance.loadChunk(100, 0).join();
        var unloadedPosition = new Pos(1600.0, 40.0, 0.0);

        instance.unloadChunk(unloadedChunk);

        assertFalse(instance.isChunkLoaded(unloadedPosition));

        var touchingWater = assertDoesNotThrow(() -> FluidUtil.isTouchingWater(player, unloadedPosition));

        assertFalse(touchingWater);
    }
}
