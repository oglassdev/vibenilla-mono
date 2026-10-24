package dev.oglass.vibenilla.ticking;

import java.util.Arrays;
import net.minestom.server.instance.Instance;

final class TestWorld {
    /** The y of the topmost block of the flat test instance. */
    static final int GROUND = 39;

    private TestWorld() {}

    /** Loads the chunk and lights it as if under open sky. */
    static void load(Instance instance, int chunkX, int chunkZ) {
        var chunk = instance.loadChunk(chunkX, chunkZ).join();
        var daylight = new byte[2048];
        Arrays.fill(daylight, (byte) 0xFF);
        for (var section : chunk.getSections()) {
            section.skyLight().set(daylight);
        }
    }
}
