package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

public record ProjectedRandomPatchySquareConfiguration(
        BlockStateProvider block,
        PlacementModifiers.BlockPredicate projectThrough,
        IntProvider size,
        int maxProjectionHeight)
        implements FeatureConfiguration {

    public static ProjectedRandomPatchySquareConfiguration fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        return new ProjectedRandomPatchySquareConfiguration(
                BlockStateProviders.fromJson(object.get("block")),
                PlacementModifiers.parseBlockPredicate(object.getAsJsonObject("project_through")),
                IntProvider.fromJson(object.get("size")),
                object.get("max_projection_height").getAsInt());
    }
}
