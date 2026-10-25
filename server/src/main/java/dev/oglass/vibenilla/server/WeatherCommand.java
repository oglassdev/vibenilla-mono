package dev.oglass.vibenilla.server;

import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;

/** {@code /weather clear|rain|thunder [duration]}. */
final class WeatherCommand extends Command {
    WeatherCommand(Weathers weathers) {
        super("weather");

        var duration = ArgumentType.Time("duration").min(0);
        for (var type : new String[] {"clear", "rain", "thunder"}) {
            var literal = ArgumentType.Literal(type);
            this.addSyntax((sender, context) -> set(weathers, sender, type, -1), literal);
            this.addSyntax(
                    (sender, context) ->
                            set(
                                    weathers,
                                    sender,
                                    type,
                                    (int) (context.get(duration).toMillis() / 50)),
                    literal,
                    duration);
        }
    }

    private static void set(Weathers weathers, CommandSender sender, String type, int ticks) {
        var random = ThreadLocalRandom.current();
        weathers.set(
                switch (type) {
                    case "clear" ->
                            WeatherCycle.clear(
                                    ticks >= 0 ? ticks : WeatherCycle.randomClearDuration(random));
                    case "rain" ->
                            WeatherCycle.rain(
                                    ticks >= 0 ? ticks : WeatherCycle.randomRainDuration(random));
                    default ->
                            WeatherCycle.thunder(
                                    ticks >= 0
                                            ? ticks
                                            : WeatherCycle.randomThunderDuration(random));
                });
        sender.sendMessage(Component.translatable("commands.weather.set." + type));
    }
}
