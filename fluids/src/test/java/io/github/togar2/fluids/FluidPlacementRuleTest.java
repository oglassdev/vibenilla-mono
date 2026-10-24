package io.github.togar2.fluids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.item.ItemStack;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

@EnvTest
final class FluidPlacementRuleTest {
    private static final BlockVec POSITION = new BlockVec(8, 40, 8);

    private static final class TopSlabRule extends BlockPlacementRule {
        TopSlabRule() {
            super(Block.OAK_SLAB);
        }

        @Override
        public Block blockPlace(PlacementState placementState) {
            return placementState.block().withProperty("type", "top");
        }
    }

    private static BlockPlacementRule.PlacementState placing(
            net.minestom.server.instance.Instance instance, Block block) {
        return new BlockPlacementRule.PlacementState(
                instance,
                block,
                BlockFace.TOP,
                POSITION,
                new Vec(0.5, 0.5, 0.5),
                Pos.ZERO,
                ItemStack.AIR,
                false);
    }

    @Test
    void delegatedRuleStillPlacesAndFluidTickIsScheduled(Env env) {
        var instance = env.createFlatInstance();
        var blocks = env.process().block();
        blocks.registerBlockPlacementRule(new TopSlabRule());
        MinestomFluids.init(blocks);
        env.process().eventHandler().addChild(MinestomFluids.events());

        instance.setBlock(POSITION, Block.WATER);
        for (var i = 0; i < 6; i++) env.tick();
        assertFalse(instance.getBlock(POSITION.add(1, 0, 0)).compare(Block.WATER));

        var placed =
                blocks.getBlockPlacementRule(Block.OAK_SLAB)
                        .blockPlace(placing(instance, Block.OAK_SLAB));
        assertEquals("top", placed.getProperty("type"));
        assertTrue(FluidState.isWaterlogged(placed));

        instance.setBlock(POSITION, placed);
        for (var i = 0; i < 6; i++) env.tick();
        assertTrue(instance.getBlock(POSITION.add(1, 0, 0)).compare(Block.WATER));
    }

    @Test
    void flowingWaterDoesNotWaterlogPlacedBlock(Env env) {
        var instance = env.createFlatInstance();
        var blocks = env.process().block();
        blocks.registerBlockPlacementRule(new TopSlabRule());
        MinestomFluids.init(blocks);

        instance.setBlock(POSITION, Block.WATER.withProperty("level", "3"));
        var placed =
                blocks.getBlockPlacementRule(Block.OAK_SLAB)
                        .blockPlace(placing(instance, Block.OAK_SLAB));
        assertFalse(FluidState.isWaterlogged(placed));
    }
}
