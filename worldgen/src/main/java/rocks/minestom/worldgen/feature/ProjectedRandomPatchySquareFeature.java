package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.ProjectedRandomPatchySquareConfiguration;

/**
 * Port of vanilla {@code ProjectedRandomPatchySquare}: a square patch whose columns thin out
 * towards the corners, each projected down through the allowed blocks before placing.
 */
public final class ProjectedRandomPatchySquareFeature
        implements Feature<ProjectedRandomPatchySquareConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<ProjectedRandomPatchySquareConfiguration, T> context) {
        var config = context.config();
        var level = context.accessor();
        var random = context.random();
        var predicateContext = Feature.predicateContext(context);
        var origin = context.origin();
        var size = config.size().sample(random);
        var bound = size * size + 1;

        for (var dx = -size; dx <= size; dx++) {
            for (var dz = -size; dz <= size; dz++) {
                var probability = Math.abs(dx) * Math.abs(dz);
                if (random.nextInt(bound) >= bound - probability) {
                    continue;
                }

                var base = origin.add(dx, 0, dz);
                var drop = config.maxProjectionHeight();
                while (config.projectThrough().test(predicateContext, base.add(0, -1, 0))) {
                    base = base.add(0, -1, 0);
                    if (--drop <= 0) {
                        break;
                    }
                }

                var state = config.block().getOptionalState(level, random, base);
                if (state != null) {
                    level.setBlock(base, state);
                }
            }
        }

        return true;
    }
}
