package rocks.minestom.worldgen.feature.stateproviders;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;

/**
 * Decodes block state providers from their three forms: a typed provider object, a block state
 * (plain id or {@code {id, properties}}) providing exactly that state, or the id of a provider
 * stored in {@code worldgen/block_state_provider}.
 */
public final class BlockStateProviders {
    private BlockStateProviders() {}

    public static final Codec<BlockStateProvider> CODEC =
            new Codec<>() {
                @Override
                public <D> Result<D> encode(Transcoder<D> coder, BlockStateProvider value) {
                    return new Result.Error<>("Encoding is not supported");
                }

                @Override
                public <D> Result<BlockStateProvider> decode(Transcoder<D> coder, D value) {
                    if (coder.getString(value) instanceof Result.Ok<String>(var id)) {
                        return decodeString(coder, value, id);
                    }

                    var mapResult = coder.getMap(value);
                    if (!(mapResult instanceof Result.Ok<Transcoder.MapLike<D>>(var map))) {
                        return new Result.Error<>("BlockStateProvider must be a string or object");
                    }

                    if (!map.hasValue("type")) {
                        return BlockCodec.CODEC
                                .decode(coder, value)
                                .mapResult(
                                        state ->
                                                (BlockStateProvider)
                                                        new SimpleStateProvider(state));
                    }

                    var type =
                            Codec.STRING
                                    .decode(coder, map.getValue("type").orElseThrow())
                                    .orElseThrow();
                    return switch (type) {
                        case "minecraft:simple" -> upcast(SimpleStateProvider.CODEC, coder, value);
                        case "minecraft:weighted" ->
                                upcast(WeightedStateProvider.CODEC, coder, value);
                        case "minecraft:randomized_int" ->
                                upcast(RandomizedIntStateProvider.CODEC, coder, value);
                        case "minecraft:rotated" ->
                                upcast(RotatedBlockProvider.CODEC, coder, value);
                        case "minecraft:copy_properties" ->
                                upcast(CopyPropertiesProvider.CODEC, coder, value);
                        case "minecraft:noise" -> upcast(NoiseProvider.CODEC, coder, value);
                        case "minecraft:noise_threshold" ->
                                upcast(NoiseThresholdProvider.CODEC, coder, value);
                        case "minecraft:dual_noise" ->
                                upcast(DualNoiseProvider.CODEC, coder, value);
                        case "minecraft:random_block" ->
                                fromJson(coder, value, RandomBlockProvider::fromJson);
                        case "minecraft:rule_based" ->
                                fromJson(
                                        coder,
                                        value,
                                        (json, tags) -> RuleBasedStateProvider.fromJson(json));
                        default -> new Result.Error<>("Unknown block state provider type: " + type);
                    };
                }
            };

    private static <D> Result<BlockStateProvider> decodeString(
            Transcoder<D> coder, D value, String id) {
        var key = Key.key(id);
        var loader = Features.currentLoader();
        if (loader != null && loader.hasBlockStateProvider(key)) {
            var provider = loader.getBlockStateProvider(key);
            return provider != null
                    ? new Result.Ok<>(provider)
                    : new Result.Error<>("Invalid block state provider: " + id);
        }

        return BlockCodec.CODEC
                .decode(coder, value)
                .mapResult(state -> (BlockStateProvider) new SimpleStateProvider(state));
    }

    private static <D, P extends BlockStateProvider> Result<BlockStateProvider> upcast(
            Codec<P> codec, Transcoder<D> coder, D value) {
        return codec.decode(coder, value).mapResult(provider -> (BlockStateProvider) provider);
    }

    private interface JsonProviderFactory {
        BlockStateProvider create(
                com.google.gson.JsonObject json,
                rocks.minestom.worldgen.structure.context.BlockTagManager blockTags);
    }

    private static <D> Result<BlockStateProvider> fromJson(
            Transcoder<D> coder, D value, JsonProviderFactory factory) {
        var raw = Codec.RAW_VALUE.decode(coder, value).orElseThrow();
        var json = raw.convertTo(Transcoder.JSON).orElseThrow();
        return new Result.Ok<>(
                factory.create(json.getAsJsonObject(), PlacementModifiers.currentBlockTags()));
    }

    public static BlockStateProvider fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
