package rocks.minestom.worldgen;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

/**
 * Defines the named density-function channels that act as the backbone signals for world
 * generation: the climate parameters that drive biome selection, the chunk-level surface height,
 * and the final density that decides where solid terrain exists.
 */
public record NoiseRouter(
        Codec.RawValue temperature,
        Codec.RawValue vegetation,
        Codec.RawValue continents,
        Codec.RawValue erosion,
        Codec.RawValue depth,
        Codec.RawValue ridges,
        Codec.RawValue chunkSurfaceLevel,
        Codec.RawValue finalDensity) {
    public static final Codec<NoiseRouter> CODEC =
            StructCodec.struct(
                    "temperature",
                    Codec.RAW_VALUE,
                    NoiseRouter::temperature,
                    "vegetation",
                    Codec.RAW_VALUE,
                    NoiseRouter::vegetation,
                    "continents",
                    Codec.RAW_VALUE,
                    NoiseRouter::continents,
                    "erosion",
                    Codec.RAW_VALUE,
                    NoiseRouter::erosion,
                    "depth",
                    Codec.RAW_VALUE,
                    NoiseRouter::depth,
                    "ridges",
                    Codec.RAW_VALUE,
                    NoiseRouter::ridges,
                    "chunk_surface_level",
                    Codec.RAW_VALUE,
                    NoiseRouter::chunkSurfaceLevel,
                    "final_density",
                    Codec.RAW_VALUE,
                    NoiseRouter::finalDensity,
                    NoiseRouter::new);
}
