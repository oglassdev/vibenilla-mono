package rocks.minestom.worldgen;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

/** The vertical extent of a dimension's noise terrain. */
public record NoiseSettings(int minY, int height) {
    public static final Codec<NoiseSettings> CODEC =
            StructCodec.struct(
                    "min_y",
                    Codec.INT,
                    NoiseSettings::minY,
                    "height",
                    Codec.INT,
                    NoiseSettings::height,
                    NoiseSettings::new);
}
