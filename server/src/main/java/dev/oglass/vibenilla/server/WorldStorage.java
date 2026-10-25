package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.DimensionType;
import org.jspecify.annotations.Nullable;
import rocks.minestom.worldgen.WorldGenerators;

/**
 * Anvil-backed overworld, nether and end. The seed and world spawn live in {@code
 * vibenilla.properties}, written atomically once when the world is created; chunks are saved
 * through one Anvil loader per dimension.
 */
final class WorldStorage {
    static final Key OVERWORLD = Key.key("minecraft:overworld");
    static final Key THE_NETHER = Key.key("minecraft:the_nether");
    static final Key THE_END = Key.key("minecraft:the_end");

    private static final String METADATA = "vibenilla.properties";

    private record Dimension(
            Key key,
            RegistryKey<DimensionType> type,
            Function<WorldGenerators, Generator> generator) {}

    private static final List<Dimension> DIMENSIONS =
            List.of(
                    new Dimension(OVERWORLD, DimensionType.OVERWORLD, WorldGenerators::overworld),
                    new Dimension(THE_NETHER, DimensionType.THE_NETHER, WorldGenerators::nether),
                    new Dimension(THE_END, DimensionType.THE_END, WorldGenerators::end));

    private final Map<Key, InstanceContainer> instances = new LinkedHashMap<>();
    private final long seed;
    private final Pos spawn;
    private @Nullable CompletableFuture<Void> saving;

    WorldStorage(Path worldPath, Path dataPath, @Nullable Long requestedSeed) {
        var metadataFile = worldPath.resolve(METADATA);
        try {
            var hasRegions = false;
            for (var dimension : DIMENSIONS) {
                var regions = regions(worldPath, dimension.key());
                Files.createDirectories(regions);
                hasRegions |= hasRegions(regions);
            }
            var metadata = readMetadata(metadataFile);
            if (metadata == null && hasRegions) {
                throw new IllegalStateException(
                        "World "
                                + worldPath
                                + " has region files but no readable "
                                + METADATA
                                + "; refusing to pick a new seed. Restore the file or delete the"
                                + " world.");
            }

            if (metadata == null) {
                seed =
                        requestedSeed != null
                                ? requestedSeed
                                : ThreadLocalRandom.current().nextLong();
            } else {
                seed = Long.parseLong(metadata.getProperty("seed"));
                if (requestedSeed != null && requestedSeed != seed) {
                    MinecraftServer.LOGGER.warn(
                            "Ignoring -Dvibenilla.seed={}; world {} already uses seed {}",
                            requestedSeed,
                            worldPath,
                            seed);
                }
            }

            var generators = new WorldGenerators(dataPath, seed);
            for (var dimension : DIMENSIONS) {
                var instance =
                        MinecraftServer.getInstanceManager()
                                .createInstanceContainer(
                                        dimension.type(),
                                        new AnvilLoader(worldPath, dimension.key()));
                instance.setChunkSupplier(LightingChunk::new);
                instance.setGenerator(dimension.generator().apply(generators));
                instances.put(dimension.key(), instance);
            }

            if (metadata == null) {
                var found = findSurface(overworld(), 0, 0);
                spawn = new Pos(found.blockX() + 0.5, found.blockY(), found.blockZ() + 0.5);
                writeMetadata(metadataFile, seed, found);
            } else {
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

    InstanceContainer overworld() {
        return instances.get(OVERWORLD);
    }

    /** The instance of the dimension, or null if the world has no such dimension. */
    @Nullable InstanceContainer instance(Key dimension) {
        return instances.get(dimension);
    }

    Collection<InstanceContainer> instances() {
        return instances.values();
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
                    CompletableFuture.allOf(
                                    instances.values().stream()
                                            .map(InstanceContainer::saveChunksToStorage)
                                            .toArray(CompletableFuture[]::new))
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

    private static Path regions(Path worldPath, Key dimension) {
        return worldPath
                .resolve("dimensions")
                .resolve(dimension.namespace())
                .resolve(dimension.value())
                .resolve("region");
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
