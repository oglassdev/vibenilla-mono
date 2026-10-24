package rocks.minestom.worldgen.feature;

import java.util.List;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.placement.PlacementContext;

/**
 * Port of vanilla's {@code RandomSelectorFeature}: each entry gets a chance roll; the first hit is
 * placed, otherwise the default feature is. Entries are placed features (inline or registry
 * references), matching vanilla.
 */
public class RandomSelectorFeature implements Feature<FeatureConfiguration> {
    private final PlacedFeature defaultFeature;
    private final List<WeightedFeature> features;

    public RandomSelectorFeature(PlacedFeature defaultFeature, List<WeightedFeature> features) {
        this.defaultFeature = defaultFeature;
        this.features = features;
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<FeatureConfiguration, T> context) {
        return false;
    }

    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<FeatureConfiguration, T> context, FeatureLoader loader) {
        var random = context.random();

        var trace = TreeFeature.TRACE;
        var index = 0;
        for (var weightedFeature : this.features) {
            if (random.nextFloat() < weightedFeature.chance) {
                if (trace) {
                    System.out.println(
                            "TRACE selector "
                                    + context.origin()
                                    + " -> entry "
                                    + index
                                    + " "
                                    + weightedFeature.feature.feature());
                }
                return placePlacedFeature(context, loader, weightedFeature.feature);
            }
            index++;
        }

        if (this.defaultFeature == null) {
            return false;
        }

        if (trace) {
            System.out.println(
                    "TRACE selector "
                            + context.origin()
                            + " -> default "
                            + this.defaultFeature.feature());
        }
        return placePlacedFeature(context, loader, this.defaultFeature);
    }

    /**
     * Places a nested placed feature the way vanilla's {@code PlacedFeature#place} does: the
     * placement modifier pipeline runs first (depth-first, sharing the feature random) and the
     * feature is placed at each surviving position. The placement context is a minimal one built
     * over the feature accessor, so heightmap and biome lookups are not available to nested
     * modifiers.
     */
    static <T extends Block.Getter & Block.Setter> boolean placePlacedFeature(
            FeaturePlaceContext<?, T> context, FeatureLoader loader, PlacedFeature placedFeature) {
        return placePlacedFeature(context, loader, placedFeature, context.origin());
    }

    static <T extends Block.Getter & Block.Setter> boolean placePlacedFeature(
            FeaturePlaceContext<?, T> context,
            FeatureLoader loader,
            PlacedFeature placedFeature,
            BlockVec origin) {
        if (placedFeature == null) {
            return false;
        }

        placedFeature = placedFeature.resolve(loader);
        if (placedFeature == null) {
            return false;
        }

        var configuredFeature = placedFeature.configuredFeature(loader);
        if (configuredFeature == null) {
            return false;
        }

        var placementContext =
                new PlacementContext(
                        context.accessor(),
                        0,
                        0,
                        0,
                        0,
                        null,
                        null,
                        context.minY(),
                        context.maxY(),
                        context.seaLevel(),
                        null,
                        null,
                        loader);

        var placedAny = new boolean[1];
        placedFeature.place(
                placementContext,
                context.random(),
                origin,
                (position, featureRandom) -> {
                    var newContext =
                            new FeaturePlaceContext<>(
                                    context.accessor(),
                                    featureRandom,
                                    position,
                                    configuredFeature.config(),
                                    context.worldSeed(),
                                    context.minY(),
                                    context.maxY(),
                                    context.seaLevel());

                    var featureImpl = configuredFeature.feature();
                    boolean placed;
                    if (featureImpl instanceof RandomSelectorFeature randomSelector) {
                        placed = randomSelector.place((FeaturePlaceContext) newContext, loader);
                    } else {
                        placed = ((Feature) featureImpl).place(newContext);
                    }

                    if (placed) {
                        placedAny[0] = true;
                        if (featureImpl instanceof UnderwaterMagmaFeature) {
                            UnderwaterMagmaFeature.convertBubbleColumnsAfterPlacement(
                                    newContext.accessor(), (FeaturePlaceContext) newContext);
                        }
                    }
                });

        return placedAny[0];
    }

    public record WeightedFeature(float chance, PlacedFeature feature) {}
}
