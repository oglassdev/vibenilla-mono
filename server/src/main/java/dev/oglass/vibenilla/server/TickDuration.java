package dev.oglass.vibenilla.server;

import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.exception.ArgumentSyntaxException;

/**
 * A command duration in ticks: a number followed by {@code d} (24000 ticks), {@code s} (20 ticks)
 * or {@code t} (one tick, also the default), rounded after multiplying.
 */
final class TickDuration {
    private static final int INVALID_UNIT = 1;
    private static final int TOO_LOW = 2;

    private TickDuration() {}

    static Argument<Integer> argument(String id, int minimum) {
        return ArgumentType.Word(id).map(input -> parse(input, minimum));
    }

    static int parse(String input, int minimum) {
        var end = 0;
        while (end < input.length() && "0123456789.-".indexOf(input.charAt(end)) >= 0) {
            end++;
        }

        float amount;
        try {
            amount = Float.parseFloat(input.substring(0, end));
        } catch (NumberFormatException e) {
            throw new ArgumentSyntaxException("Invalid duration", input, INVALID_UNIT);
        }
        var factor =
                switch (input.substring(end)) {
                    case "", "t" -> 1;
                    case "s" -> 20;
                    case "d" -> 24000;
                    default ->
                            throw new ArgumentSyntaxException("Invalid unit", input, INVALID_UNIT);
                };

        var ticks = Math.round(amount * factor);
        if (ticks < minimum) {
            throw new ArgumentSyntaxException(
                    "Tick count must not be less than " + minimum, input, TOO_LOW);
        }
        return ticks;
    }
}
