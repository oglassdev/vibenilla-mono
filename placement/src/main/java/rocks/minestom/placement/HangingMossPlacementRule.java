package rocks.minestom.placement;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class HangingMossPlacementRule extends BlockPlacementRule {
    public HangingMossPlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var instance = placementState.instance();
        var placePosition = placementState.placePosition();
        var above = instance.getBlock(placePosition.relative(BlockFace.TOP));

        if (!canHangFrom(above)) {
            return null;
        }

        var below = instance.getBlock(placePosition.relative(BlockFace.BOTTOM));
        return placementState
                .block()
                .withProperty("tip", String.valueOf(!below.compare(this.block)));
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        var currentBlock = updateState.currentBlock();
        var position = updateState.blockPosition();

        if (updateState.fromFace() == BlockFace.TOP) {
            var above = updateState.instance().getBlock(position.relative(BlockFace.TOP));
            return canHangFrom(above) ? currentBlock : Block.AIR;
        }

        if (updateState.fromFace() == BlockFace.BOTTOM) {
            var below = updateState.instance().getBlock(position.relative(BlockFace.BOTTOM));
            return currentBlock.withProperty("tip", String.valueOf(!below.compare(this.block)));
        }

        return currentBlock;
    }

    private boolean canHangFrom(Block above) {
        return above.compare(this.block)
                || above.registry().collisionShape().isFaceFull(BlockFace.BOTTOM);
    }
}
