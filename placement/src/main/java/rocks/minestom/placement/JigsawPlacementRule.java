package rocks.minestom.placement;

import java.util.Objects;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class JigsawPlacementRule extends BlockPlacementRule {
    public JigsawPlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var front = Objects.requireNonNullElse(placementState.blockFace(), BlockFace.TOP);
        var playerPosition = placementState.playerPosition();
        var yaw = playerPosition == null ? 0.0F : playerPosition.yaw();
        var top =
                front.toDirection().vertical()
                        ? BlockFace.fromYaw(yaw).getOppositeFace()
                        : BlockFace.TOP;

        return placementState
                .block()
                .withHandler(GameMasterInteractionBlockHandler.INSTANCE)
                .withProperty("orientation", orientationName(front) + "_" + orientationName(top));
    }

    private static String orientationName(BlockFace face) {
        return switch (face) {
            case TOP -> "up";
            case BOTTOM -> "down";
            default -> face.name().toLowerCase();
        };
    }
}
