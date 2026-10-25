package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;

/**
 * The overworld's day time, game time and weather cycle, kept in {@code environment.properties}
 * next to the world metadata and rewritten atomically on every save.
 */
final class WorldEnvironment {
    private final Path file;
    private final Instance instance;
    private final Weathers weathers;

    WorldEnvironment(Path file, Instance instance) {
        this.file = file;
        this.instance = instance;
        var properties = read(file);
        var cycle = WeatherCycle.INITIAL;
        if (properties != null) {
            try {
                instance.setTime(Long.parseLong(properties.getProperty("dayTime", "0")));
                instance.setWorldAge(Long.parseLong(properties.getProperty("gameTime", "0")));
                cycle = WeatherCycle.load(properties);
            } catch (NumberFormatException e) {
                MinecraftServer.LOGGER.error("Invalid environment file {}", file, e);
            }
        }
        this.weathers = new Weathers(instance, cycle);
    }

    Weathers weathers() {
        return weathers;
    }

    void save() {
        var properties = new Properties();
        properties.setProperty("dayTime", Long.toString(instance.getTime()));
        properties.setProperty("gameTime", Long.toString(instance.getWorldAge()));
        weathers.cycle().store(properties);
        var temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var out = Files.newOutputStream(temp)) {
            properties.store(out, null);
            AtomicFiles.replace(temp, file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Properties read(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try (var in = Files.newInputStream(file)) {
            var properties = new Properties();
            properties.load(in);
            return properties;
        } catch (IOException e) {
            MinecraftServer.LOGGER.error("Failed to read environment file {}", file, e);
            return null;
        }
    }
}
