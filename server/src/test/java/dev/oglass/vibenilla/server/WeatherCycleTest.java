package dev.oglass.vibenilla.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeatherCycleTest {
    @Test
    void clearTimeCountsDownAndForcesClearSkies() {
        var cycle = new WeatherCycle(2, 50, 50, true, true).advance(new Random(1));
        assertEquals(new WeatherCycle(1, 0, 0, false, false), cycle);
    }

    @Test
    void flipsWhenTheTimerReachesZero() {
        var cycle = new WeatherCycle(0, 1, 5, false, false).advance(new Random(1));
        assertTrue(cycle.raining());
        assertEquals(0, cycle.rainTime());
        assertEquals(4, cycle.thunderTime());
        assertFalse(cycle.thundering());
    }

    @Test
    void drawsVanillaDurationsWhenTimersRunOut() {
        var random = new Random(7);
        for (var i = 0; i < 100; i++) {
            var dry = WeatherCycle.INITIAL.advance(random);
            assertTrue(dry.rainTime() >= 12000 && dry.rainTime() <= 180000);
            assertTrue(dry.thunderTime() >= 12000 && dry.thunderTime() <= 180000);
            var storm = new WeatherCycle(0, 0, 0, true, true).advance(random);
            assertTrue(storm.rainTime() >= 12000 && storm.rainTime() <= 24000);
            assertTrue(storm.thunderTime() >= 3600 && storm.thunderTime() <= 15600);
        }
    }

    @Test
    void roundTripsThroughProperties() {
        var cycle = new WeatherCycle(10, 20, 30, true, false);
        var properties = new Properties();
        cycle.store(properties);
        assertEquals(cycle, WeatherCycle.load(properties));
    }
}
