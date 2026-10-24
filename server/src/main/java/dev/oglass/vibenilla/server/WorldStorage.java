package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.DimensionType;
import org.jspecify.annotations.Nullable;
import rocks.minestom.worldgen.WorldGenerators;

/**
 * Anvil-backed overworld. {@code level.dat} holds the instance tags, which carry the seed and world
 * spawn.
 */
final class WorldStorage {
    private static final Tag<Long> SEED = Tag.Long("vibenilla_seed");
    private static final Tag<Integer> SPAWN_X = Tag.Integer("vibenilla_spawn_x");
    private static final Tag<Integer> SPAWN_Y = Tag.Integer("vibenilla_spawn_y");
    private static final Tag<Integer> SPAWN_Z = Tag.Integer("vibenilla_spawn_z");

    private static final Key OVERWORLD = Key.key("minecraft:overworld");

    private final InstanceContainer instance;
    private final long seed;
    private final Pos spawn;

    WorldStorage(Path worldPath, Path dataPath, @Nullable Long requestedSeed) {
        try {
            Files.createDirectories(worldPath.resolve("dimensions/minecraft/overworld/region"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        instance =
                MinecraftServer.getInstanceManager()
                        .createInstanceContainer(
                                DimensionType.OVERWORLD, new AnvilLoader(worldPath, OVERWORLD));
        instance.setChunkSupplier(LightingChunk::new);

        var storedSeed = instance.getTag(SEED);
        if (storedSeed == null) {
            seed = requestedSeed != null ? requestedSeed : ThreadLocalRandom.current().nextLong();
            instance.setTag(SEED, seed);
        } else {
            seed = storedSeed;
            if (requestedSeed != null && requestedSeed != seed) {
                MinecraftServer.LOGGER.warn(
                        "Ignoring -Dvibenilla.seed={}; world {} already uses seed {}",
                        requestedSeed,
                        worldPath,
                        seed);
            }
        }
        instance.setGenerator(new WorldGenerators(dataPath, seed).overworld());

        var spawnX = instance.getTag(SPAWN_X);
        var spawnY = instance.getTag(SPAWN_Y);
        var spawnZ = instance.getTag(SPAWN_Z);
        if (spawnX == null || spawnY == null || spawnZ == null) {
            var found = findSurface(instance, 0, 0);
            spawnX = found.blockX();
            spawnY = found.blockY();
            spawnZ = found.blockZ();
            instance.setTag(SPAWN_X, spawnX);
            instance.setTag(SPAWN_Y, spawnY);
            instance.setTag(SPAWN_Z, spawnZ);
        }
        spawn = new Pos(spawnX + 0.5, spawnY, spawnZ + 0.5);
    }

    InstanceContainer instance() {
        return instance;
    }

    long seed() {
        return seed;
    }

    Pos spawn() {
        return spawn;
    }

    /** Writes level.dat and every loaded chunk, blocking until done when {@code wait} is set. */
    void save(boolean wait) {
        var level = instance.saveInstance();
        var chunks = instance.saveChunksToStorage();
        if (wait) {
            level.join();
            chunks.join();
        }
    }

    private static Pos findSurface(Instance instance, int x, int z) {
        instance.loadChunk(x >> 4, z >> 4).join();
        var dimension = instance.getCachedDimensionType();
        for (var y = dimension.maxY() - 1; y > dimension.minY(); y--) {
            if (!instance.getBlock(x, y, z).air()) {
                return new Pos(x + 0.5, y + 1, z + 0.5);
            }
        }
        return new Pos(x + 0.5, 64, z + 0.5);
    }
}
