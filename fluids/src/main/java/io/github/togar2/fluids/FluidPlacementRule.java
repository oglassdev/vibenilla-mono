package io.github.togar2.fluids;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;

public class FluidPlacementRule extends BlockPlacementRule {
	public FluidPlacementRule(@NotNull Block block) {
		super(block);
	}
	
	@Override
	public @NotNull Block blockUpdate(@NotNull UpdateState updateState) {
		String waterlogged = updateState.currentBlock().properties().get("waterlogged");
		if (waterlogged == null || waterlogged.equals("true")) {
			Instance instance = (Instance) updateState.instance();
			BlockVec blockVec = updateState.blockPosition().asBlockVec();
			MinestomFluids.scheduleTick(
					instance, blockVec,
					FluidState.of(updateState.currentBlock())
			);
		}
		return super.blockUpdate(updateState);
	}
	
	@Override
	public @NotNull Block blockPlace(@NotNull PlacementState placementState) {
		BlockVec blockVec = placementState.placePosition().asBlockVec();
		Block block = placementState.block();
		
		if (FluidState.canBeWaterlogged(block) && !FluidState.isWaterlogged(block)) {
			FluidState replaced = FluidState.of(placementState.instance().getBlock(blockVec));
			if (replaced.isWater() && replaced.isSource()) block = FluidState.setWaterlogged(block, true);
		}
		
		String waterlogged = block.properties().get("waterlogged");
		if (waterlogged == null || waterlogged.equals("true")) {
			Instance instance = (Instance) placementState.instance();
			MinestomFluids.scheduleTick(instance, blockVec, FluidState.of(block));
		}
		
		return block;
	}
}
