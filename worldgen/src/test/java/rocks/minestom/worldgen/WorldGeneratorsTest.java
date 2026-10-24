package rocks.minestom.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorldGeneratorsTest {
    private static final String DATAPACK_PROPERTY = "worldgen.datapack";
    private static final String DEFAULT_DATAPACK =
            "/home/alex/.t3/scratch/vibenilla-mono/server/run/vanilla";
    private static final long SEED = 123456789L;

    private static Path datapack;
    private static WorldGenerators generators;

    @BeforeAll
    static void setUp() {
        var configured = System.getProperty(DATAPACK_PROPERTY, System.getenv("WORLDGEN_DATAPACK"));
        datapack = Path.of(configured != null ? configured : DEFAULT_DATAPACK);
        assumeTrue(
                Files.isDirectory(datapack.resolve("data/minecraft/worldgen")),
                "datapack not found at " + datapack);
        MinecraftServer.init();
        generators = new WorldGenerators(datapack, SEED);
    }

    @Test
    void generatesOverworldChunks() {
        var instance =
                MinecraftServer.getInstanceManager()
                        .createInstanceContainer(DimensionType.OVERWORLD);
        instance.setGenerator(generators.overworld());
        assertGenerates(instance);
    }

    @Test
    void generatesNetherChunks() {
        var instance =
                MinecraftServer.getInstanceManager()
                        .createInstanceContainer(DimensionType.THE_NETHER);
        instance.setGenerator(generators.nether());
        assertGenerates(instance);
    }

    @Test
    void generatesEndChunks() {
        var instance =
                MinecraftServer.getInstanceManager().createInstanceContainer(DimensionType.THE_END);
        instance.setGenerator(generators.end());
        assertGenerates(instance);
    }

    private static void assertGenerates(Instance instance) {
        var futures = new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for (var chunkX = -2; chunkX <= 2; chunkX++) {
            for (var chunkZ = -2; chunkZ <= 2; chunkZ++) {
                futures.add(instance.loadChunk(chunkX, chunkZ));
            }
        }
        futures.forEach(java.util.concurrent.CompletableFuture::join);

        var solid = false;
        var minY = instance.getCachedDimensionType().minY();
        var maxY = instance.getCachedDimensionType().maxY();
        for (var y = minY; y < maxY && !solid; y++) {
            solid = !instance.getBlock(0, y, 0).air();
        }
        assertTrue(solid, "expected generated terrain at the origin column");
        assertFalse(instance.getChunks().isEmpty());
    }
}
