package io.github.togar2.fluids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minestom.server.coordinate.BlockVec;
import org.junit.jupiter.api.Test;

final class FluidTicksTest {
    private static final BlockVec POINT = new BlockVec(1, 2, 3);

    @Test
    void positionHasOnePendingTickPerFluid() {
        var ticks = new FluidTicks();
        ticks.schedule(POINT, MinestomFluids.WATER, 5);
        ticks.schedule(POINT, MinestomFluids.WATER, 3);

        assertTrue(ticks.pollDue(4).isEmpty());
        assertEquals(1, ticks.pollDue(5).size());
        assertTrue(ticks.pollDue(100).isEmpty());
    }

    @Test
    void scheduledFluidReplacesPendingTickOfOtherFluid() {
        var ticks = new FluidTicks();
        ticks.schedule(POINT, MinestomFluids.WATER, 5);
        ticks.schedule(POINT, MinestomFluids.LAVA, 30);

        assertTrue(ticks.pollDue(5).isEmpty());
        var due = ticks.pollDue(30);
        assertEquals(1, due.size());
        assertSame(MinestomFluids.LAVA, due.getFirst().fluid());
    }

    @Test
    void dueTicksComeOutInDeadlineOrder() {
        var ticks = new FluidTicks();
        var first = new BlockVec(0, 0, 0);
        var second = new BlockVec(1, 0, 0);
        var third = new BlockVec(2, 0, 0);
        ticks.schedule(third, MinestomFluids.WATER, 7);
        ticks.schedule(first, MinestomFluids.WATER, 2);
        ticks.schedule(second, MinestomFluids.LAVA, 2);

        var points = ticks.pollDue(10).stream().map(FluidTicks.Tick::point).toList();
        assertEquals(List.of(first, second, third), points);
    }
}
