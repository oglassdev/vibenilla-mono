package rocks.minestom.worldgen.feature.placement;

import com.google.gson.JsonElement;

/**
 * Vanilla {@code VerticalAnchor}: a height given relative to the world bottom, top or sea level.
 */
record HeightAnchor(Kind kind, int value) {
    enum Kind {
        ABSOLUTE("absolute"),
        ABOVE_BOTTOM("above_bottom"),
        BELOW_TOP("below_top"),
        RELATIVE_TO_SEA_LEVEL("relative_to_sea_level");

        private final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    static HeightAnchor fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        if (object.size() != 1) {
            throw new IllegalArgumentException("Height anchor needs exactly one entry: " + json);
        }

        var entry = object.entrySet().iterator().next();
        for (var kind : Kind.values()) {
            if (kind.key.equals(entry.getKey())) {
                return new HeightAnchor(kind, entry.getValue().getAsInt());
            }
        }

        throw new IllegalArgumentException("Unknown height anchor " + entry.getKey());
    }

    int resolveY(PlacementContext context) {
        return switch (this.kind) {
            case ABSOLUTE -> this.value;
            case ABOVE_BOTTOM -> context.minY() + this.value;
            case BELOW_TOP -> context.maxY() - this.value;
            case RELATIVE_TO_SEA_LEVEL -> context.seaLevel() + this.value;
        };
    }
}
