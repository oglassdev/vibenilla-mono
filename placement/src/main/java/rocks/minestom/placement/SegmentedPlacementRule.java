package rocks.minestom.placement;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class SegmentedPlacementRule extends BlockPlacementRule {
    private static final Key SUPPORTS_VEGETATION_TAG = Key.key("minecraft:supports_vegetation");

    private final String amountProperty;
    private final boolean requiresSturdySupport;

    public SegmentedPlacementRule(Block block) {
        this(block, "flower_amount", false);
    }

    public SegmentedPlacementRule(
            Block block, String amountProperty, boolean requiresSturdySupport) {
        super(block);
        this.amountProperty = amountProperty;
        this.requiresSturdySupport = requiresSturdySupport;
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var instance = placementState.instance();
        var placePosition = placementState.placePosition();
        var below = instance.getBlock(placePosition.relative(BlockFace.BOTTOM));

        if (!supportsVegetation(below)) {
            return null;
        }

        var existingBlock = instance.getBlock(placePosition);

        if (existingBlock.compare(placementState.block())) {
            var amount = parseAmount(existingBlock.getProperty(this.amountProperty));
            var increased = Math.min(4, amount + 1);
            return existingBlock.withProperty(this.amountProperty, Integer.toString(increased));
        }

        var playerPosition = placementState.playerPosition();
        var yaw = playerPosition == null ? 0.0F : playerPosition.yaw();
        var facing = BlockFace.fromYaw(yaw).getOppositeFace();

        return placementState
                .block()
                .withProperty(this.amountProperty, "1")
                .withProperty("facing", facing.name().toLowerCase());
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

        if (!supportsVegetation(below)) {
            return Block.AIR;
        }

        return updateState.currentBlock();
    }

    private boolean supportsVegetation(Block block) {
        if (this.requiresSturdySupport) {
            return Utility.canSupportRigidBlock(block, BlockFace.TOP);
        }

        var tag = MinecraftServer.process().blocks().getTag(SUPPORTS_VEGETATION_TAG);
        return tag != null && tag.contains(block);
    }

    @Override
    public boolean isSelfReplaceable(Replacement replacement) {
        if (!replacement.block().compare(this.block)) {
            return false;
        }

        if (replacement.material() != this.block.registry().material()) {
            return false;
        }

        var amount = parseAmount(replacement.block().getProperty(this.amountProperty));
        return amount < 4;
    }

    private static int parseAmount(String property) {
        if (property == null) {
            return 1;
        }

        try {
            return Integer.parseInt(property);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }
}
