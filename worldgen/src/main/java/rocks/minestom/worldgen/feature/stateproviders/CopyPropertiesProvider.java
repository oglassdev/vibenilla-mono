package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.BlockProperties;
import rocks.minestom.worldgen.random.RandomSource;

/** Provides the source's state carrying over the properties of the block already in place. */
public record CopyPropertiesProvider(BlockStateProvider source) implements BlockStateProvider {
    public static final Codec<CopyPropertiesProvider> CODEC =
            StructCodec.struct(
                    "source",
                    BlockStateProviders.CODEC,
                    CopyPropertiesProvider::source,
                    CopyPropertiesProvider::new);

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        return this.source.getState(random, position);
    }

    @Override
    public Block getState(Block.Getter accessor, RandomSource random, BlockVec position) {
        return BlockProperties.withPropertiesOf(
                this.source.getState(accessor, random, position), accessor.getBlock(position));
    }
}
