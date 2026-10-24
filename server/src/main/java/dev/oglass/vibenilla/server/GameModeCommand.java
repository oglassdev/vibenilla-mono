package dev.oglass.vibenilla.server;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;

final class GameModeCommand extends Command {
    GameModeCommand() {
        super("gamemode", "gm");

        var mode =
                ArgumentType.Enum("mode", GameMode.class)
                        .setFormat(ArgumentEnum.Format.LOWER_CASED);
        this.setCondition((sender, command) -> sender instanceof Player);
        this.addSyntax((sender, context) -> ((Player) sender).setGameMode(context.get(mode)), mode);
    }
}
