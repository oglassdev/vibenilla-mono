package io.github.togar2.fluids;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.WorldEventPacket;
import net.minestom.server.utils.PacketSendingUtils;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import net.minestom.server.worldevent.WorldEvent;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class LavaFluid extends FlowableFluid {
	public static final double MIN_REPLACE_HEIGHT = 4.0 / 9.0;
	
	public LavaFluid() {
		super(Block.LAVA, Material.LAVA_BUCKET);
	}
	
	@Override
	protected boolean isInfinite() {
		return false; // TODO customize
	}
	
	@Override
	protected int getLevelDecreasePerBlock(Instance instance) {
		return isFastLava(instance) ? 1 : 2;
	}
	
	@Override
	protected int getHoleRadius(Instance instance) {
		return isFastLava(instance) ? 4 : 2;
	}
	
	@Override
	protected @Nullable FluidState onBreakingBlock(Instance instance, BlockVec point,
	                                               BlockFace direction, Block block, FluidState newState) {
		FluidBlockBreakEvent event = new FluidBlockBreakEvent(instance, point, direction, block, newState);
		EventDispatcher.call(event);
		if (event.isCancelled()) return null;
		fizz(instance, point);
		return event.getNewState();
	}
	
	@Override
	protected boolean canBeReplacedWith(Instance instance, BlockVec point, FluidState currentState,
	                                    FluidState newState, BlockFace direction) {
		return currentState.getHeight(instance, point) >= MIN_REPLACE_HEIGHT && newState.isWater();
	}
	
	@Override
	public int getNextTickDelay(Instance instance, BlockVec point) {
		return isFastLava(instance) ? 10 : 30;
	}
	
	@Override
	public int getNextSpreadDelay(Instance instance, BlockVec point, FluidState state, FluidState newState) {
		int tickDelay = getNextTickDelay(instance, point);
		if (!state.isEmpty() && !newState.isEmpty() && !state.isFalling() && !newState.isFalling()) {
			double currentHeight = state.getHeight(instance, point);
			double newHeight = newState.getHeight(instance, point);
			
			if (newHeight > currentHeight && ThreadLocalRandom.current().nextInt(4) != 0)
				tickDelay *= 4;
		}
		
		return tickDelay;
	}
	
	@Override
	protected void flow(Instance instance, BlockVec point, FluidState newState, BlockFace direction) {
		if (direction == BlockFace.BOTTOM) {
			Block currentBlock = instance.getBlock(point);
			FluidState currentState = FluidState.of(currentBlock);
			if (currentState.isWater() && newState.isLava()) {
				if (currentBlock.liquid()) {
					LavaSolidifyEvent event = new LavaSolidifyEvent(instance, point, direction, Block.STONE);
					EventDispatcher.call(event);
					if (event.isCancelled()) return;
					
					instance.setBlock(point, event.getResultingBlock());
				}
				
				fizz(instance, point);
				return;
			}
		}
		
		super.flow(instance, point, newState, direction);
	}
	
	private static boolean isFastLava(Instance instance) {
		return MinestomFluids.getEnvironmentAttribute(instance, EnvironmentAttribute.FAST_LAVA);
	}
	
	public static void fizz(Instance instance, BlockVec point) {
		PacketSendingUtils.sendGroupedPacket(
				instance.getChunkAt(point).getViewers(),
				new WorldEventPacket(WorldEvent.LAVA_FIZZ.id(), point, 0, false)
		);
	}
}
