package rocks.minestom.placement;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class SporeBlossomPlacementRule extends BlockPlacementRule {
    public SporeBlossomPlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var instance = placementState.instance();
        var placePosition = placementState.placePosition();
        var above = instance.getBlock(placePosition.relative(BlockFace.TOP));

        if (!Utility.canSupportCenter(above, BlockFace.BOTTOM)
                || isWater(instance.getBlock(placePosition))) {
            return null;
        }

        return placementState.block();
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        if (updateState.fromFace() != BlockFace.TOP) {
            return updateState.currentBlock();
        }

        var above =
                updateState
                        .instance()
                        .getBlock(updateState.blockPosition().relative(BlockFace.TOP));
        return Utility.canSupportCenter(above, BlockFace.BOTTOM)
                ? updateState.currentBlock()
                : Block.AIR;
    }

    private static boolean isWater(Block block) {
        return block.compare(Block.WATER);
    }
}
