package io.github.togar2.fluids;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.PacketSendingUtils;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class WaterFluid extends FlowableFluid {
	public WaterFluid() {
		super(Block.WATER, Material.WATER_BUCKET);
	}
	
	@Override
	protected boolean isInfinite() {
		return true;
	}
	
	@Override
	protected @Nullable FluidState onBreakingBlock(Instance instance, BlockVec point,
	                                               BlockFace direction, Block block, FluidState newState) {
		FluidBlockBreakEvent event = new FluidBlockBreakEvent(instance, point, direction, block, newState);
		EventDispatcher.call(event);
		return event.isCancelled() ? null : event.getNewState();
	}
	
	@Override
	protected int getHoleRadius(Instance instance) {
		return 4;
	}
	
	@Override
	public int getLevelDecreasePerBlock(Instance instance) {
		return 1;
	}
	
	@Override
	public int getNextTickDelay(Instance instance, BlockVec point) {
		return 5;
	}
	
	@Override
	protected boolean canBeReplacedWith(Instance instance, BlockVec point, FluidState currentState,
	                                    FluidState newState, BlockFace direction) {
		return direction == BlockFace.BOTTOM && !newState.isWater();
	}
	
	/**
	 * @return whether water placed in this instance evaporates instead of being placed, as it does in the nether
	 */
	public static boolean evaporates(Instance instance) {
		return MinestomFluids.getEnvironmentAttribute(instance, EnvironmentAttribute.WATER_EVAPORATES);
	}
	
	/**
	 * Plays the effect shown when water evaporates on placement.
	 */
	public static void evaporate(Instance instance, BlockVec point) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		var viewers = instance.getChunkAt(point).getViewers();
		
		PacketSendingUtils.sendGroupedPacket(viewers, new SoundEffectPacket(
				SoundEvent.BLOCK_FIRE_EXTINGUISH, Sound.Source.BLOCK,
				point.add(0.5, 0.5, 0.5), 0.5f,
				2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f,
				random.nextLong()
		));
		
		for (int i = 0; i < 8; i++) {
			PacketSendingUtils.sendGroupedPacket(viewers, new ParticlePacket(
					Particle.LARGE_SMOKE,
					point.x() + random.nextDouble(),
					point.y() + random.nextDouble(),
					point.z() + random.nextDouble(),
					0, 0, 0, 0, 1
			));
		}
	}
}
