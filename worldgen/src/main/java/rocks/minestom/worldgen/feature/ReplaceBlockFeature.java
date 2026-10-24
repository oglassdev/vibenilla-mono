package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.ReplaceBlockConfiguration;

/**
 * Port of vanilla {@code ReplaceBlockFeature}: the first matching replacement rewrites the origin.
 */
public final class ReplaceBlockFeature implements Feature<ReplaceBlockConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<ReplaceBlockConfiguration, T> context) {
        var origin = context.origin();
        var current = context.accessor().getBlock(origin);
        for (var replacement : context.config().replacements()) {
            if (replacement.target().test(current, origin, context.random())) {
                context.accessor().setBlock(origin, replacement.state());
                break;
            }
        }

        return true;
    }
}
