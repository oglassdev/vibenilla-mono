package dev.oglass.vibenilla.server;

import java.util.concurrent.ThreadLocalRandom;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Weather;
import net.minestom.server.timer.TaskSchedule;

/**
 * Drives an instance's weather from a {@link WeatherCycle}. Rain and thunder levels move by 0.01
 * per tick towards the state the cycle asks for, and lightning strikes while it thunders.
 */
final class Weathers {
    private static final float LEVEL_STEP = 0.01F;
    private static final float RAINING_LEVEL = 0.2F;
    private static final float THUNDERING_LEVEL = 0.9F;

    private final Instance instance;
    private WeatherCycle cycle;
    private float rainLevel;
    private float thunderLevel;

    Weathers(Instance instance, WeatherCycle cycle) {
        this.instance = instance;
        this.cycle = cycle;
        this.rainLevel = cycle.raining() ? 1.0F : 0.0F;
        this.thunderLevel = cycle.thundering() ? 1.0F : 0.0F;
        apply();
        instance.scheduler().buildTask(this::tick).repeat(TaskSchedule.tick(1)).schedule();
    }

    synchronized WeatherCycle cycle() {
        return cycle;
    }

    synchronized void set(WeatherCycle cycle) {
        this.cycle = cycle;
    }

    /** Ends rain and thunder if either is under way. */
    synchronized void clearStorms() {
        if (rainLevel > RAINING_LEVEL || cycle.raining()) {
            cycle = cycle.reset();
        }
    }

    private synchronized void tick() {
        cycle = cycle.advance(ThreadLocalRandom.current());
        var rain = step(rainLevel, cycle.raining());
        var thunder = step(thunderLevel, cycle.thundering());
        if (rain != rainLevel || thunder != thunderLevel) {
            rainLevel = rain;
            thunderLevel = thunder;
            apply();
        }
        if (rainLevel > RAINING_LEVEL && thunderLevel > THUNDERING_LEVEL) {
            Lightning.tick(instance, ThreadLocalRandom.current());
        }
    }

    private void apply() {
        instance.setWeather(new Weather(rainLevel, thunderLevel), 1);
    }

    private static float step(float level, boolean up) {
        return Math.clamp(level + (up ? LEVEL_STEP : -LEVEL_STEP), 0.0F, 1.0F);
    }
}
