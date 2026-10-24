package rocks.minestom.worldgen.feature;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public final class FeatureLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureLoader.class);

    private static final Codec<BiomeFeatures> BIOME_FEATURES_CODEC =
            StructCodec.struct(
                    "features",
                    Codec.KEY.list().list().optional(List.of()),
                    BiomeFeatures::features,
                    BiomeFeatures::new);

    private final DataPack dataPack;
    private final BlockTagManager blockTags;
    private final BlockSupports blockSupports;
    private final Map<Key, Optional<ConfiguredFeature<?>>> configuredFeatureCache;
    private final Map<Key, Optional<PlacedFeature>> placedFeatureCache;
    private final Map<Key, Optional<BlockStateProvider>> blockStateProviderCache;
    private final Map<Key, List<List<Key>>> biomeFeatureCache;
    private final Map<Key, Set<Key>> biomeFeatureSetCache;
    private final Map<List<Key>, List<FeatureSorter.StepFeatureData>> featuresPerStepCache;

    public DataPack dataPack() {
        return this.dataPack;
    }

    public BlockTagManager blockTags() {
        return this.blockTags;
    }

    public BlockSupports blockSupports() {
        return this.blockSupports;
    }

    public FeatureLoader(DataPack dataPack) {
        this.dataPack = dataPack;
        this.blockTags = new BlockTagManager(dataPack.rootPath());
        this.blockSupports = new BlockSupports(this.blockTags);
        this.configuredFeatureCache = new ConcurrentHashMap<>();
        this.placedFeatureCache = new ConcurrentHashMap<>();
        this.blockStateProviderCache = new ConcurrentHashMap<>();
        this.biomeFeatureCache = new ConcurrentHashMap<>();
        this.biomeFeatureSetCache = new ConcurrentHashMap<>();
        this.featuresPerStepCache = new ConcurrentHashMap<>();
    }

    /**
     * Deterministic global feature ordering per decoration step for the given biome set, mirroring
     * vanilla's FeatureSorter result. Cached per biome list.
     */
    public List<FeatureSorter.StepFeatureData> featuresPerStep(List<Key> possibleBiomes) {
        return this.featuresPerStepCache.computeIfAbsent(
                possibleBiomes,
                biomes -> FeatureSorter.buildFeaturesPerStep(biomes, this::getBiomeFeatures));
    }

    /** Whether the biome's feature lists contain the placed feature, at any step. */
    public boolean biomeHasFeature(Key biomeId, Key placedFeatureId) {
        var featureSet =
                this.biomeFeatureSetCache.computeIfAbsent(
                        biomeId,
                        biome -> {
                            var set = new HashSet<Key>();
                            for (var step : this.getBiomeFeatures(biome)) {
                                set.addAll(step);
                            }
                            return set;
                        });
        return featureSet.contains(placedFeatureId);
    }

    /** The feature with the given id, or null when its data is missing or invalid. */
    public ConfiguredFeature<?> getConfiguredFeature(Key id) {
        return this.cached(this.configuredFeatureCache, "feature", id, this::parseFeature);
    }

    /** The placed feature with the given id, or null when its data is missing or invalid. */
    public PlacedFeature getPlacedFeature(Key id) {
        return this.cached(this.placedFeatureCache, "placed feature", id, this::parsePlacedFeature);
    }

    /**
     * The shared block state provider stored in {@code worldgen/block_state_provider}, referenced
     * by id from other worldgen data; null when its data is missing or invalid.
     */
    public BlockStateProvider getBlockStateProvider(Key id) {
        return this.cached(
                this.blockStateProviderCache,
                "block state provider",
                id,
                this::parseBlockStateProvider);
    }

    /** Whether a shared block state provider with the given id exists in the data pack. */
    public boolean hasBlockStateProvider(Key id) {
        return Files.isRegularFile(
                this.dataPack
                        .rootPath()
                        .resolve("data")
                        .resolve(id.namespace())
                        .resolve("worldgen/block_state_provider")
                        .resolve(id.value() + ".json"));
    }

    public List<List<Key>> getBiomeFeatures(Key biomeId) {
        return this.biomeFeatureCache.computeIfAbsent(biomeId, this::loadBiomeFeatures);
    }

    ConfiguredFeature<?> parseFeature(Key id) {
        var json = this.dataPack.readFeature(id);
        return this.inParseScope(() -> Features.parseConfiguredFeature(json, this.blockTags));
    }

    PlacedFeature parsePlacedFeature(Key id) {
        var json = this.dataPack.readPlacedFeature(id);
        return this.inParseScope(() -> PlacedFeature.fromJson(json));
    }

    BlockStateProvider parseBlockStateProvider(Key id) {
        var json = this.dataPack.readBlockStateProvider(id);
        return this.inParseScope(() -> BlockStateProviders.fromJson(json));
    }

    List<List<Key>> parseBiomeFeatures(Key biomeId) {
        var json = this.dataPack.readBiome(biomeId);
        return BIOME_FEATURES_CODEC.decode(Transcoder.JSON, json).orElseThrow().features();
    }

    /**
     * Runs a parse with this loader and its block tags as the parse scope, so nested configurations
     * can reference by-id data and resolve tags.
     */
    private <T> T inParseScope(Supplier<T> parse) {
        var previousBlockTags = PlacementModifiers.currentBlockTags();
        var previousLoader = Features.currentLoader();
        PlacementModifiers.currentBlockTags(this.blockTags);
        Features.currentLoader(this);
        try {
            return parse.get();
        } finally {
            PlacementModifiers.currentBlockTags(previousBlockTags);
            Features.currentLoader(previousLoader);
        }
    }

    private <T> T cached(
            Map<Key, Optional<T>> cache, String kind, Key id, Function<Key, T> parser) {
        var entry = cache.get(id);
        if (entry == null) {
            entry = this.loadQuietly(kind, id, parser);
            cache.put(id, entry);
        }
        return entry.orElse(null);
    }

    private <T> Optional<T> loadQuietly(String kind, Key id, Function<Key, T> parser) {
        try {
            return Optional.of(parser.apply(id));
        } catch (Exception exception) {
            LOGGER.warn("Failed to load {} {}", kind, id.asString(), exception);
            return Optional.empty();
        }
    }

    private List<List<Key>> loadBiomeFeatures(Key biomeId) {
        try {
            return this.parseBiomeFeatures(biomeId);
        } catch (Exception exception) {
            LOGGER.warn("Failed to load features of biome {}", biomeId.asString(), exception);
            return List.of();
        }
    }

    private record BiomeFeatures(List<List<Key>> features) {}
}
