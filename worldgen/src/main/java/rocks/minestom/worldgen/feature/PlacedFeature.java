package rocks.minestom.worldgen.feature;

import com.google.gson.JsonElement;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.feature.placement.PlacementContext;
import rocks.minestom.worldgen.feature.placement.PlacementModifier;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * A feature with its placement modifiers, or a reference to a registered placed feature.
 *
 * @param reference the id of a registered placed feature when this is a reference, else null
 * @param feature the id of the configured feature to place when it is not inline
 * @param loader the loader that parsed this, used to resolve ids when the caller supplies none
 */
public record PlacedFeature(
        Key reference,
        Key feature,
        ConfiguredFeature<?> inlineFeature,
        List<PlacementModifier> placement,
        FeatureLoader loader) {

    /** Parses a placed feature given inline, or as a registry reference by id. */
    public static PlacedFeature fromJson(JsonElement json) {
        var loader = Features.currentLoader();
        if (json.isJsonPrimitive()) {
            return new PlacedFeature(Key.key(json.getAsString()), null, null, List.of(), loader);
        }

        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("PlacedFeature must be a JSON object or an id");
        }

        var object = json.getAsJsonObject();
        var featureElement = object.get("feature");
        Key featureKey = null;
        ConfiguredFeature<?> inlineConfiguredFeature = null;

        if (featureElement.isJsonPrimitive()) {
            featureKey = Key.key(featureElement.getAsString());
        } else {
            inlineConfiguredFeature = Features.parseConfiguredFeature(featureElement);
        }

        var placementModifiers = PlacementModifiers.parse(object.getAsJsonArray("placement"));
        return new PlacedFeature(
                null, featureKey, inlineConfiguredFeature, placementModifiers, loader);
    }

    /**
     * The placed feature to run: this one, or the registered one a reference points to (null when
     * it cannot be loaded).
     */
    public PlacedFeature resolve(FeatureLoader loader) {
        if (this.reference == null) {
            return this;
        }

        var source = loader != null ? loader : this.loader;
        return source != null ? source.getPlacedFeature(this.reference) : null;
    }

    public ConfiguredFeature<?> configuredFeature(FeatureLoader loader) {
        if (this.inlineFeature != null) {
            return this.inlineFeature;
        }

        var source = loader != null ? loader : this.loader;
        if (this.feature == null || source == null) {
            return null;
        }

        return source.getConfiguredFeature(this.feature);
    }

    public List<BlockVec> getPositions(
            PlacementContext context, RandomSource random, BlockVec origin) {
        if (this.placement.isEmpty()) {
            return List.of(origin);
        }

        return PlacementModifiers.apply(this.placement, context, random, origin);
    }

    /**
     * Runs the placement modifier pipeline lazily (depth-first per position, like vanilla's stream
     * pipeline) so the shared random is consumed in vanilla order, placing the feature at each
     * surviving position.
     */
    public void place(
            PlacementContext context,
            RandomSource random,
            BlockVec origin,
            PositionConsumer placer) {
        var pmodChunks = System.getProperty("worldgen.pmodChunks", "");
        var pmodGated =
                !pmodChunks.isEmpty()
                        && java.util.Arrays.asList(pmodChunks.split(";"))
                                .contains((origin.blockX() >> 4) + "," + (origin.blockZ() >> 4));
        this.placeRecursive(context, random, origin, 0, pmodGated, placer);
    }

    private void placeRecursive(
            PlacementContext context,
            RandomSource random,
            BlockVec position,
            int modifierIndex,
            boolean pmodGated,
            PositionConsumer placer) {
        if (modifierIndex >= this.placement.size()) {
            placer.accept(position, random);
            return;
        }

        var modifier = this.placement.get(modifierIndex);
        var positions = modifier.apply(context, random, position);
        for (var next : positions) {
            if (pmodGated) {
                System.out.println(
                        "PMOD "
                                + context.currentFeature()
                                + " stage="
                                + modifierIndex
                                + " "
                                + modifier.getClass().getSimpleName()
                                + " pos="
                                + next.blockX()
                                + ","
                                + next.blockY()
                                + ","
                                + next.blockZ());
            }
            this.placeRecursive(context, random, next, modifierIndex + 1, pmodGated, placer);
        }
    }

    public interface PositionConsumer {
        void accept(BlockVec position, RandomSource random);
    }
}
