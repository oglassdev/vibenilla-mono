package rocks.minestom.placement;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

/**
 * An attached stem reverts to a fully grown stem once its fruit is gone, and breaks when its
 * support is removed.
 */
public final class AttachedStemPlacementRule extends BlockPlacementRule {
    private static final String MAX_AGE = "7";

    private final Block fruit;
    private final Block stem;
    private final Key supportTag;

    public AttachedStemPlacementRule(Block block, Block fruit, Block stem, Key supportTag) {
        super(block);
        this.fruit = fruit;
        this.stem = stem;
        this.supportTag = supportTag;
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        return this.block;
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        var currentBlock = updateState.currentBlock();
        var instance = updateState.instance();
        var position = updateState.blockPosition();

        if (updateState.fromFace().name().equalsIgnoreCase(currentBlock.getProperty("facing"))
                && !instance.getBlock(position.relative(updateState.fromFace())).compare(fruit)) {
            return stem.withProperty("age", MAX_AGE);
        }

        var support = MinecraftServer.process().blocks().getTag(supportTag);
        var below = instance.getBlock(position.relative(BlockFace.BOTTOM));
        return support != null && support.contains(below) ? currentBlock : Block.AIR;
    }
}
