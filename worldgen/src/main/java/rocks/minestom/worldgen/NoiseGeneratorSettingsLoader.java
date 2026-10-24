package rocks.minestom.worldgen;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.biome.ClimateSampler;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.surface.SurfaceRules;
import rocks.minestom.worldgen.surface.SurfaceSystem;

public final class NoiseGeneratorSettingsLoader {
    private final DataPack dataPack;

    public NoiseGeneratorSettingsLoader(DataPack dataPack) {
        this.dataPack = dataPack;
    }

    public NoiseGeneratorSettingsRuntime load(Key id, long seed) {
        var json = this.dataPack.readNoiseSettings(id);
        var settings = NoiseGeneratorSettings.CODEC.decode(Transcoder.JSON, json).orElseThrow();

        var randomState = new RandomState(this.dataPack, seed, settings.legacyRandomSource());
        var densityResolver = new DensityFunctionResolver(this.dataPack, randomState);
        var router = settings.noiseRouter();

        var climateSampler =
                new ClimateSampler(
                        decodeDensity(densityResolver, router.temperature()),
                        decodeDensity(densityResolver, router.vegetation()),
                        decodeDensity(densityResolver, router.continents()),
                        decodeDensity(densityResolver, router.erosion()),
                        decodeDensity(densityResolver, router.depth()),
                        decodeDensity(densityResolver, router.ridges()));

        var aquiferSettings = settings.aquifers();
        var aquifers =
                aquiferSettings == null
                        ? null
                        : new NoiseGeneratorSettingsRuntime.AquiferDensities(
                                decodeDensity(densityResolver, aquiferSettings.barrier()),
                                decodeDensity(densityResolver, aquiferSettings.exclusion()),
                                decodeDensity(
                                        densityResolver, aquiferSettings.fluidLevelFloodedness()),
                                decodeDensity(densityResolver, aquiferSettings.fluidLevelSpread()),
                                decodeDensity(densityResolver, aquiferSettings.lava()),
                                decodeDensity(densityResolver, aquiferSettings.surfaceLevel()));

        var surfaceRuleJson = settings.materialRule().convertTo(Transcoder.JSON).orElseThrow();
        var surfaceRule =
                SurfaceRules.codec(this.dataPack, densityResolver.codec())
                        .decode(Transcoder.JSON, surfaceRuleJson)
                        .orElseThrow();

        var seaLevel = settings.seaLevel();
        var surfaceSystem =
                new SurfaceSystem(
                        randomState, settings.defaultBlock(), seaLevel, randomState.random());

        return new NoiseGeneratorSettingsRuntime(
                settings.noise().minY(),
                settings.noise().height(),
                seaLevel,
                settings.defaultBlock(),
                settings.defaultFluid(),
                decodeDensity(densityResolver, router.finalDensity()),
                decodeDensity(densityResolver, router.chunkSurfaceLevel()),
                aquifers,
                climateSampler,
                randomState,
                surfaceSystem,
                surfaceRule);
    }

    private static DensityFunction decodeDensity(
            DensityFunctionResolver resolver, Codec.RawValue value) {
        var json = value.convertTo(Transcoder.JSON).orElseThrow();
        return resolver.codec().decode(Transcoder.JSON, json).orElseThrow();
    }
}
