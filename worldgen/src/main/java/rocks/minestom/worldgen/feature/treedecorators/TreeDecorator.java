package rocks.minestom.worldgen.feature.treedecorators;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.ConfiguredFeature;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.random.RandomSource;

public interface TreeDecorator {
    void place(Context context);

    /**
     * Vanilla {@code Util.shuffle}: descending Fisher-Yates using {@code random.nextInt(i)},
     * matching the draw sequence exactly.
     */
    static <T> void shuffle(List<T> list, RandomSource random) {
        for (var index = list.size(); index > 1; index--) {
            list.set(index - 1, list.set(random.nextInt(index), list.get(index - 1)));
        }
    }

    /** Places a configured feature at a position, sharing the tree's level and random. */
    @FunctionalInterface
    interface FeaturePlacer {
        boolean place(ConfiguredFeature<?> feature, BlockVec origin);

        static <T extends Block.Getter & Block.Setter> FeaturePlacer in(
                FeaturePlaceContext<?, T> context) {
            return new FeaturePlacer() {
                @Override
                public boolean place(ConfiguredFeature<?> feature, BlockVec origin) {
                    return placeConfigured(feature, origin);
                }

                private <C extends FeatureConfiguration> boolean placeConfigured(
                        ConfiguredFeature<C> feature, BlockVec origin) {
                    Feature<C> implementation = feature.feature();
                    return implementation.place(
                            new FeaturePlaceContext<>(
                                    context.accessor(),
                                    context.random(),
                                    origin,
                                    feature.config(),
                                    context.worldSeed(),
                                    context.minY(),
                                    context.maxY(),
                                    context.seaLevel()));
                }
            };
        }
    }

    final class Context {
        private final Block.Getter level;
        private final BiConsumer<BlockVec, Block> decorationSetter;
        private final RandomSource random;
        private final List<BlockVec> logs;
        private final List<BlockVec> leaves;
        private final List<BlockVec> roots;
        private final FeaturePlacer featurePlacer;

        /**
         * Log/leaf/root collections must iterate in vanilla's hash order (see {@code VanillaPos});
         * the stable Y sort below then reproduces vanilla's list ordering exactly.
         */
        public Context(
                Block.Getter level,
                BiConsumer<BlockVec, Block> decorationSetter,
                RandomSource random,
                List<BlockVec> logs,
                List<BlockVec> leaves,
                List<BlockVec> roots,
                FeaturePlacer featurePlacer) {
            this.level = level;
            this.featurePlacer = featurePlacer;
            this.decorationSetter = decorationSetter;
            this.random = random;
            this.logs = new ArrayList<>(logs);
            this.leaves = new ArrayList<>(leaves);
            this.roots = new ArrayList<>(roots);
            this.logs.sort(Comparator.comparingInt(BlockVec::blockY));
            this.leaves.sort(Comparator.comparingInt(BlockVec::blockY));
            this.roots.sort(Comparator.comparingInt(BlockVec::blockY));
        }

        public void placeVine(BlockVec position, String directionProperty) {
            this.setBlock(position, Block.VINE.withProperty(directionProperty, "true"));
        }

        public void setBlock(BlockVec position, Block block) {
            this.decorationSetter.accept(position, block);
        }

        public boolean isAir(BlockVec position) {
            return this.level.getBlock(position).air();
        }

        public boolean checkBlock(BlockVec position, Predicate<Block> predicate) {
            return predicate.test(this.level.getBlock(position));
        }

        public boolean isReplaceable(BlockVec position) {
            return this.level.getBlock(position).replaceable();
        }

        public boolean isWaterOrWaterNearby(BlockVec position) {
            for (var offset : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, -1}, {0, 1}}) {
                if (this.level
                        .getBlock(position.add(offset[0], 0, offset[1]))
                        .compare(Block.WATER)) {
                    return true;
                }
            }

            return false;
        }

        public boolean placeFeature(ConfiguredFeature<?> feature, BlockVec origin) {
            return this.featurePlacer.place(feature, origin);
        }

        public Block.Getter level() {
            return this.level;
        }

        public RandomSource random() {
            return this.random;
        }

        public List<BlockVec> logs() {
            return this.logs;
        }

        public List<BlockVec> leaves() {
            return this.leaves;
        }

        public List<BlockVec> roots() {
            return this.roots;
        }
    }
}
