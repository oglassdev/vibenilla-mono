package rocks.minestom.worldgen.structure.placement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.structure.StructurePlacement;
import rocks.minestom.worldgen.structure.StructureSet;

public final class StructurePlacements {
    private static final Codec<RandomSpreadPlacementData> RANDOM_SPREAD_CODEC =
            StructCodec.struct(
                    "spacing",
                    Codec.INT,
                    RandomSpreadPlacementData::spacing,
                    "separation",
                    Codec.INT,
                    RandomSpreadPlacementData::separation,
                    "salt",
                    Codec.INT,
                    RandomSpreadPlacementData::salt,
                    "spread_type",
                    Codec.STRING.optional("linear"),
                    RandomSpreadPlacementData::spreadType,
                    "frequency",
                    Codec.FLOAT.optional(1.0f),
                    RandomSpreadPlacementData::frequency,
                    "frequency_reduction_method",
                    Codec.STRING.optional("default"),
                    RandomSpreadPlacementData::frequencyReductionMethod,
                    RandomSpreadPlacementData::new);

    private StructurePlacements() {}

    /**
     * Parses a structure placement, failing on unknown placement types. Exclusion zones reference
     * other sets through {@code structureSets}, resolved lazily.
     */
    public static StructurePlacement parsePlacement(
            JsonElement json, Function<Key, StructureSet> structureSets) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Structure placement must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var type = obj.get("type").getAsString();

        if (type.equals("minecraft:random_spread")) {
            var decoded = RANDOM_SPREAD_CODEC.decode(Transcoder.JSON, json).orElseThrow();
            var spreadType = parseSpreadType(decoded.spreadType());
            var frequencyReduction =
                    FrequencyReduction.fromName(decoded.frequencyReductionMethod());
            return new RandomSpreadPlacement(
                    decoded.spacing(),
                    decoded.separation(),
                    decoded.salt(),
                    spreadType,
                    decoded.frequency(),
                    frequencyReduction,
                    parseExclusionZone(obj, structureSets));
        }

        if (type.equals("minecraft:concentric_rings")) {
            var preferredBiomes = obj.get("preferred_biomes").getAsString();
            var tagKey =
                    Key.key(
                            preferredBiomes.startsWith("#")
                                    ? preferredBiomes.substring(1)
                                    : preferredBiomes);
            return new ConcentricRingsPlacement(
                    obj.get("count").getAsInt(),
                    obj.get("distance").getAsInt(),
                    obj.get("spread").getAsInt(),
                    tagKey);
        }

        throw new IllegalArgumentException("Unsupported structure placement type: " + type);
    }

    private static RandomSpreadPlacement.ExclusionZone parseExclusionZone(
            JsonObject placement, Function<Key, StructureSet> structureSets) {
        if (!placement.has("exclusion_zone")) {
            return null;
        }
        var zone = placement.getAsJsonObject("exclusion_zone");
        var otherSet = Key.key(zone.get("other_set").getAsString());
        return new RandomSpreadPlacement.ExclusionZone(
                () -> {
                    var set = structureSets.apply(otherSet);
                    return set == null ? null : set.placement();
                },
                zone.get("chunk_count").getAsInt());
    }

    private static RandomSpreadType parseSpreadType(String value) {
        return switch (value) {
            case "linear" -> RandomSpreadType.LINEAR;
            case "triangular" -> RandomSpreadType.TRIANGULAR;
            default -> throw new IllegalArgumentException("Unknown spread type: " + value);
        };
    }

    private record RandomSpreadPlacementData(
            int spacing,
            int separation,
            int salt,
            String spreadType,
            float frequency,
            String frequencyReductionMethod) {}
}
