package dev.oglass.vibenilla.containers;

import java.util.Objects;
import net.goldenstack.loot.Trove;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.BlockHandler;

final class ContainerHandler implements BlockHandler {
    private final ContainerKind kind;
    private final Sessions sessions;

    ContainerHandler(ContainerKind kind, Sessions sessions) {
        this.kind = kind;
        this.sessions = sessions;
    }

    @Override
    public Key getKey() {
        return kind.key();
    }

    @Override
    public boolean onInteract(Interaction interaction) {
        var player = interaction.getPlayer();

        if (player.isSneaking()
                && (!player.getItemInMainHand().isAir() || !player.getItemInOffHand().isAir())) {
            return true;
        }

        if (player.getGameMode() != GameMode.SPECTATOR) {
            sessions.open(
                    player,
                    interaction.getInstance(),
                    interaction.getBlockPosition().asBlockVec(),
                    kind);
        }
        return false;
    }

    @Override
    public void onDestroy(Destroy destroy) {
        var block = destroy.getBlock();
        var next = destroy.getNewBlock();
        var instance = destroy.getInstance();
        var position = destroy.getBlockPosition().asBlockVec();

        if (next.compare(block)) {
            if (!Objects.equals(block.getProperty("type"), next.getProperty("type"))
                    || !Objects.equals(block.getProperty("facing"), next.getProperty("facing"))) {
                sessions.closeAt(instance, position);
            }
            return;
        }

        sessions.closeAt(instance, position);

        var unpacked = LootFiller.unpack(sessions.lootTables(), instance, position, block, null);

        for (var item : ContainerItems.read(unpacked, ContainerKind.SLOTS_PER_BLOCK)) {
            if (!item.isAir()) {
                Trove.blockDrop(instance, item, position);
            }
        }
    }
}
