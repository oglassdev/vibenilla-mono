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

public record SteppedColumnClusterConfiguration(
        BlockStateProvider block,
        PlacementModifiers.BlockPredicate continueThrough,
        PlacementModifiers.BlockPredicate canReplace,
        Set<Key> cannotPlaceOn,
        IntProvider clusterReach,
        IntProvider columnCount,
        IntProvider columnReach,
        IntProvider height)
        implements FeatureConfiguration {

    public static SteppedColumnClusterConfiguration fromJson(
            JsonElement json, BlockTagManager blockTags) {
        var object = json.getAsJsonObject();
        return new SteppedColumnClusterConfiguration(
                BlockStateProviders.fromJson(object.get("block")),
                PlacementModifiers.parseBlockPredicate(object.getAsJsonObject("continue_through")),
                PlacementModifiers.parseBlockPredicate(object.getAsJsonObject("can_replace")),
                BlockSets.parse(object.get("cannot_place_on"), blockTags),
                IntProvider.fromJson(object.get("cluster_reach")),
                IntProvider.fromJson(object.get("column_count")),
                IntProvider.fromJson(object.get("column_reach")),
                IntProvider.fromJson(object.get("height")));
    }
}
