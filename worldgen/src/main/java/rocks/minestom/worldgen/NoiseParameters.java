package rocks.minestom.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

/**
 * Noise octave layout in the legacy {@code firstOctave}/{@code amplitudes} form. The 26.3 data
 * stores {@code base_octave}, {@code octave_count} and sparse {@code amplitude_modifiers} with a
 * pre-computed {@code base_amplitude} that reproduces this normalization, so the latter is not read.
 */
public record NoiseParameters(int firstOctave, List<Double> amplitudes) {
    public static final Codec<NoiseParameters> CODEC =
            StructCodec.struct(
                    "base_octave",
                    Codec.INT.optional(0),
                    NoiseParameters::firstOctave,
                    "octave_count",
                    Codec.INT.optional(1),
                    parameters -> parameters.amplitudes().size(),
                    "amplitude_modifiers",
                    Codec.DOUBLE.list().optional(List.of()),
                    NoiseParameters::amplitudes,
                    NoiseParameters::fromData);

    private static NoiseParameters fromData(
            int baseOctave, int octaveCount, List<Double> amplitudeModifiers) {
        if (amplitudeModifiers.isEmpty()) {
            var amplitudes = new ArrayList<Double>(octaveCount);
            for (var index = 0; index < octaveCount; index++) {
                amplitudes.add(1.0D);
            }
            return new NoiseParameters(baseOctave, amplitudes);
        }
        return new NoiseParameters(baseOctave, amplitudeModifiers);
    }
}
