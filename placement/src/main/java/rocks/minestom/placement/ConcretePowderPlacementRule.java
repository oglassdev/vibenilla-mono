package rocks.minestom.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class ConcretePowderPlacementRule extends BlockPlacementRule {
    private static final BlockFace[] SIDE_FACES = {
        BlockFace.TOP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST
    };

    private final Block concrete;

    public ConcretePowderPlacementRule(Block block, Block concrete) {
        super(block);
        this.concrete = concrete;
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var blockGetter = placementState.instance();
        var placePosition = placementState.placePosition();

        if (isWater(blockGetter.getBlock(placePosition))
                || touchesWater(blockGetter, placePosition)) {
            return this.concrete;
        }

        return placementState.block();
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        if (touchesWater(updateState.instance(), updateState.blockPosition())) {
            return this.concrete;
        }

        return updateState.currentBlock();
    }

    private static boolean touchesWater(Block.Getter blockGetter, Point position) {
        for (var face : SIDE_FACES) {
            var neighbor = blockGetter.getBlock(position.relative(face));

            if (isWater(neighbor)
                    && !neighbor.registry().collisionShape().isFaceFull(face.getOppositeFace())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isWater(Block block) {
        return block.compare(Block.WATER)
                || block.compare(Block.BUBBLE_COLUMN)
                || block.compare(Block.KELP)
                || block.compare(Block.KELP_PLANT)
                || block.compare(Block.SEAGRASS)
                || block.compare(Block.TALL_SEAGRASS)
                || "true".equals(block.getProperty("waterlogged"));
    }
}
