package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SingleBlockPillarConfiguration;

/**
 * Port of vanilla {@code SingleBlockPillarFeature}: extends a pillar of one block from the origin
 * while the position stays replaceable, then runs the cap feature at the last placed position.
 */
public final class SingleBlockPillarFeature implements Feature<SingleBlockPillarConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<SingleBlockPillarConfiguration, T> context) {
        var config = context.config();
        var level = context.accessor();
        var random = context.random();
        var predicateContext = Feature.predicateContext(context);
        var position = context.origin();

        while (config.canReplace().test(predicateContext, position)
                && random.nextFloat() < config.chanceToContinue()
                && position.blockY() >= context.minY()
                && position.blockY() <= context.maxY()) {
            level.setBlock(position, config.block().getState(level, random, position));
            position = config.direction().relative(position);
        }

        position = config.direction().opposite().relative(position);
        if (config.capFeature() != null) {
            RandomSelectorFeature.placePlacedFeature(context, null, config.capFeature(), position);
        }

        return true;
    }
}
