package dev.oglass.vibenilla.server;

import java.util.Properties;
import java.util.random.RandomGenerator;

/**
 * Vanilla's weather timers. While {@code clearTime} runs the weather stays clear; afterwards rain
 * and thunder each flip when their own timer reaches zero and then draw a new random duration.
 */
record WeatherCycle(
        int clearTime, int rainTime, int thunderTime, boolean raining, boolean thundering) {
    static final WeatherCycle INITIAL = new WeatherCycle(0, 0, 0, false, false);

    /** The cycle after one tick. */
    WeatherCycle advance(RandomGenerator random) {
        var clear = clearTime;
        var thunder = thunderTime;
        var rain = rainTime;
        var isThundering = thundering;
        var isRaining = raining;
        if (clear > 0) {
            clear--;
            thunder = isThundering ? 0 : 1;
            rain = isRaining ? 0 : 1;
            isThundering = false;
            isRaining = false;
        } else {
            if (thunder > 0) {
                thunder--;
                if (thunder == 0) {
                    isThundering = !isThundering;
                }
            } else if (isThundering) {
                thunder = between(random, 3600, 15600);
            } else {
                thunder = between(random, 12000, 180000);
            }

            if (rain > 0) {
                rain--;
                if (rain == 0) {
                    isRaining = !isRaining;
                }
            } else if (isRaining) {
                rain = between(random, 12000, 24000);
            } else {
                rain = between(random, 12000, 180000);
            }
        }
        return new WeatherCycle(clear, rain, thunder, isRaining, isThundering);
    }

    /** Ends any rain and thunder and restarts both timers. */
    WeatherCycle reset() {
        return new WeatherCycle(clearTime, 0, 0, false, false);
    }

    static WeatherCycle clear(int duration) {
        return new WeatherCycle(duration, 0, 0, false, false);
    }

    static WeatherCycle rain(int duration) {
        return new WeatherCycle(0, duration, duration, true, false);
    }

    static WeatherCycle thunder(int duration) {
        return new WeatherCycle(0, duration, duration, true, true);
    }

    static int randomClearDuration(RandomGenerator random) {
        return between(random, 12000, 180000);
    }

    static int randomRainDuration(RandomGenerator random) {
        return between(random, 12000, 24000);
    }

    static int randomThunderDuration(RandomGenerator random) {
        return between(random, 3600, 15600);
    }

    void store(Properties properties) {
        properties.setProperty("clearWeatherTime", Integer.toString(clearTime));
        properties.setProperty("rainTime", Integer.toString(rainTime));
        properties.setProperty("thunderTime", Integer.toString(thunderTime));
        properties.setProperty("raining", Boolean.toString(raining));
        properties.setProperty("thundering", Boolean.toString(thundering));
    }

    static WeatherCycle load(Properties properties) {
        return new WeatherCycle(
                Integer.parseInt(properties.getProperty("clearWeatherTime", "0")),
                Integer.parseInt(properties.getProperty("rainTime", "0")),
                Integer.parseInt(properties.getProperty("thunderTime", "0")),
                Boolean.parseBoolean(properties.getProperty("raining")),
                Boolean.parseBoolean(properties.getProperty("thundering")));
    }

    private static int between(RandomGenerator random, int min, int max) {
        return random.nextInt(min, max + 1);
    }
}
