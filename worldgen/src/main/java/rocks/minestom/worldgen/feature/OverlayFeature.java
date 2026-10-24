package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.OverlayConfiguration;

/** Port of vanilla {@code OverlayFeature}: places every listed placed feature at the origin. */
public final class OverlayFeature implements Feature<OverlayConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<OverlayConfiguration, T> context) {
        var placedAny = false;
        for (var feature : context.config().features()) {
            placedAny |= RandomSelectorFeature.placePlacedFeature(context, null, feature);
        }

        return placedAny;
    }
}
