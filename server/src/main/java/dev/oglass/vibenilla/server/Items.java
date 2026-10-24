package dev.oglass.vibenilla.server;

import java.time.Duration;
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
import net.minestom.server.item.ItemStack;

/** Item entities: dropping from the inventory, spawning drops, and picking them up. */
final class Items {
    private static final Duration PICKUP_DELAY = Duration.ofMillis(500);
    private static final Duration THROWN_PICKUP_DELAY = Duration.ofSeconds(2);

    private Items() {}

    static EventNode<Event> events() {
        return EventNode.all("items")
                .addListener(ItemDropEvent.class, Items::onDrop)
                .addListener(PickupItemEvent.class, Items::onPickup);
    }

    static void spawn(Instance instance, Point position, ItemStack item) {
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

    private static void onDrop(ItemDropEvent event) {
        var player = event.getPlayer();
        var entity = new ItemEntity(event.getItemStack());
        entity.setPickupDelay(THROWN_PICKUP_DELAY);
        entity.setInstance(
                player.getInstance(), player.getPosition().add(0, player.getEyeHeight() - 0.3, 0));
        entity.setVelocity(player.getPosition().direction().mul(6));
    }

    private static void onPickup(PickupItemEvent event) {
        if (event.getLivingEntity() instanceof Player player) {
            event.setCancelled(!player.getInventory().addItemStack(event.getItemStack()));
        }
    }
}
