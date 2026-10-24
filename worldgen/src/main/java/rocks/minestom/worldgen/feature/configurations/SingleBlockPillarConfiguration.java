package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.PlacedFeature;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

/** A null {@code capFeature} means no cap. */
public record SingleBlockPillarConfiguration(
        BlockStateProvider block,
        PlacementModifiers.BlockPredicate canReplace,
        Direction direction,
        float chanceToContinue,
        PlacedFeature capFeature)
        implements FeatureConfiguration {

    public static SingleBlockPillarConfiguration fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        var direction = Direction.fromSerializedName(object.get("direction").getAsString());
        if (direction != Direction.UP && direction != Direction.DOWN) {
            throw new IllegalArgumentException("Pillar direction must be vertical: " + direction);
        }

        return new SingleBlockPillarConfiguration(
                BlockStateProviders.fromJson(object.get("block")),
                object.has("can_replace")
                        ? PlacementModifiers.parseBlockPredicate(
                                object.getAsJsonObject("can_replace"))
                        : PlacementModifiers.BlockPredicate.ALWAYS_TRUE,
                direction,
                object.has("chance_to_continue")
                        ? object.get("chance_to_continue").getAsFloat()
                        : 1.0F,
                object.has("cap_feature")
                        ? PlacedFeature.fromJson(object.get("cap_feature"))
                        : null);
    }
}
