package dev.oglass.vibenilla.server;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.goldenstack.loot.LootContext;
import net.goldenstack.loot.LootTable;
import net.goldenstack.loot.Trove;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;

/** Drops a broken block's vanilla loot table in survival. */
final class BlockDrops {
    private final Map<Key, LootTable> tables;

    /** Tables are keyed by their path relative to the directory, which matches block keys. */
    BlockDrops(Path blockLootTableDirectory) {
        this.tables = Trove.readTables(blockLootTableDirectory);
    }

    EventNode<Event> events() {
        return EventNode.all("block-drops").addListener(PlayerBlockBreakEvent.class, this::onBreak);
    }

    private void onBreak(PlayerBlockBreakEvent event) {
        var player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        var block = event.getBlock();
        var table = this.tables.get(block.key());
        if (table == null) {
            return;
        }

        var position = event.getBlockPosition();
        var context =
                LootContext.from(
                        VanillaInterface.defaults(),
                        Map.of(
                                LootContext.RANDOM,
                                ThreadLocalRandom.current(),
                                LootContext.WORLD,
                                event.getInstance(),
                                LootContext.ORIGIN,
                                position,
                                LootContext.BLOCK_STATE,
                                block,
                                LootContext.TOOL,
                                player.getItemInMainHand(),
                                LootContext.THIS_ENTITY,
                                player));

        for (var item : table.generate(context)) {
            Items.spawn(event.getInstance(), position.add(0.5, 0.5, 0.5), item);
        }
    }
}
