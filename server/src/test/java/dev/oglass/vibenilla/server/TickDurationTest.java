package dev.oglass.vibenilla.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minestom.server.command.builder.exception.ArgumentSyntaxException;
import org.junit.jupiter.api.Test;

class TickDurationTest {
    @Test
    void unitsMatchVanilla() {
        assertEquals(24000, TickDuration.parse("1d", 0));
        assertEquals(40, TickDuration.parse("2s", 0));
        assertEquals(7, TickDuration.parse("7t", 0));
        assertEquals(7, TickDuration.parse("7", 0));
    }

    @Test
    void fractionsRoundAfterMultiplying() {
        assertEquals(12000, TickDuration.parse("0.5d", 0));
        assertEquals(30, TickDuration.parse("1.5s", 0));
        assertEquals(3, TickDuration.parse("2.5", 0));
    }

    @Test
    void enforcesTheMinimum() {
        assertEquals(-5, TickDuration.parse("-5", Integer.MIN_VALUE));
        assertThrows(ArgumentSyntaxException.class, () -> TickDuration.parse("-1", 0));
        assertThrows(ArgumentSyntaxException.class, () -> TickDuration.parse("0", 1));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(ArgumentSyntaxException.class, () -> TickDuration.parse("5h", 0));
        assertThrows(ArgumentSyntaxException.class, () -> TickDuration.parse("d", 0));
        assertThrows(ArgumentSyntaxException.class, () -> TickDuration.parse("1.2.3", 0));
    }
}
