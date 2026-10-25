package dev.oglass.vibenilla.server;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.instance.Instance;

/** {@code /time set day|noon|night|midnight|<ticks>} and {@code /time add <ticks>}. */
final class TimeCommand extends Command {
    private static final Map<String, Integer> PRESETS =
            Map.of("day", 1000, "noon", 6000, "night", 13000, "midnight", 18000);

    TimeCommand(Instance instance) {
        super("time");

        var set = ArgumentType.Literal("set");
        var preset = ArgumentType.Word("time").from(PRESETS.keySet().toArray(String[]::new));
        var time = TickDuration.argument("time", 0);
        var amount = TickDuration.argument("amount", Integer.MIN_VALUE);
        this.addSyntax(
                (sender, context) -> set(instance, sender, PRESETS.get(context.get(preset))),
                set,
                preset);
        this.addSyntax((sender, context) -> set(instance, sender, context.get(time)), set, time);
        this.addSyntax(
                (sender, context) -> {
                    var ticks = context.get(amount);
                    instance.setTime(instance.getTime() + ticks);
                    sender.sendMessage(
                            Component.translatable("commands.time.add", Component.text(ticks)));
                },
                ArgumentType.Literal("add"),
                amount);
    }

    private static void set(Instance instance, CommandSender sender, int time) {
        instance.setTime(time);
        sender.sendMessage(Component.translatable("commands.time.set", Component.text(time)));
    }
}
