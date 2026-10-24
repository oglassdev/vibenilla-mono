package dev.oglass.vibenilla.ticking;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;

/**
 * Vanilla random block ticks. Each instance tick, every loaded chunk within the simulation distance
 * of a player picks {@code speed} random positions in each non-empty section and runs the handlers
 * registered for the block found there.
 */
public final class RandomTicks {
    public static final int DEFAULT_SPEED = 3;
    public static final int DEFAULT_SIMULATION_DISTANCE = 10;

    private static final RandomTickHandler NONE = (instance, position, block, random) -> {};
    private static final int SECTION_VOLUME = 16 * 16 * 16;

    private record Registration(Predicate<Block> matcher, RandomTickHandler handler) {}

    private final List<Registration> registrations = new CopyOnWriteArrayList<>();
    private final EventNode<Event> events = EventNode.all("random-ticks");
    private final int speed;
    private final int simulationDistance;
    private volatile RandomTickHandler[] handlersByState =
            new RandomTickHandler[Block.statesCount()];

    public RandomTicks() {
        this(DEFAULT_SPEED, DEFAULT_SIMULATION_DISTANCE);
    }

    /**
     * @param speed positions ticked per section per instance tick, vanilla's {@code
     *     randomTickSpeed}
     * @param simulationDistance chunk radius around each player in which blocks are ticked
     */
    public RandomTicks(int speed, int simulationDistance) {
        this.speed = speed;
        this.simulationDistance = simulationDistance;
        this.events.addListener(InstanceTickEvent.class, event -> tick(event.getInstance()));
    }

    /** Runs the handler for every state of the block. */
    public void register(Block block, RandomTickHandler handler) {
        register(candidate -> candidate.compare(block), handler);
    }

    /**
     * Runs the handler for every block state matching the predicate. The predicate is evaluated at
     * most once per state, so it must not depend on anything but the state.
     */
    public void register(Predicate<Block> matcher, RandomTickHandler handler) {
        this.registrations.add(new Registration(matcher, handler));
        this.handlersByState = new RandomTickHandler[Block.statesCount()];
    }

    /**
     * The node ticking every instance. Other listeners that belong with random ticks can be added
     * to it.
     */
    public EventNode<Event> events() {
        return this.events;
    }

    /** Ticks the chunks near the players of the instance once. */
    public void tick(Instance instance) {
        if (this.speed <= 0) {
            return;
        }

        var near = new LongOpenHashSet();
        for (var player : instance.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            var position = player.getPosition();
            for (var x = -this.simulationDistance; x <= this.simulationDistance; x++) {
                for (var z = -this.simulationDistance; z <= this.simulationDistance; z++) {
                    near.add(chunkIndex(position.chunkX() + x, position.chunkZ() + z));
                }
            }
        }

        var random = ThreadLocalRandom.current();
        var picks = new int[this.speed];
        for (long index : near) {
            var chunk = instance.getChunk((int) (index >> 32), (int) index);
            if (chunk != null && chunk.isLoaded()) {
                tickChunk(instance, chunk, picks, random);
            }
        }
    }

    private void tickChunk(Instance instance, Chunk chunk, int[] picks, ThreadLocalRandom random) {
        var sections = chunk.getSections();
        for (var index = 0; index < sections.size(); index++) {
            var count = pick(chunk, sections.get(index).blockPalette(), picks, random);
            var minX = chunk.getChunkX() << 4;
            var minY = (chunk.getMinSection() + index) << 4;
            var minZ = chunk.getChunkZ() << 4;
            for (var pick = 0; pick < count; pick++) {
                var offset = picks[pick];
                var position =
                        new BlockVec(
                                minX + (offset & 15),
                                minY + ((offset >> 8) & 15),
                                minZ + ((offset >> 4) & 15));
                var block = instance.getBlock(position);
                var handler = handlerFor(block.stateId());
                if (handler == NONE) {
                    continue;
                }

                try {
                    handler.tick(instance, position, block, random);
                } catch (Exception exception) {
                    MinecraftServer.getExceptionManager().handleException(exception);
                }
            }
        }
    }

    /** Fills {@code picks} with the section offsets of random positions holding a handled block. */
    private int pick(Chunk chunk, Palette palette, int[] picks, ThreadLocalRandom random) {
        chunk.lockReadLock();
        try {
            if (palette.count() == 0
                    || (palette.bitsPerEntry() == 0 && handlerFor(palette.singleValue()) == NONE)) {
                return 0;
            }

            var count = 0;
            for (var pick = 0; pick < this.speed; pick++) {
                var offset = random.nextInt(SECTION_VOLUME);
                var state = palette.get(offset & 15, (offset >> 8) & 15, (offset >> 4) & 15);
                if (handlerFor(state) != NONE) {
                    picks[count++] = offset;
                }
            }
            return count;
        } finally {
            chunk.unlockReadLock();
        }
    }

    private static long chunkIndex(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private RandomTickHandler handlerFor(int stateId) {
        var cache = this.handlersByState;
        var handler = cache[stateId];
        if (handler == null) {
            handler = resolve(stateId);
            cache[stateId] = handler;
        }
        return handler;
    }

    private RandomTickHandler resolve(int stateId) {
        var block = Block.fromStateId(stateId);
        if (block == null) {
            return NONE;
        }

        var handlers =
                this.registrations.stream()
                        .filter(registration -> registration.matcher.test(block))
                        .map(Registration::handler)
                        .toList();
        return switch (handlers.size()) {
            case 0 -> NONE;
            case 1 -> handlers.getFirst();
            default ->
                    (instance, position, current, random) ->
                            handlers.forEach(
                                    handler -> handler.tick(instance, position, current, random));
        };
    }
}
