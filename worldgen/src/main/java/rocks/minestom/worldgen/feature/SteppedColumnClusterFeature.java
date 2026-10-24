package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SteppedColumnClusterConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementContext;

/**
 * Port of vanilla {@code SteppedColumnClusterFeature}: a cluster of columns whose height steps down
 * with the Manhattan distance from the cluster centre.
 */
public final class SteppedColumnClusterFeature
        implements Feature<SteppedColumnClusterConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<SteppedColumnClusterConfiguration, T> context) {
        var config = context.config();
        var level = context.accessor();
        var random = context.random();
        var predicates = Feature.predicateContext(context);
        var origin = context.origin();
        if (!canPlaceAt(config, level, predicates, origin)) {
            return false;
        }

        var columnHeight = config.height().sample(random);
        var clusterReach = Math.min(columnHeight, config.clusterReach().sample(random));
        var count = config.columnCount().sample(random);
        var placed = false;

        // Vanilla BlockPos.randomBetweenClosed: each position draws x, y (span 1) and z in turn
        for (var attempt = 0; attempt < count; attempt++) {
            var x = origin.blockX() - clusterReach + random.nextInt(clusterReach * 2 + 1);
            var y = origin.blockY() + random.nextInt(1);
            var z = origin.blockZ() - clusterReach + random.nextInt(clusterReach * 2 + 1);
            var position = new BlockVec(x, y, z);
            var blocksToPlaceY = columnHeight - BlockPosIterators.distManhattan(position, origin);
            if (blocksToPlaceY >= 0) {
                placed |=
                        placeColumn(
                                context,
                                predicates,
                                position,
                                blocksToPlaceY,
                                config.columnReach().sample(random));
            }
        }

        return placed;
    }

    private static <T extends Block.Getter & Block.Setter> boolean placeColumn(
            FeaturePlaceContext<SteppedColumnClusterConfiguration, T> context,
            PlacementContext predicates,
            BlockVec origin,
            int columnHeight,
            int reach) {
        var config = context.config();
        var level = context.accessor();
        var placedAny = false;

        // Vanilla BlockPos.betweenClosed over the y-plane: x varies fastest, then z
        for (var z = origin.blockZ() - reach; z <= origin.blockZ() + reach; z++) {
            for (var x = origin.blockX() - reach; x <= origin.blockX() + reach; x++) {
                var position = new BlockVec(x, origin.blockY(), z);
                var stepLimit = BlockPosIterators.distManhattan(position, origin);
                var columnPosition =
                        config.canReplace().test(predicates, position)
                                ? findSurface(
                                        config,
                                        level,
                                        predicates,
                                        position,
                                        stepLimit,
                                        context.minY())
                                : findAir(config, level, position, stepLimit, context.maxY());
                if (columnPosition == null) {
                    continue;
                }

                var blocksY = columnHeight - stepLimit / 2;
                for (var cursor = columnPosition; blocksY >= 0; blocksY--) {
                    if (config.canReplace().test(predicates, cursor)) {
                        level.setBlock(
                                cursor, config.block().getState(level, context.random(), cursor));
                        cursor = cursor.add(0, 1, 0);
                        placedAny = true;
                    } else {
                        if (!config.continueThrough().test(predicates, cursor)) {
                            break;
                        }

                        cursor = cursor.add(0, 1, 0);
                    }
                }
            }
        }

        return placedAny;
    }

    private static BlockVec findSurface(
            SteppedColumnClusterConfiguration config,
            Block.Getter level,
            PlacementContext predicates,
            BlockVec start,
            int limit,
            int minY) {
        var cursor = start;
        while (cursor.blockY() > minY + 1 && limit > 0) {
            limit--;
            if (canPlaceAt(config, level, predicates, cursor)) {
                return cursor;
            }

            cursor = cursor.add(0, -1, 0);
        }

        return null;
    }

    private static boolean canPlaceAt(
            SteppedColumnClusterConfiguration config,
            Block.Getter level,
            PlacementContext predicates,
            BlockVec position) {
        if (!config.canReplace().test(predicates, position)) {
            return false;
        }

        var below = level.getBlock(position.add(0, -1, 0));
        return !below.air() && !config.cannotPlaceOn().contains(below.key());
    }

    private static BlockVec findAir(
            SteppedColumnClusterConfiguration config,
            Block.Getter level,
            BlockVec start,
            int limit,
            int maxY) {
        var cursor = start;
        while (cursor.blockY() <= maxY && limit > 0) {
            limit--;
            var block = level.getBlock(cursor);
            if (config.cannotPlaceOn().contains(block.key())) {
                return null;
            }

            if (block.air()) {
                return cursor;
            }

            cursor = cursor.add(0, 1, 0);
        }

        return null;
    }
}
