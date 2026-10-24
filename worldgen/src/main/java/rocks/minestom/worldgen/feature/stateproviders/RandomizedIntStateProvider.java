package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

public record RandomizedIntStateProvider(
        BlockStateProvider source, String property, IntProvider values)
        implements BlockStateProvider {
    public static final Codec<RandomizedIntStateProvider> CODEC =
            StructCodec.struct(
                    "source",
                    BlockStateProviders.CODEC,
                    RandomizedIntStateProvider::source,
                    "property",
                    Codec.STRING,
                    RandomizedIntStateProvider::property,
                    "values",
                    IntProvider.CODEC,
                    RandomizedIntStateProvider::values,
                    RandomizedIntStateProvider::new);

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        return this.randomize(this.source.getState(random, position), random);
    }

    @Override
    public Block getState(Block.Getter accessor, RandomSource random, BlockVec position) {
        return this.randomize(this.source.getState(accessor, random, position), random);
    }

    /**
     * Only samples the values provider when the state has the property; otherwise the state is
     * returned unmodified without consuming a random draw.
     */
    private Block randomize(Block base, RandomSource random) {
        if (base.getProperty(this.property) == null) {
            return base;
        }

        return base.withProperty(this.property, Integer.toString(this.values.sample(random)));
    }
}
