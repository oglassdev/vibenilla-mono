package dev.oglass.vibenilla.server;

import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.Enchantment;

/** Drops a dying survival player's inventory and experience unless keepInventory is enabled. */
final class Deaths {
    private static final boolean KEEP_INVENTORY = Boolean.getBoolean("vibenilla.keepInventory");
    private static final int CRAFTING_RESULT_SLOT = 36;

    private Deaths() {}

    static EventNode<Event> events() {
        return EventNode.all("deaths").addListener(PlayerDeathEvent.class, Deaths::onDeath);
    }

    private static void onDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();
        if (KEEP_INVENTORY || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        // Closing returns any crafting grid contents to the inventory so they drop too.
        player.closeInventory();

        var instance = player.getInstance();
        var position = player.getPosition().add(0, player.getEyeHeight() - 0.3, 0);
        var inventory = player.getInventory();

        for (var slot = 0; slot < PlayerInventory.INVENTORY_SIZE; slot++) {
            if (slot != CRAFTING_RESULT_SLOT) {
                dropUnlessVanishing(instance, position, inventory.getItemStack(slot));
            }
        }
        dropUnlessVanishing(instance, position, inventory.getCursorItem());

        inventory.clear();
        inventory.setCursorItem(ItemStack.AIR);

        Experience.dropAndReset(player, position);
    }

    private static void dropUnlessVanishing(Instance instance, Point position, ItemStack item) {
        if (item.isAir()) {
            return;
        }
        var enchantments = item.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && enchantments.has(Enchantment.VANISHING_CURSE)) {
            return;
        }
        Items.spawn(instance, position, item);
    }
}
