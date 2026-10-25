package rocks.minestom.placement;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

/** An attached stem reverts to a fully grown stem once its fruit is gone. */
public final class AttachedStemPlacementRule extends BlockPlacementRule {
    private static final String MAX_AGE = "7";

    private final Block fruit;
    private final Block stem;

    public AttachedStemPlacementRule(Block block, Block fruit, Block stem) {
        super(block);
        this.fruit = fruit;
        this.stem = stem;
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        return this.block;
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        var currentBlock = updateState.currentBlock();
        if (!updateState.fromFace().name().equalsIgnoreCase(currentBlock.getProperty("facing"))) {
            return currentBlock;
        }

        var neighbor =
                updateState
                        .instance()
                        .getBlock(updateState.blockPosition().relative(updateState.fromFace()));
        return neighbor.compare(this.fruit) ? currentBlock : this.stem.withProperty("age", MAX_AGE);
    }
}
