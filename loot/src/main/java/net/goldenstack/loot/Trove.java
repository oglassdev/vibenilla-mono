package net.goldenstack.loot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.registry.RegistryTranscoder;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Trove {

    private Trove() {}

    private static final @NotNull Map<Key, LootTable> TABLES = new ConcurrentHashMap<>();
    private static final @NotNull Map<Key, LootPredicate> PREDICATES = new ConcurrentHashMap<>();

    /**
     * Returns the loot table that was registered under the provided key by {@link
     * #readTables(Path)}, or null if there is none.
     */
    public static @Nullable LootTable table(@NotNull Key key) {
        return TABLES.get(key);
    }

    /**
     * Returns the predicate that was registered under the provided key by {@link
     * #readPredicates(Path)} or {@link #readTables(Path)}, or null if there is none.
     */
    public static @Nullable LootPredicate predicate(@NotNull Key key) {
        return PREDICATES.get(key);
    }

    /**
     * Parses every JSON file in the provided directory, or one of its subdirectories, into loot
     * tables, returning the results in to a table registry instance. The tables are also made
     * available to {@link VanillaInterface#defaults()} for loot table entries that reference other
     * tables.<br>
     * If the directory is, or is inside, a {@code loot_table} directory with a sibling {@code
     * predicate} directory, those predicates are read with {@link #readPredicates(Path)} first, so
     * that tables can reference them.
     *
     * @param directory the directory to parse
     * @return the registry instance that contains parsing information
     */
    public static @NotNull Map<Key, LootTable> readTables(@NotNull Path directory) {
        for (var path = directory.toAbsolutePath(); path != null; path = path.getParent()) {
            var fileName = path.getFileName();
            if (fileName == null || !fileName.toString().equals("loot_table")) continue;

            var predicates = path.resolveSibling("predicate");
            if (Files.isDirectory(predicates)) {
                readPredicates(predicates);
            }
            break;
        }

        var tables = read(directory, LootTable.CODEC);
        TABLES.putAll(tables);
        return tables;
    }

    /**
     * Parses every JSON file in the provided directory, or one of its subdirectories, into loot
     * predicates, which are made available to {@link VanillaInterface#defaults()} for predicate
     * references.
     *
     * @param directory the directory to parse
     * @return the predicates, keyed by their path relative to the directory
     */
    public static @NotNull Map<Key, LootPredicate> readPredicates(@NotNull Path directory) {
        var predicates = read(directory, LootPredicate.CODEC);
        PREDICATES.putAll(predicates);
        return predicates;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static <T> @NotNull Map<Key, T> read(@NotNull Path directory, @NotNull Codec<T> codec) {
        Map<Key, T> values = new HashMap<>();

        final String FILE_SUFFIX = ".json";

        List<Path> files;
        try (var stream =
                Files.find(
                        directory,
                        Integer.MAX_VALUE,
                        (path, attr) ->
                                attr.isRegularFile()
                                        && path.getFileName().toString().endsWith(FILE_SUFFIX))) {
            files = stream.toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Transcoder<BinaryTag> coder =
                new RegistryTranscoder<>(Transcoder.NBT, MinecraftServer.process());

        for (var path : files) {

            String keyPath =
                    StreamSupport.stream(directory.relativize(path).spliterator(), false)
                            .map(Path::toString)
                            .collect(Collectors.joining("/"));

            keyPath = keyPath.substring(0, keyPath.length() - FILE_SUFFIX.length());

            BinaryTag tag;
            try {
                tag = MinestomAdventure.tagStringIO().asTag(Files.readString(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            values.put(Key.key(keyPath), codec.decode(coder, tag).orElseThrow("parsing " + path));
        }

        return values;
    }

    public static void blockDrop(
            @NotNull Instance instance, @NotNull ItemStack item, @NotNull Point block) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        Pos spawn =
                new Pos(
                        block.blockX() + 0.5 + rng.nextDouble(-0.25, 0.25),
                        block.blockY()
                                + 0.5
                                + rng.nextDouble(-0.25, 0.25)
                                - EntityType.ITEM.height() / 2,
                        block.blockZ() + 0.5 + rng.nextDouble(-0.25, 0.25),
                        rng.nextFloat(360),
                        0);

        drop(instance, item, spawn);
    }

    public static void drop(
            @NotNull Instance instance, @NotNull ItemStack item, @NotNull Point position) {
        ItemEntity entity = new ItemEntity(item);

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        Vec vel = new Vec(rng.nextDouble(-0.1, 0.1), 0.2, rng.nextDouble(-0.1, 0.1)).mul(20);

        entity.setPickupDelay(10, TimeUnit.SERVER_TICK);

        entity.setInstance(instance, position);
        entity.setVelocity(vel);
    }
}
