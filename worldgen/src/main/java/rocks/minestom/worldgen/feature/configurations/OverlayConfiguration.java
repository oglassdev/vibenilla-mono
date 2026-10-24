package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.PlacedFeature;

public record OverlayConfiguration(List<PlacedFeature> features) implements FeatureConfiguration {
    public static OverlayConfiguration fromJson(JsonElement json) {
        var features = new ArrayList<PlacedFeature>();
        var entries = json.getAsJsonObject().get("features");
        if (entries.isJsonArray()) {
            for (var feature : entries.getAsJsonArray()) {
                features.add(PlacedFeature.fromJson(feature));
            }
        } else {
            features.add(PlacedFeature.fromJson(entries));
        }

        if (features.isEmpty()) {
            throw new IllegalArgumentException("Overlay features must not be empty");
        }

        return new OverlayConfiguration(List.copyOf(features));
    }
}
