package dev.oglass.vibenilla.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.Enchantment;

/** Drops a dying survival player's inventory and experience unless keepInventory is enabled. */
final class Deaths {
    private static final boolean KEEP_INVENTORY = Boolean.getBoolean("vibenilla.keepInventory");
    private static final Duration PICKUP_DELAY = Duration.ofSeconds(2);
    private static final int CRAFTING_RESULT_SLOT = 36;
    private static final int PLAYER_GRID_START = 37;
    private static final int PLAYER_GRID_END = 40;
    private static final int TABLE_GRID_START = 1;
    private static final int TABLE_GRID_END = 9;

    private Deaths() {}

    static EventNode<Event> events() {
        return EventNode.all("deaths").addListener(PlayerDeathEvent.class, Deaths::onDeath);
    }

    private static void onDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();
        if (KEEP_INVENTORY || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        var inventory = player.getInventory();
        var drops = new ArrayList<ItemStack>();

        // Taken before closing, which would otherwise drop the cursor as an ordinary item.
        drops.add(inventory.getCursorItem());
        inventory.setCursorItem(ItemStack.AIR);

        // Crafting table contents are taken here so closing has nothing to return.
        if (player.getOpenInventory() instanceof Inventory open
                && open.getInventoryType() == InventoryType.CRAFTING) {
            for (var slot = TABLE_GRID_START; slot <= TABLE_GRID_END; slot++) {
                drops.add(open.getItemStack(slot));
                open.setItemStack(slot, ItemStack.AIR);
            }
        }
        player.closeInventory();

        // Emptying the grid first leaves the result empty, so clearing it crafts nothing.
        for (var slot = PLAYER_GRID_START; slot <= PLAYER_GRID_END; slot++) {
            drops.add(inventory.getItemStack(slot));
            inventory.setItemStack(slot, ItemStack.AIR);
        }
        for (var slot = 0; slot < PlayerInventory.INVENTORY_SIZE; slot++) {
            if (slot != CRAFTING_RESULT_SLOT
                    && (slot < PLAYER_GRID_START || slot > PLAYER_GRID_END)) {
                drops.add(inventory.getItemStack(slot));
            }
        }
        inventory.clear();

        var instance = player.getInstance();
        var position = player.getPosition().add(0, player.getEyeHeight() - 0.3, 0);
        for (var item : drops) {
            dropUnlessVanishing(instance, position, item);
        }

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

        var random = ThreadLocalRandom.current();
        var entity = new ItemEntity(item);
        entity.setPickupDelay(PICKUP_DELAY);
        entity.setInstance(instance, position);
        entity.setVelocity(
                new Vec(
                        random.nextDouble(-2, 2),
                        random.nextDouble(2, 4),
                        random.nextDouble(-2, 2)));
    }
}
