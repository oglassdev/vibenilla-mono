package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.RandomNeighborSpreadConfiguration;

/**
 * Port of vanilla {@code RandomNeighborSpreadFeature}: places the block at the origin, then spreads
 * it to random replaceable positions that touch exactly one accepted neighbour.
 */
public final class RandomNeighborSpreadFeature
        implements Feature<RandomNeighborSpreadConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<RandomNeighborSpreadConfiguration, T> context) {
        var config = context.config();
        var level = context.accessor();
        var random = context.random();
        var predicateContext = Feature.predicateContext(context);
        var origin = context.origin();
        level.setBlock(origin, config.block().getState(level, random, origin));

        var attempts = config.attempts().sample(random);
        for (var attempt = 0; attempt < attempts; attempt++) {
            var offsetX = config.xzOffset().sample(random);
            var offsetY = config.yOffset().sample(random);
            var offsetZ = config.xzOffset().sample(random);
            var position = origin.add(offsetX, offsetY, offsetZ);
            if (!config.canReplace().test(predicateContext, position)) {
                continue;
            }

            var neighbors = 0;
            for (var direction : Direction.values()) {
                var neighbor = level.getBlock(direction.relative(position));
                if (config.acceptedNeighbors().contains(neighbor.key())) {
                    neighbors++;
                }

                if (neighbors > 1) {
                    break;
                }
            }

            if (neighbors == 1) {
                level.setBlock(position, config.block().getState(level, random, position));
            }
        }

        return true;
    }
}
