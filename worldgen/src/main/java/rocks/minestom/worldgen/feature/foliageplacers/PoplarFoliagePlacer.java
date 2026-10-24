package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.BlockProperties;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code PoplarFoliagePlacer}: a tall rhombus-shaped crown with random holes in its
 * sides and sideways logs where its widest rows meet the axes.
 */
public record PoplarFoliagePlacer(
        IntProvider radius, IntProvider offset, IntProvider height, float sideHoleChance)
        implements FoliagePlacer {
    public static final Codec<PoplarFoliagePlacer> CODEC =
            StructCodec.struct(
                    "radius",
                    IntProvider.CODEC,
                    PoplarFoliagePlacer::radius,
                    "offset",
                    IntProvider.CODEC,
                    PoplarFoliagePlacer::offset,
                    "height",
                    IntProvider.CODEC,
                    PoplarFoliagePlacer::height,
                    "side_hole_chance",
                    Codec.FLOAT,
                    PoplarFoliagePlacer::sideHoleChance,
                    PoplarFoliagePlacer::new);

    @Override
    public void createFoliage(
            Block.Getter getter,
            FoliageSetter foliageSetter,
            RandomSource random,
            TreeConfiguration config,
            int maxFreeTreeHeight,
            FoliageAttachment attachment,
            int foliageHeight,
            int foliageRadius) {
        var doubleTrunk = attachment.doubleTrunk();
        var foliagePos = attachment.pos().add(0, this.offset.sample(random), 0);
        var currentRadius = foliageRadius + attachment.radiusOffset() - 1;
        var flipRhombusShape = random.nextBoolean();
        var context =
                new Crown(
                        getter,
                        foliageSetter,
                        random,
                        config,
                        foliagePos,
                        doubleTrunk,
                        foliageHeight,
                        flipRhombusShape);

        context.placeLeavesRow(currentRadius - 2, foliageHeight - 1);
        context.placeLeavesRow(currentRadius - 1, foliageHeight - 2);
        context.placeLeavesRow(currentRadius - 1, foliageHeight - 3);
        for (var y = foliageHeight - 4; y >= 1; y--) {
            context.placeLeavesRow(currentRadius, y);
        }

        context.replaceLeavesWithLog(currentRadius, foliageHeight - 4);
        context.placeLeavesRow(currentRadius - 1, 0);
        context.placeLeavesRow(Math.clamp(currentRadius - 2, 1, 2), -1);
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height.sample(random);
    }

    @Override
    public int foliageRadius(RandomSource random, int baseHeight) {
        return this.radius.sample(random);
    }

    private final class Crown {
        private final Block.Getter getter;
        private final FoliageSetter setter;
        private final RandomSource random;
        private final TreeConfiguration config;
        private final BlockVec origin;
        private final boolean doubleTrunk;
        private final int foliageHeight;
        private final boolean flipRhombusShape;

        private Crown(
                Block.Getter getter,
                FoliageSetter setter,
                RandomSource random,
                TreeConfiguration config,
                BlockVec origin,
                boolean doubleTrunk,
                int foliageHeight,
                boolean flipRhombusShape) {
            this.getter = getter;
            this.setter = setter;
            this.random = random;
            this.config = config;
            this.origin = origin;
            this.doubleTrunk = doubleTrunk;
            this.foliageHeight = foliageHeight;
            this.flipRhombusShape = flipRhombusShape;
        }

        private void placeLeavesRow(int currentRadius, int y) {
            var offset = this.doubleTrunk ? 1 : 0;
            for (var dx = -currentRadius; dx <= currentRadius + offset; dx++) {
                for (var dz = -currentRadius; dz <= currentRadius + offset; dz++) {
                    if (!this.shouldSkipLocation(dx, y, dz, currentRadius)) {
                        FoliagePlacer.tryPlaceLeaf(
                                this.getter,
                                this.setter,
                                this.random,
                                this.config,
                                this.origin.add(dx, y, dz));
                    }
                }
            }
        }

        private void replaceLeavesWithLog(int currentRadius, int y) {
            var offset = this.doubleTrunk ? 1 : 0;
            for (var dx = -currentRadius; dx <= currentRadius + offset; dx++) {
                for (var dz = -currentRadius; dz <= currentRadius + offset; dz++) {
                    var absDx = Math.abs(dx);
                    var absDz = Math.abs(dz);
                    if (isWithinRhombusShape(
                                    currentRadius,
                                    absDx,
                                    absDz,
                                    this.cornerBlocksToCut(
                                            dx, dz, currentRadius, this.isPartialRhombusRow(y)),
                                    2)
                            && (absDz == 0 && currentRadius - absDx >= 4
                                    || absDx == 0 && currentRadius - absDz >= 4)) {
                        this.tryPlaceLog(this.origin.add(dx, y, dz), absDz == 0 ? "x" : "z");
                    }
                }
            }
        }

        private void tryPlaceLog(BlockVec position, String axis) {
            var foliage =
                    this.config.foliageProvider().getState(this.getter, this.random, position);
            if (this.getter.getBlock(position).compare(foliage, Block.Comparator.STATE)) {
                var log = this.config.trunkProvider().getState(this.getter, this.random, position);
                this.setter.set(position, BlockProperties.trySet(log, "axis", axis));
            }
        }

        private boolean shouldSkipLocation(int dx, int y, int dz, int currentRadius) {
            var partialRow = this.isPartialRhombusRow(y);
            var cornerBlocksToCut = this.cornerBlocksToCut(dx, dz, currentRadius, partialRow);
            var absDx = Math.abs(dx);
            var absDz = Math.abs(dz);
            if (partialRow && (absDx == currentRadius || absDz == currentRadius)) {
                return true;
            }

            var additionalSideRemoval =
                    this.random.nextFloat() <= PoplarFoliagePlacer.this.sideHoleChance ? 1 : 0;
            return !isWithinRhombusShape(
                    currentRadius, absDx, absDz, cornerBlocksToCut, additionalSideRemoval);
        }

        private int cornerBlocksToCut(int dx, int dz, int currentRadius, boolean partialRow) {
            var smallCorner =
                    this.flipRhombusShape
                            ? dx > 0 && dz > 0 || dz < 0 && dx < 0
                            : dx > 0 && dz < 0 || dz > 0 && dx < 0;
            if (smallCorner) {
                return currentRadius - 1;
            }

            return partialRow ? currentRadius + 1 : currentRadius;
        }

        private boolean isPartialRhombusRow(int y) {
            return this.foliageHeight - 1 == y || this.foliageHeight - 2 == y;
        }
    }

    private static boolean isWithinRhombusShape(
            int currentRadius,
            int absDx,
            int absDz,
            int cornerBlocksToCut,
            int additionalSideRemoval) {
        return absDx + absDz <= currentRadius * 2 - (cornerBlocksToCut + additionalSideRemoval);
    }
}
