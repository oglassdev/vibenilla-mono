package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.BlockProperties;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Orients the wrapped provider's state along {@code direction}, or along a random direction when it
 * is null, by setting the {@code axis} and {@code facing} properties the block accepts.
 */
public record RotatedBlockProvider(BlockStateProvider state, Direction direction)
        implements BlockStateProvider {

    public static final Codec<RotatedBlockProvider> CODEC =
            StructCodec.struct(
                    "state",
                    BlockStateProviders.CODEC,
                    RotatedBlockProvider::state,
                    "direction",
                    Direction.CODEC.optional(),
                    RotatedBlockProvider::direction,
                    RotatedBlockProvider::new);

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        var direction = this.direction != null ? this.direction : Direction.getRandom(random);
        return this.rotate(this.state.getState(random, position), direction);
    }

    @Override
    public Block getState(Block.Getter accessor, RandomSource random, BlockVec position) {
        var direction = this.direction != null ? this.direction : Direction.getRandom(random);
        return this.rotate(this.state.getState(accessor, random, position), direction);
    }

    private Block rotate(Block block, Direction direction) {
        var rotated = BlockProperties.trySet(block, "axis", direction.axisName());
        return BlockProperties.trySet(rotated, "facing", direction.serializedName());
    }
}
