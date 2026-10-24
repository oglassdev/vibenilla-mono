package io.github.togar2.fluids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minestom.server.coordinate.BlockVec;

/**
 * The fluid ticks pending in one instance. A position has at most one pending tick, which is
 * remembered together with the fluid that scheduled it.
 */
final class FluidTicks {
    record Tick(BlockVec point, Fluid fluid, long deadline, long sequence) {}

    private final Map<BlockVec, Tick> pending = new HashMap<>();
    private final PriorityQueue<Tick> queue =
            new PriorityQueue<>(
                    Comparator.comparingLong(Tick::deadline).thenComparingLong(Tick::sequence));
    private long sequence;

    /**
     * Schedules a tick unless the position already has one pending for the same fluid. A pending
     * tick of another fluid is replaced.
     */
    synchronized void schedule(BlockVec point, Fluid fluid, long deadline) {
        var existing = pending.get(point);
        if (existing != null && existing.fluid() == fluid) return;

        var tick = new Tick(point, fluid, deadline, sequence++);
        pending.put(point, tick);
        queue.add(tick);
    }

    /** Removes and returns the ticks due at {@code age}, earliest deadline first. */
    synchronized List<Tick> pollDue(long age) {
        var due = new ArrayList<Tick>();
        while (!queue.isEmpty() && queue.peek().deadline() <= age) {
            var tick = queue.poll();
            if (pending.get(tick.point()) != tick) continue;
            pending.remove(tick.point());
            due.add(tick);
        }
        return due;
    }
}
