package rocks.minestom.placement;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class CoralPlantPlacementRule extends BlockPlacementRule {
    public CoralPlantPlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var instance = placementState.instance();
        var placePosition = placementState.placePosition();
        var below = instance.getBlock(placePosition.relative(BlockFace.BOTTOM));

        if (!below.registry().collisionShape().isFaceFull(BlockFace.TOP)) {
            return null;
        }

        var replaced = instance.getBlock(placePosition);
        var waterlogged = Utility.isWaterSource(replaced);

        return placementState.block().withProperty("waterlogged", waterlogged ? "true" : "false");
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        if (updateState.fromFace() != BlockFace.BOTTOM) {
            return updateState.currentBlock();
        }

        var below =
                updateState
                        .instance()
                        .getBlock(updateState.blockPosition().relative(BlockFace.BOTTOM));

        if (!below.registry().collisionShape().isFaceFull(BlockFace.TOP)) {
            return Block.AIR;
        }

        return updateState.currentBlock();
    }
}
