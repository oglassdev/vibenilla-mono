package rocks.minestom.placement;

import java.util.Objects;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class WaterloggedAxisPlacementRule extends BlockPlacementRule {
    public WaterloggedAxisPlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var blockFace = Objects.requireNonNullElse(placementState.blockFace(), BlockFace.TOP);
        var axis =
                switch (blockFace) {
                    case WEST, EAST -> "x";
                    case SOUTH, NORTH -> "z";
                    case TOP, BOTTOM -> "y";
                };
        var replaced = placementState.instance().getBlock(placementState.placePosition());
        var waterlogged = Utility.isWaterSource(replaced);

        return placementState
                .block()
                .withProperty("axis", axis)
                .withProperty("waterlogged", String.valueOf(waterlogged));
    }
}
