package rocks.minestom.worldgen.feature.trunkplacers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.BlockProperties;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code PoplarTrunkPlacer}: a straight trunk that sprouts sideways log branches at
 * the level below the foliage.
 */
public record PoplarTrunkPlacer(
        int baseHeight,
        int heightRandA,
        int heightRandB,
        IntProvider trunkHeightAboveBranches,
        IntProvider branchAmount)
        implements TrunkPlacer {
    public static final Codec<PoplarTrunkPlacer> CODEC =
            StructCodec.struct(
                    "base_height",
                    Codec.INT,
                    PoplarTrunkPlacer::baseHeight,
                    "height_rand_a",
                    Codec.INT,
                    PoplarTrunkPlacer::heightRandA,
                    "height_rand_b",
                    Codec.INT,
                    PoplarTrunkPlacer::heightRandB,
                    "trunk_height_above_branches",
                    IntProvider.CODEC,
                    PoplarTrunkPlacer::trunkHeightAboveBranches,
                    "branch_amount",
                    IntProvider.CODEC,
                    PoplarTrunkPlacer::branchAmount,
                    PoplarTrunkPlacer::new);

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            int freeTreeHeight,
            BlockVec basePos,
            TreeConfiguration config) {
        TrunkPlacer.setDirtAt(getter, logSetter, random, basePos.sub(0, 1, 0), config);
        var trunkHeightUpToFoliageBranches =
                freeTreeHeight - this.trunkHeightAboveBranches.sample(random);

        for (var y = 0; y < freeTreeHeight; y++) {
            this.placeLog(getter, logSetter, random, basePos.add(0, y, 0), config);
            var directions = shuffledHorizontalDirections(random);
            if (trunkHeightUpToFoliageBranches - 1 == y) {
                var branches = this.branchAmount.sample(random);
                for (var branch = 0; branch < branches; branch++) {
                    var direction = directions.get(branch);
                    this.placeLog(
                            getter,
                            logSetter,
                            random,
                            direction.relative(basePos.add(0, y, 0)),
                            config,
                            state -> BlockProperties.trySet(state, "axis", direction.axisName()));
                }
            }
        }

        return List.of(
                new FoliagePlacer.FoliageAttachment(
                        basePos.add(0, trunkHeightUpToFoliageBranches, 0), 0, false));
    }

    @Override
    public int getTreeHeight(RandomSource random) {
        return this.baseHeight
                + random.nextInt(this.heightRandA + 1)
                + random.nextInt(this.heightRandB + 1);
    }

    /**
     * Vanilla {@code Direction.allShuffled} (a Fisher-Yates over all six) without the vertical
     * ones.
     */
    private static List<Direction> shuffledHorizontalDirections(RandomSource random) {
        var all = new ArrayList<>(List.of(Direction.values()));
        for (var i = all.size(); i > 1; i--) {
            var target = random.nextInt(i);
            var swapped = all.get(target);
            all.set(target, all.get(i - 1));
            all.set(i - 1, swapped);
        }

        all.removeIf(direction -> direction == Direction.UP || direction == Direction.DOWN);
        return all;
    }
}
