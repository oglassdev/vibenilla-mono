package dev.oglass.vibenilla.server;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.goldenstack.loot.LootContext;
import net.goldenstack.loot.LootTable;
import net.goldenstack.loot.Trove;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Drops a broken block's vanilla loot table. */
final class BlockDrops {
    private final Map<Key, LootTable> tables;

    BlockDrops(Path lootTableDirectory) {
        this.tables = Trove.readTables(lootTableDirectory);
    }

    void drop(PlayerBlockBreakEvent event) {
        var player = event.getPlayer();
        generate(
                event.getInstance(),
                event.getBlockPosition(),
                event.getBlock(),
                player.getItemInMainHand(),
                player);
    }

    /** Drops the loot of a block that broke without a tool or a player, such as decayed leaves. */
    void drop(Instance instance, Point position, Block block) {
        generate(instance, position, block, ItemStack.AIR, null);
    }

    private void generate(
            Instance instance,
            Point position,
            Block block,
            ItemStack tool,
            @Nullable Player player) {
        var table =
                this.tables.get(Key.key(block.key().namespace(), "blocks/" + block.key().value()));
        if (table == null) {
            return;
        }

        var parameters = new HashMap<LootContext.Key<?>, Object>();
        parameters.put(LootContext.RANDOM, ThreadLocalRandom.current());
        parameters.put(LootContext.WORLD, instance);
        parameters.put(LootContext.ORIGIN, position);
        parameters.put(LootContext.BLOCK_STATE, block);
        parameters.put(LootContext.TOOL, tool);
        if (player != null) {
            parameters.put(LootContext.THIS_ENTITY, player);
        }

        var context = LootContext.from(VanillaInterface.defaults(), parameters);
        for (var item : table.generate(context)) {
            Items.spawn(instance, position.add(0.5, 0.5, 0.5), item);
        }
    }
}
