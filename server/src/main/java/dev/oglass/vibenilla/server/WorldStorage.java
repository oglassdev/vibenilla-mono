package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.world.DimensionType;
import org.jspecify.annotations.Nullable;
import rocks.minestom.worldgen.WorldGenerators;

/**
 * Anvil-backed overworld. The seed and world spawn live in {@code vibenilla.properties}, written
 * atomically once when the world is created; chunks are saved through the Anvil loader.
 */
final class WorldStorage {
    private static final Key OVERWORLD = Key.key("minecraft:overworld");
    private static final String METADATA = "vibenilla.properties";

    private final InstanceContainer instance;
    private final long seed;
    private final Pos spawn;
    private @Nullable CompletableFuture<Void> saving;

    WorldStorage(Path worldPath, Path dataPath, @Nullable Long requestedSeed) {
        var regions = worldPath.resolve("dimensions/minecraft/overworld/region");
        var metadataFile = worldPath.resolve(METADATA);
        try {
            Files.createDirectories(regions);
            var metadata = readMetadata(metadataFile);
            if (metadata == null && hasRegions(regions)) {
                throw new IllegalStateException(
                        "World "
                                + worldPath
                                + " has region files but no readable "
                                + METADATA
                                + "; refusing to pick a new seed. Restore the file or delete the"
                                + " world.");
            }

            instance =
                    MinecraftServer.getInstanceManager()
                            .createInstanceContainer(
                                    DimensionType.OVERWORLD, new AnvilLoader(worldPath, OVERWORLD));
            instance.setChunkSupplier(LightingChunk::new);

            if (metadata == null) {
                seed =
                        requestedSeed != null
                                ? requestedSeed
                                : ThreadLocalRandom.current().nextLong();
                instance.setGenerator(new WorldGenerators(dataPath, seed).overworld());
                var found = findSurface(instance, 0, 0);
                spawn = new Pos(found.blockX() + 0.5, found.blockY(), found.blockZ() + 0.5);
                writeMetadata(metadataFile, seed, found);
            } else {
                seed = Long.parseLong(metadata.getProperty("seed"));
                if (requestedSeed != null && requestedSeed != seed) {
                    MinecraftServer.LOGGER.warn(
                            "Ignoring -Dvibenilla.seed={}; world {} already uses seed {}",
                            requestedSeed,
                            worldPath,
                            seed);
                }
                instance.setGenerator(new WorldGenerators(dataPath, seed).overworld());
                spawn =
                        new Pos(
                                Integer.parseInt(metadata.getProperty("spawnX")) + 0.5,
                                Integer.parseInt(metadata.getProperty("spawnY")),
                                Integer.parseInt(metadata.getProperty("spawnZ")) + 0.5);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    /** Starts saving every loaded chunk unless a save is already running, which is returned. */
    synchronized CompletableFuture<Void> save() {
        if (saving == null || saving.isDone()) {
            saving =
                    instance.saveChunksToStorage()
                            .whenComplete(
                                    (ignored, error) -> {
                                        if (error != null) {
                                            MinecraftServer.LOGGER.error(
                                                    "Failed to save world", error);
                                        }
                                    });
        }
        return saving;
    }

    /** Waits for any running save, then performs and awaits a final one. */
    void saveAndWait() {
        CompletableFuture<Void> running;
        synchronized (this) {
            running = saving;
        }
        if (running != null) {
            running.handle((ignored, error) -> null).join();
        }
        save().join();
    }

    private static @Nullable Properties readMetadata(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try (var in = Files.newInputStream(file)) {
            var properties = new Properties();
            properties.load(in);
            Long.parseLong(properties.getProperty("seed"));
            Integer.parseInt(properties.getProperty("spawnX"));
            Integer.parseInt(properties.getProperty("spawnY"));
            Integer.parseInt(properties.getProperty("spawnZ"));
            return properties;
        } catch (IOException | RuntimeException e) {
            MinecraftServer.LOGGER.error("Failed to read world metadata {}", file, e);
            return null;
        }
    }

    private static void writeMetadata(Path file, long seed, Pos spawn) throws IOException {
        var properties = new Properties();
        properties.setProperty("seed", Long.toString(seed));
        properties.setProperty("spawnX", Integer.toString(spawn.blockX()));
        properties.setProperty("spawnY", Integer.toString(spawn.blockY()));
        properties.setProperty("spawnZ", Integer.toString(spawn.blockZ()));
        var temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var out = Files.newOutputStream(temp)) {
            properties.store(out, null);
        }
        AtomicFiles.replace(temp, file);
    }

    private static boolean hasRegions(Path regions) throws IOException {
        try (var files = Files.list(regions)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".mca"));
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
