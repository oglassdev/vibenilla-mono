package rocks.minestom.placement;

import java.util.Objects;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class AxisPlacementRule extends BlockPlacementRule {
    public AxisPlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var blockFace = Objects.requireNonNullElse(placementState.blockFace(), BlockFace.TOP);

        return placementState
                .block()
                .withProperty(
                        "axis",
                        switch (blockFace) {
                            case WEST, EAST -> "x";
                            case SOUTH, NORTH -> "z";
                            case TOP, BOTTOM -> "y";
                        });
    }
}
