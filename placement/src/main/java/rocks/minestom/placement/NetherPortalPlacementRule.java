package rocks.minestom.placement;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

/** Vanilla {@code NetherPortalBlock.updateShape}: a portal without a complete frame vanishes. */
public final class NetherPortalPlacementRule extends BlockPlacementRule {
    /**
     * Updates recurse one block at a time, so a 21x21 portal can collapse along a path through
     * every block.
     */
    private static final int MAX_UPDATE_DISTANCE = 21 * 21 + 1;

    public NetherPortalPlacementRule(Block block) {
        super(block);
    }

    @Override
    public int maxUpdateDistance() {
        return MAX_UPDATE_DISTANCE;
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        return placementState.block();
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        var current = updateState.currentBlock();
        var axis = PortalShape.Axis.of(current);
        var face = updateState.fromFace();
        var position = updateState.blockPosition();

        var alongThickness =
                axis == PortalShape.Axis.X
                        ? face == BlockFace.NORTH || face == BlockFace.SOUTH
                        : face == BlockFace.EAST || face == BlockFace.WEST;
        if (alongThickness) {
            return current;
        }
        var neighbor = updateState.instance().getBlock(position.relative(face));
        if (neighbor.compare(Block.NETHER_PORTAL)) {
            return current;
        }
        var shape =
                PortalShape.at(
                        updateState.instance(),
                        new BlockVec(position.blockX(), position.blockY(), position.blockZ()),
                        axis);
        return shape.isComplete() ? current : Block.AIR;
    }
}
