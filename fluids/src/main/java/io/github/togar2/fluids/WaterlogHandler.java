package io.github.togar2.fluids;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;

public interface WaterlogHandler {
	WaterlogHandler DEFAULT = new WaterlogHandler() {};
	
	/**
	 * Handler for blocks which hold a fluid but never let one be placed into or taken out of them.
	 */
	WaterlogHandler REJECTING = new WaterlogHandler() {
		@Override
		public boolean canPlaceFluid(Instance instance, BlockVec point, Block block, FluidState state) {
			return false;
		}
		
		@Override
		public boolean canRemoveFluid(Instance instance, BlockVec point, FluidState state) {
			return false;
		}
		
		@Override
		public boolean placeFluid(Instance instance, BlockVec point, FluidState state) {
			return false;
		}
	};
	
	default boolean canPlaceFluid(Instance instance, BlockVec point, Block block, FluidState state) {
		return state.isWater() && state.isSource();
	}
	
	default boolean canRemoveFluid(Instance instance, BlockVec point, FluidState state) {
		return state.isWaterlogged();
	}
	
	default boolean placeFluid(Instance instance, BlockVec point, FluidState state) {
		Block currentBlock = instance.getBlock(point);
		if (FluidState.isWaterlogged(currentBlock)) return false;
		if (!canPlaceFluid(instance, point, currentBlock, state)) return false;
		
		// The placed state (waterlogged block) is different from the original fluid state (probably just water)
		FluidState placedState = FluidState.of(currentBlock).setWaterlogged(true);
		instance.placeBlock(new BlockHandler.Placement(placedState.block(), currentBlock, instance, point));
		MinestomFluids.scheduleTick(instance, point, placedState);
		return true;
	}
}
