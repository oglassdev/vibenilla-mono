package io.github.togar2.fluids;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import net.minestom.server.world.attribute.EnvironmentAttributeMap;

public class MinestomFluids {
    public static final Fluid WATER = new WaterFluid();
    public static final Fluid LAVA = new LavaFluid();
    public static final Fluid EMPTY = new EmptyFluid();

    public static final FluidState AIR_STATE = new FluidState(Block.AIR, EMPTY);

    /**
     * Blocks which always contain a water source but never accept a fluid being placed into them.
     */
    private static final List<Block> WATER_PLANTS =
            List.of(Block.KELP, Block.KELP_PLANT, Block.SEAGRASS, Block.TALL_SEAGRASS);

    private static final Map<Integer, WaterlogHandler> WATERLOG_HANDLERS =
            new ConcurrentHashMap<>();

    private static final Tag<FluidTicks> TICKS = Tag.Transient("fluid-ticks");

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
        var ticks = instance.getTag(TICKS);
        if (ticks == null) return;

        for (var tick : ticks.pollDue(instance.getWorldAge())) {
            if (!instance.isChunkLoaded(tick.point())) continue;

            FluidState state = FluidState.of(instance.getBlock(tick.point()));
            if (state.fluid() == tick.fluid()) state.fluid().onTick(instance, tick.point(), state);
        }
    }

    public static void tick(Instance instance, BlockVec point) {
        FluidState state = FluidState.of(instance.getBlock(point));
        state.fluid().onTick(instance, point, state);
    }

    public static void scheduleTick(Instance instance, BlockVec point, FluidState state) {
        scheduleTick(
                instance, point, state.fluid(), state.fluid().getNextTickDelay(instance, point));
    }

    /** Schedules a tick for the fluid currently at the position. */
    public static void scheduleTick(Instance instance, BlockVec point, int tickDelay) {
        scheduleTick(instance, point, FluidState.of(instance.getBlock(point)).fluid(), tickDelay);
    }

    /**
     * Schedules a tick for the fluid. It is dropped when the position no longer holds that fluid
     * once it comes due, and ignored when the position already has a tick pending for the fluid.
     */
    public static void scheduleTick(Instance instance, BlockVec point, Fluid fluid, int tickDelay) {
        if (tickDelay == -1) return;

        instance.updateAndGetTag(TICKS, ticks -> ticks != null ? ticks : new FluidTicks())
                .schedule(point, fluid, instance.getWorldAge() + tickDelay);
    }

    public static void registerWaterlog(Block block, WaterlogHandler handler) {
        WATERLOG_HANDLERS.put(block.id(), handler);
    }

    public static WaterlogHandler getWaterlog(Block block) {
        return WATERLOG_HANDLERS.get(block.id());
    }

    /**
     * Resolves an environment attribute for the dimension the instance is in, falling back to the
     * attribute default when the dimension does not override it.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getEnvironmentAttribute(
            Instance instance, EnvironmentAttribute<T> attribute) {
        T value = attribute.defaultValue();
        EnvironmentAttributeMap.Entry<?, ?> entry =
                instance.getCachedDimensionType().attributes().entries().get(attribute);
        if (entry == null) return value;

        EnvironmentAttributeMap.Entry<T, Object> typedEntry =
                (EnvironmentAttributeMap.Entry<T, Object>) entry;
        return typedEntry.modifier().modify(value, typedEntry.argument());
    }

    /** Registers fluid behavior in the global block manager, see {@link #init(BlockManager)}. */
    public static void init() {
        init(MinecraftServer.getBlockManager());
    }

    /**
     * Registers fluid behavior. Waterloggable blocks keep the placement rule registered for them,
     * so this must be called after the rules of other modules are registered, for example after
     * {@code Registrations.registerAllVanilla(blockManager)}. A rule registered for such a block
     * later replaces the fluid behavior of that block.
     */
    public static void init(BlockManager blockManager) {
        blockManager.registerBlockPlacementRule(new FluidPlacementRule(Block.WATER));
        blockManager.registerBlockPlacementRule(new LavaPlacementRule(Block.LAVA));

        for (Block block : WATER_PLANTS) {
            registerWaterlog(block, WaterlogHandler.REJECTING);
        }

        for (Block block : Block.values()) {
            if (!FluidState.canBeWaterlogged(block)) continue;

            registerWaterlog(block, WaterlogHandler.DEFAULT);
            var existing = blockManager.getBlockPlacementRule(block);
            if (existing instanceof FluidPlacementRule) continue;
            blockManager.registerBlockPlacementRule(new FluidPlacementRule(block, existing));
        }
    }

    public static EventNode<Event> events() {
        EventNode<Event> node = EventNode.all("fluid-events");
        node.addListener(InstanceTickEvent.class, MinestomFluids::tick);
        return node;
    }

    /**
     * Bucket gameplay and waterlogged block handling for players, see {@link FluidBuckets}. This is
     * not part of {@link #events()}.
     */
    public static EventNode<Event> bucketEvents() {
        return FluidBuckets.events();
    }
}
