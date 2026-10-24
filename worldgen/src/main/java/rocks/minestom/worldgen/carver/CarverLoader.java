package rocks.minestom.worldgen.carver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

/**
 * Loads carvers and per-biome carver lists from the datapack, mirroring {@link
 * rocks.minestom.worldgen.feature.FeatureLoader}'s caching. A carver or biome that fails to load is
 * logged once and then treated as absent.
 */
public final class CarverLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CarverLoader.class);

    private final DataPack dataPack;
    private final Map<Key, Optional<List<Key>>> biomeCarverCache;
    private final Map<Key, Optional<WorldCarver>> carverCache;

    public CarverLoader(DataPack dataPack) {
        this.dataPack = dataPack;
        this.biomeCarverCache = new ConcurrentHashMap<>();
        this.carverCache = new ConcurrentHashMap<>();
    }

    /**
     * The biome's carver ids, in datapack order (order defines the carver seed index); empty if the
     * biome failed to load.
     */
    public List<Key> biomeCarvers(Key biomeId) {
        return cached(this.biomeCarverCache, "biome carvers", biomeId, this::loadBiomeCarvers)
                .orElse(List.of());
    }

    /** The carver, or null if it failed to load. */
    public WorldCarver configuredCarver(Key id) {
        return cached(this.carverCache, "carver", id, this::loadCarver).orElse(null);
    }

    private static <T> Optional<T> cached(
            Map<Key, Optional<T>> cache, String kind, Key id, Function<Key, T> loader) {
        return cache.computeIfAbsent(
                id,
                key -> {
                    try {
                        return Optional.of(loader.apply(key));
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Failed to load {} {}: {}", kind, key, exception.toString());
                        return Optional.empty();
                    }
                });
    }

    private List<Key> loadBiomeCarvers(Key biomeId) {
        var carvers = this.dataPack.readBiome(biomeId).getAsJsonObject().get("carvers");
        if (carvers == null) {
            return List.of();
        }
        if (carvers.isJsonPrimitive()) {
            return List.of(Key.key(carvers.getAsString()));
        }
        return keyList(carvers.getAsJsonArray());
    }

    private static List<Key> keyList(JsonArray array) {
        var keys = new ArrayList<Key>(array.size());
        for (var element : array) {
            keys.add(Key.key(element.getAsString()));
        }
        return List.copyOf(keys);
    }

    private WorldCarver loadCarver(Key id) {
        return parseCarver(this.dataPack.readCarver(id));
    }

    /** Parses a carver definition, failing on unknown carver types and malformed fields. */
    public static WorldCarver parseCarver(JsonElement json) {
        var obj = json.getAsJsonObject();
        var type = Key.key(obj.get("type").getAsString()).asString();
        return switch (type) {
            case "minecraft:cave" -> CaveWorldCarver.fromJson(obj);
            case "minecraft:canyon" -> CanyonWorldCarver.fromJson(obj);
            default -> throw new IllegalArgumentException("Unsupported carver type: " + type);
        };
    }

    /** An int provider, additionally supporting {@code very_biased_to_bottom}. */
    static IntProvider parseCount(JsonElement json) {
        if (json.isJsonObject()
                && json.getAsJsonObject().has("type")
                && Key.key(json.getAsJsonObject().get("type").getAsString())
                        .equals(VeryBiasedToBottomIntProvider.TYPE)) {
            var obj = json.getAsJsonObject();
            return new VeryBiasedToBottomIntProvider(
                    obj.get("min_inclusive").getAsInt(), obj.get("max_inclusive").getAsInt());
        }
        return IntProvider.fromJson(json);
    }
}
