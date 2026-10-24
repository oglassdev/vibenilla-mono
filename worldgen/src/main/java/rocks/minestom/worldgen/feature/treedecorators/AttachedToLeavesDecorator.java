package rocks.minestom.worldgen.feature.treedecorators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

/**
 * Vanilla's {@code AttachedToLeavesDecorator}: every leaf (in shuffled order) rolls a random
 * configured direction, then the probability, and places the provided block there when enough empty
 * blocks follow and no block was placed within the exclusion box around the target.
 */
public record AttachedToLeavesDecorator(
        float probability,
        int exclusionRadiusXZ,
        int exclusionRadiusY,
        BlockStateProvider blockProvider,
        int requiredEmptyBlocks,
        List<Direction> directions)
        implements TreeDecorator {
    public static final Codec<AttachedToLeavesDecorator> CODEC =
            StructCodec.struct(
                    "probability",
                    Codec.FLOAT,
                    AttachedToLeavesDecorator::probability,
                    "exclusion_radius_xz",
                    Codec.INT,
                    AttachedToLeavesDecorator::exclusionRadiusXZ,
                    "exclusion_radius_y",
                    Codec.INT,
                    AttachedToLeavesDecorator::exclusionRadiusY,
                    "block_provider",
                    BlockStateProviders.CODEC,
                    AttachedToLeavesDecorator::blockProvider,
                    "required_empty_blocks",
                    Codec.INT,
                    AttachedToLeavesDecorator::requiredEmptyBlocks,
                    "directions",
                    Direction.CODEC.list(),
                    AttachedToLeavesDecorator::directions,
                    AttachedToLeavesDecorator::new);

    @Override
    public void place(TreeDecorator.Context context) {
        var blacklist = new HashSet<BlockVec>();
        var random = context.random();
        var leaves = new ArrayList<>(context.leaves());
        TreeDecorator.shuffle(leaves, random);

        for (var leaf : leaves) {
            var direction = this.directions.get(random.nextInt(this.directions.size()));
            var placement = direction.relative(leaf);
            if (blacklist.contains(placement)
                    || !(random.nextFloat() < this.probability)
                    || !this.hasRequiredEmptyBlocks(context, leaf, direction)) {
                continue;
            }

            for (var x = -this.exclusionRadiusXZ; x <= this.exclusionRadiusXZ; x++) {
                for (var y = -this.exclusionRadiusY; y <= this.exclusionRadiusY; y++) {
                    for (var z = -this.exclusionRadiusXZ; z <= this.exclusionRadiusXZ; z++) {
                        blacklist.add(placement.add(x, y, z));
                    }
                }
            }

            context.setBlock(
                    placement, this.blockProvider.getState(context.level(), random, placement));
        }
    }

    private boolean hasRequiredEmptyBlocks(
            TreeDecorator.Context context, BlockVec leaf, Direction direction) {
        for (var step = 1; step <= this.requiredEmptyBlocks; step++) {
            var offset =
                    leaf.add(
                            direction.stepX() * step,
                            direction.stepY() * step,
                            direction.stepZ() * step);
            if (!context.isAir(offset)) {
                return false;
            }
        }

        return true;
    }
}
