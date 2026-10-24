package rocks.minestom.worldgen.carver;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

/** Vanilla {@code VeryBiasedToBottomInt}: three nested draws strongly favoring small values. */
record VeryBiasedToBottomIntProvider(int minInclusive, int maxInclusive) implements IntProvider {
    static final Key TYPE = Key.key("minecraft:very_biased_to_bottom");

    VeryBiasedToBottomIntProvider {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException(
                    "Max must be at least min, min_inclusive: "
                            + minInclusive
                            + ", max_inclusive: "
                            + maxInclusive);
        }
    }

    @Override
    public Key type() {
        return TYPE;
    }

    @Override
    public int sample(RandomSource random) {
        return this.minInclusive
                + random.nextInt(
                        random.nextInt(
                                        random.nextInt(this.maxInclusive - this.minInclusive + 1)
                                                + 1)
                                + 1);
    }

    @Override
    public int minValue() {
        return this.minInclusive;
    }

    @Override
    public int maxValue() {
        return this.maxInclusive;
    }
}
