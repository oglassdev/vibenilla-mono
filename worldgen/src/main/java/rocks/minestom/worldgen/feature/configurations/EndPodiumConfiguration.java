package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

public record EndPodiumConfiguration(boolean active) implements FeatureConfiguration {
    public static final Codec<EndPodiumConfiguration> CODEC =
            StructCodec.struct(
                    "active",
                    Codec.BOOLEAN.optional(false),
                    EndPodiumConfiguration::active,
                    EndPodiumConfiguration::new);
}
