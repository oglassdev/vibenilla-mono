package dev.oglass.vibenilla.ticking;

import java.nio.file.Path;
import java.util.Map;
import java.util.random.RandomGenerator;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.ConfiguredFeature;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Grows saplings into the trees of the vanilla world generation data, following vanilla's {@code
 * TreeGrower}. Saplings without a grower here, such as poplar and mangrove propagules, never grow.
 */
public final class WorldgenTrees {
    private static final int SEA_LEVEL = 63;

    /** A vanilla {@code TreeGrower}; features that the species lacks are null. */
    private record Grower(
            float secondaryChance,
            @Nullable String mega,
            @Nullable String secondaryMega,
            @Nullable String tree,
            @Nullable String secondaryTree,
            @Nullable String flowers,
            @Nullable String secondaryFlowers) {
        @Nullable
        String pickMega(RandomGenerator random) {
            return secondaryMega != null && random.nextFloat() < secondaryChance
                    ? secondaryMega
                    : mega;
        }

        @Nullable
        String pickTree(RandomGenerator random, boolean nearFlowers) {
            if (random.nextFloat() < secondaryChance) {
                if (nearFlowers && secondaryFlowers != null) {
                    return secondaryFlowers;
                }
                if (secondaryTree != null) {
                    return secondaryTree;
                }
            }
            return nearFlowers && flowers != null ? flowers : tree;
        }
    }

    private static final Map<String, Grower> GROWERS =
            Map.of(
                    "oak_sapling",
                    new Grower(
                            0.1F,
                            null,
                            null,
                            "oak",
                            "fancy_oak",
                            "oak_bees_005",
                            "fancy_oak_bees_005"),
                    "spruce_sapling",
                    new Grower(0.5F, "mega_spruce", "mega_pine", "spruce", null, null, null),
                    "birch_sapling",
                    new Grower(0.0F, null, null, "birch", null, "birch_bees_005", null),
                    "jungle_sapling",
                    new Grower(
                            0.0F,
                            "mega_jungle_tree",
                            null,
                            "jungle_tree_no_vine",
                            null,
                            null,
                            null),
                    "acacia_sapling",
                    new Grower(0.0F, null, null, "acacia", null, null, null),
                    "cherry_sapling",
                    new Grower(0.0F, null, null, "cherry", null, "cherry_bees_005", null),
                    "dark_oak_sapling",
                    new Grower(0.0F, "dark_oak", null, null, null, null, null),
                    "pale_oak_sapling",
                    new Grower(0.0F, "pale_oak_bonemeal", null, null, null, null, null));

    private final FeatureLoader features;

    /**
     * @param dataRoot the directory holding the vanilla {@code data} folder
     */
    public WorldgenTrees(Path dataRoot) {
        this.features = new FeatureLoader(new DataPack(dataRoot));
    }

    /** Grows the tree of the sapling at the position, returning whether one was placed. */
    boolean grow(Instance instance, BlockVec position, Block sapling, RandomGenerator random) {
        var grower = GROWERS.get(sapling.key().value());
        if (grower == null) {
            return false;
        }

        var source = new LegacyRandomSource(random.nextLong());
        var accessor = new Accessor(instance);
        var mega = grower.pickMega(random);
        if (mega != null) {
            var corner = twoByTwo(instance, position, sapling);
            if (corner != null) {
                return growMega(accessor, corner, sapling, mega, source);
            }
        }

        var tree = grower.pickTree(random, hasFlowers(instance, position));
        if (tree == null) {
            return false;
        }

        var original = instance.getBlock(position);
        Level.set(instance, position, Block.AIR);
        if (place(tree, accessor, position, source)) {
            return true;
        }
        Level.set(instance, position, original);
        return false;
    }

    private boolean growMega(
            Accessor accessor,
            BlockVec corner,
            Block sapling,
            String feature,
            RandomSource random) {
        var instance = accessor.instance();
        for (var dx = 0; dx <= 1; dx++) {
            for (var dz = 0; dz <= 1; dz++) {
                Level.set(instance, corner.add(dx, 0, dz), Block.AIR);
            }
        }
        if (place(feature, accessor, corner, random)) {
            return true;
        }
        for (var dx = 0; dx <= 1; dx++) {
            for (var dz = 0; dz <= 1; dz++) {
                Level.set(instance, corner.add(dx, 0, dz), sapling);
            }
        }
        return false;
    }

    /** The lowest corner of a 2x2 of saplings that includes the position, if there is one. */
    @Nullable
    private static BlockVec twoByTwo(Instance instance, BlockVec position, Block sapling) {
        for (var x = 0; x >= -1; x--) {
            for (var z = 0; z >= -1; z--) {
                var corner = position.add(x, 0, z);
                if (isTwoByTwo(instance, corner, sapling)) {
                    return corner;
                }
            }
        }
        return null;
    }

    private static boolean isTwoByTwo(Instance instance, BlockVec corner, Block sapling) {
        for (var dx = 0; dx <= 1; dx++) {
            for (var dz = 0; dz <= 1; dz++) {
                if (!Level.block(instance, corner.add(dx, 0, dz)).compare(sapling)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasFlowers(Instance instance, BlockVec position) {
        for (var x = -2; x <= 2; x++) {
            for (var y = -1; y <= 1; y++) {
                for (var z = -2; z <= 2; z++) {
                    if (Level.hasTag(
                            Level.block(instance, position.add(x, y, z)), "minecraft:flowers")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean place(String feature, Accessor accessor, BlockVec origin, RandomSource random) {
        var configured = this.features.getConfiguredFeature(Key.key("minecraft", feature));
        return configured != null && place(configured, accessor, origin, random);
    }

    private static <C extends FeatureConfiguration> boolean place(
            ConfiguredFeature<C> configured,
            Accessor accessor,
            BlockVec origin,
            RandomSource random) {
        var dimension = accessor.instance().getCachedDimensionType();
        return configured
                .feature()
                .place(
                        new FeaturePlaceContext<>(
                                accessor,
                                random,
                                origin,
                                configured.config(),
                                0L,
                                dimension.minY(),
                                dimension.maxY(),
                                SEA_LEVEL));
    }

    private record Accessor(Instance instance) implements Block.Getter, Block.Setter {
        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            return Level.block(this.instance, x, y, z);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            Level.set(this.instance, new BlockVec(x, y, z), block);
        }
    }
}
