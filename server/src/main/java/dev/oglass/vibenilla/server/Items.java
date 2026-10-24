package dev.oglass.vibenilla.server;

import java.util.concurrent.ThreadLocalRandom;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.inventory.TransactionOption;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.CollectItemPacket;
import net.minestom.server.utils.time.TimeUnit;

/** Item entities: dropping from the inventory, spawning drops, and picking them up. */
final class Items {
    private static final int PICKUP_DELAY_TICKS = 10;
    private static final int THROWN_PICKUP_DELAY_TICKS = 40;
    private static final int LIFETIME_TICKS = 6000;
    private static final float MERGE_RANGE = 0.5f;

    private Items() {}

    static EventNode<Event> events() {
        return EventNode.all("items")
                .addListener(ItemDropEvent.class, Items::onDrop)
                .addListener(PickupItemEvent.class, Items::onPickup);
    }

    static void spawn(Instance instance, Point position, ItemStack item) {
        var random = ThreadLocalRandom.current();
        var entity = create(item, PICKUP_DELAY_TICKS);
        entity.setInstance(instance, position);
        entity.setVelocity(
                new Vec(
                        random.nextDouble(-2, 2),
                        random.nextDouble(2, 4),
                        random.nextDouble(-2, 2)));
    }

    private static ItemEntity create(ItemStack item, int pickupDelayTicks) {
        var entity = new ItemEntity(item);
        entity.setPickupDelay(pickupDelayTicks, TimeUnit.SERVER_TICK);
        entity.setMergeRange(MERGE_RANGE);
        entity.scheduleRemove(LIFETIME_TICKS, TimeUnit.SERVER_TICK);
        return entity;
    }

    private static void onDrop(ItemDropEvent event) {
        var player = event.getPlayer();
        var entity = create(event.getItemStack(), THROWN_PICKUP_DELAY_TICKS);
        entity.setInstance(
                player.getInstance(), player.getPosition().add(0, player.getEyeHeight() - 0.3, 0));
        entity.setVelocity(player.getPosition().direction().mul(6));
    }

    private static void onPickup(PickupItemEvent event) {
        if (!(event.getLivingEntity() instanceof Player player)) {
            return;
        }

        var entity = event.getItemEntity();
        var stack = entity.getItemStack();
        var leftover = player.getInventory().addItemStack(stack, TransactionOption.ALL);
        var taken = leftover.isAir() ? stack.amount() : stack.amount() - leftover.amount();
        if (leftover.isAir()) {
            return;
        }

        // A partial pickup keeps the entity, so the collect animation is sent here instead.
        event.setCancelled(true);
        if (taken > 0) {
            entity.setItemStack(leftover);
            entity.sendPacketToViewers(
                    new CollectItemPacket(entity.getEntityId(), player.getEntityId(), taken));
        }
    }
}
