package io.github.togar2.fluids;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import net.minestom.server.world.attribute.EnvironmentAttributeMap;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MinestomFluids {
	public static final Fluid WATER = new WaterFluid();
	public static final Fluid LAVA = new LavaFluid();
	public static final Fluid EMPTY = new EmptyFluid();
	
	public static final FluidState AIR_STATE = new FluidState(Block.AIR, EMPTY);
	
	/**
	 * Blocks which always contain a water source but never accept a fluid being placed into them.
	 */
	private static final List<Block> WATER_PLANTS = List.of(
			Block.KELP, Block.KELP_PLANT, Block.SEAGRASS, Block.TALL_SEAGRASS
	);
	
	private static final Map<Integer, WaterlogHandler> WATERLOG_HANDLERS = new ConcurrentHashMap<>();
	
	private static final Tag<Map<Long, Set<BlockVec>>> TICK_UPDATES = Tag.Transient("fluid-tick-updates");
	
	public static Fluid get(Block block) {
		if (block.compare(Block.LAVA)) {
			return LAVA;
		} else if (block.fluid()) {
			return WATER;
		} else {
			return EMPTY;
		}
	}
	
	public static void tick(InstanceTickEvent event) {
		Instance instance = event.getInstance();
		long age = instance.getWorldAge();
		
		var updates = instance.getTag(TICK_UPDATES);
		if (updates == null) return;
		
		for (var iterator = updates.entrySet().iterator(); iterator.hasNext(); ) {
			var entry = iterator.next();
			if (entry.getKey() > age) continue;
			
			Set<BlockVec> currentUpdate = entry.getValue();
			iterator.remove();
			
			for (BlockVec point : currentUpdate) {
				tick(instance, point);
			}
		}
	}
	
	public static void tick(Instance instance, BlockVec point) {
		FluidState state = FluidState.of(instance.getBlock(point));
		state.fluid().onTick(instance, point, state);
	}
	
	public static void scheduleTick(Instance instance, BlockVec point, FluidState state) {
		scheduleTick(instance, point, state.fluid().getNextTickDelay(instance, point));
	}
	
	public static void scheduleTick(Instance instance, BlockVec point, int tickDelay) {
		if (tickDelay == -1) return;
		
		var updates = instance.getTag(TICK_UPDATES);
		if (updates == null) {
			updates = new ConcurrentHashMap<>();
			instance.setTag(TICK_UPDATES, updates);
		}
		
		long newAge = instance.getWorldAge() + tickDelay;
		updates.computeIfAbsent(newAge, l -> new HashSet<>()).add(point);
	}
	
	public static void registerWaterlog(Block block, WaterlogHandler handler) {
		WATERLOG_HANDLERS.put(block.id(), handler);
	}
	
	public static WaterlogHandler getWaterlog(Block block) {
		return WATERLOG_HANDLERS.get(block.id());
	}
	
	/**
	 * Resolves an environment attribute for the dimension the instance is in,
	 * falling back to the attribute default when the dimension does not override it.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getEnvironmentAttribute(Instance instance, EnvironmentAttribute<T> attribute) {
		T value = attribute.defaultValue();
		EnvironmentAttributeMap.Entry<?, ?> entry = instance.getCachedDimensionType()
				.attributes().entries().get(attribute);
		if (entry == null) return value;
		
		EnvironmentAttributeMap.Entry<T, Object> typedEntry = (EnvironmentAttributeMap.Entry<T, Object>) entry;
		return typedEntry.modifier().modify(value, typedEntry.argument());
	}
	
	public static void init() {
		MinecraftServer.getBlockManager().registerBlockPlacementRule(new FluidPlacementRule(Block.WATER));
		MinecraftServer.getBlockManager().registerBlockPlacementRule(new LavaPlacementRule(Block.LAVA));
		
		for (Block block : WATER_PLANTS) {
			registerWaterlog(block, WaterlogHandler.REJECTING);
		}
		
		for (Block block : Block.values()) {
			if (FluidState.canBeWaterlogged(block)) {
				registerWaterlog(block, WaterlogHandler.DEFAULT);
				MinecraftServer.getBlockManager().registerBlockPlacementRule(new FluidPlacementRule(block));
			}
		}
	}
	
	public static EventNode<Event> events() {
		EventNode<Event> node = EventNode.all("fluid-events");
		node.addListener(InstanceTickEvent.class, MinestomFluids::tick);
		return node;
	}
}
