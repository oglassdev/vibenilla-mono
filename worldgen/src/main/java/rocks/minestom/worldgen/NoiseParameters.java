package rocks.minestom.worldgen;

import java.util.List;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public record NoiseParameters(int firstOctave, List<Double> amplitudes) {
    public static final Codec<NoiseParameters> CODEC =
            StructCodec.struct(
                    "firstOctave",
                    Codec.INT,
                    NoiseParameters::firstOctave,
                    "amplitudes",
                    Codec.DOUBLE.list(),
                    NoiseParameters::amplitudes,
                    NoiseParameters::new);
}
