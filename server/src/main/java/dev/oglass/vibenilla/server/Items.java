package dev.oglass.vibenilla.server;

import java.util.concurrent.ThreadLocalRandom;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityItemMergeEvent;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.inventory.TransactionOption;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.CollectItemPacket;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

/** Item entities: dropping, merging, expiring and picking them up. */
final class Items {
    private static final int PICKUP_DELAY_TICKS = 10;
    private static final int THROWN_PICKUP_DELAY_TICKS = 40;
    private static final int LIFETIME_TICKS = 6000;
    private static final float MERGE_RANGE = 0.5f;
    private static final Tag<Long> EXPIRY = Tag.Transient("vibenilla:item-expiry");
    private static final Tag<Long> PICKABLE_AT = Tag.Transient("vibenilla:item-pickable-at");

    private Items() {}

    static EventNode<Event> events() {
        return EventNode.all("items")
                .addListener(ItemDropEvent.class, Items::onDrop)
                .addListener(PickupItemEvent.class, Items::onPickup)
                .addListener(EntitySpawnEvent.class, Items::onSpawn)
                .addListener(EntityItemMergeEvent.class, Items::onMerge);
    }

    static void spawn(Instance instance, Point position, ItemStack item) {
        var random = ThreadLocalRandom.current();
        var entity = create(instance, item, PICKUP_DELAY_TICKS);
        entity.setInstance(instance, position);
        entity.setVelocity(
                new Vec(
                        random.nextDouble(-2, 2),
                        random.nextDouble(2, 4),
                        random.nextDouble(-2, 2)));
    }

    /** The pickup delay is counted in world ticks, as Minestom's own delay runs on wall time. */
    private static ItemEntity create(Instance instance, ItemStack item, int pickupDelayTicks) {
        var entity = new ItemEntity(item);
        entity.setTag(PICKABLE_AT, instance.getWorldAge() + pickupDelayTicks);
        return entity;
    }

    /** Every item entity in an instance gets the merge range and lifetime, whoever spawned it. */
    private static void onSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || item.hasTag(EXPIRY)) {
            return;
        }

        item.setMergeRange(MERGE_RANGE);
        item.setTag(EXPIRY, event.getSpawnInstance().getWorldAge() + LIFETIME_TICKS);
        item.scheduler()
                .submitTask(
                        () -> {
                            var instance = item.getInstance();
                            if (item.isRemoved() || instance == null) {
                                return TaskSchedule.stop();
                            }

                            var remaining = item.getTag(EXPIRY) - instance.getWorldAge();
                            if (remaining > 0) {
                                return TaskSchedule.tick((int) remaining);
                            }
                            item.remove();
                            return TaskSchedule.stop();
                        });
    }

    /** The surviving entity keeps the younger of the two ages and the longer pickup delay. */
    private static void onMerge(EntityItemMergeEvent event) {
        var survivor = event.getEntity();
        keepLater(event.getMerged(), survivor, EXPIRY);
        keepLater(event.getMerged(), survivor, PICKABLE_AT);
    }

    private static void keepLater(ItemEntity from, ItemEntity to, Tag<Long> tag) {
        var other = from.getTag(tag);
        var current = to.getTag(tag);
        if (other != null && current != null && other > current) {
            to.setTag(tag, other);
        }
    }

    private static void onDrop(ItemDropEvent event) {
        var player = event.getPlayer();
        var entity = create(player.getInstance(), event.getItemStack(), THROWN_PICKUP_DELAY_TICKS);
        entity.setInstance(
                player.getInstance(), player.getPosition().add(0, player.getEyeHeight() - 0.3, 0));
        entity.setVelocity(player.getPosition().direction().mul(6));
    }

    private static void onPickup(PickupItemEvent event) {
        var entity = event.getItemEntity();
        var pickableAt = entity.getTag(PICKABLE_AT);
        if (pickableAt != null && entity.getInstance().getWorldAge() < pickableAt) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getLivingEntity() instanceof Player player)) {
            return;
        }

        var stack = entity.getItemStack();
        var leftover = player.getInventory().addItemStack(stack, TransactionOption.ALL);
        var taken = leftover.isAir() ? stack.amount() : stack.amount() - leftover.amount();
        if (leftover.isAir()) {
            return;
        }

        // A partial pickup keeps the entity, so the collect animation is sent here instead. The
        // client subtracts the collected amount itself, so the new stack must follow the packet.
        event.setCancelled(true);
        if (taken > 0) {
            entity.sendPacketToViewers(
                    new CollectItemPacket(entity.getEntityId(), player.getEntityId(), taken));
            entity.setItemStack(leftover);
        }
    }
}
