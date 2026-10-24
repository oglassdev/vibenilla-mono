package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import java.util.Set;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public record RandomNeighborSpreadConfiguration(
        BlockStateProvider block,
        Set<Key> acceptedNeighbors,
        PlacementModifiers.BlockPredicate canReplace,
        IntProvider attempts,
        IntProvider xzOffset,
        IntProvider yOffset)
        implements FeatureConfiguration {

    public static RandomNeighborSpreadConfiguration fromJson(
            JsonElement json, BlockTagManager blockTags) {
        var object = json.getAsJsonObject();
        return new RandomNeighborSpreadConfiguration(
                BlockStateProviders.fromJson(object.get("block")),
                BlockSets.parse(object.get("accepted_neighbors"), blockTags),
                PlacementModifiers.parseBlockPredicate(object.getAsJsonObject("can_replace")),
                IntProvider.fromJson(object.get("attempts")),
                IntProvider.fromJson(object.get("xz_offset")),
                IntProvider.fromJson(object.get("y_offset")));
    }
}
