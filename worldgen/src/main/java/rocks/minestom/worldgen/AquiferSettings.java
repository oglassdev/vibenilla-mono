package rocks.minestom.worldgen;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

/** The density channels driving the aquifers of a dimension; absent when it has none. */
public record AquiferSettings(
        Codec.RawValue barrier,
        Codec.RawValue exclusion,
        Codec.RawValue fluidLevelFloodedness,
        Codec.RawValue fluidLevelSpread,
        Codec.RawValue lava,
        Codec.RawValue surfaceLevel) {
    public static final Codec<AquiferSettings> CODEC =
            StructCodec.struct(
                    "barrier",
                    Codec.RAW_VALUE,
                    AquiferSettings::barrier,
                    "exclusion",
                    Codec.RAW_VALUE,
                    AquiferSettings::exclusion,
                    "fluid_level_floodedness",
                    Codec.RAW_VALUE,
                    AquiferSettings::fluidLevelFloodedness,
                    "fluid_level_spread",
                    Codec.RAW_VALUE,
                    AquiferSettings::fluidLevelSpread,
                    "lava",
                    Codec.RAW_VALUE,
                    AquiferSettings::lava,
                    "surface_level",
                    Codec.RAW_VALUE,
                    AquiferSettings::surfaceLevel,
                    AquiferSettings::new);
}
