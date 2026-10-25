package rocks.minestom.placement;

import java.util.concurrent.ThreadLocalRandom;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.Nullable;

/** A plant body turns back into a head when nothing grows on from it. */
public final class GrowingPlantBodyPlacementRule extends BlockPlacementRule {
    private static final int HEAD_AGES = 25;

    private final Block head;
    private final BlockFace growthDirection;
    private final @Nullable BlockHandler handler;

    public GrowingPlantBodyPlacementRule(
            Block block, Block head, BlockFace growthDirection, @Nullable BlockHandler handler) {
        super(block);
        this.head = head;
        this.growthDirection = growthDirection;
        this.handler = handler;
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var placed = placementState.block();
        return this.handler == null ? placed : placed.withHandler(this.handler);
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        var currentBlock = updateState.currentBlock();
        if (updateState.fromFace() != this.growthDirection) {
            return currentBlock;
        }

        var neighbor =
                updateState
                        .instance()
                        .getBlock(updateState.blockPosition().relative(this.growthDirection));
        if (neighbor.compare(this.block) || neighbor.compare(this.head)) {
            return currentBlock;
        }

        var restored =
                this.head
                        .withProperty(
                                "age",
                                Integer.toString(ThreadLocalRandom.current().nextInt(HEAD_AGES)))
                        .withHandler(currentBlock.handler());
        var berries = currentBlock.getProperty("berries");
        return berries == null ? restored : restored.withProperty("berries", berries);
    }
}
