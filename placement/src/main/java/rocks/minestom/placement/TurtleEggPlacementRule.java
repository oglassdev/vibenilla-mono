package rocks.minestom.placement;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class TurtleEggPlacementRule extends BlockPlacementRule {
    public TurtleEggPlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var instance = placementState.instance();
        var placePosition = placementState.placePosition();
        var existingBlock = instance.getBlock(placePosition);

        if (existingBlock.compare(placementState.block())) {
            var eggsProperty = existingBlock.getProperty("eggs");
            var eggs = eggsProperty == null ? 1 : Integer.parseInt(eggsProperty);

            if (eggs < 4) {
                return existingBlock.withProperty("eggs", Integer.toString(Math.min(4, eggs + 1)));
            }
        }

        return placementState.block().withProperty("eggs", "1");
    }

    @Override
    public boolean isSelfReplaceable(Replacement replacement) {
        if (!replacement.block().compare(this.block)) {
            return false;
        }

        if (replacement.material() != this.block.registry().material()) {
            return false;
        }

        var eggsProperty = replacement.block().getProperty("eggs");
        var eggs = eggsProperty == null ? 1 : Integer.parseInt(eggsProperty);
        return eggs < 4;
    }
}
