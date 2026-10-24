package dev.oglass.vibenilla.containers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import net.goldenstack.loot.LootTable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;

/**
 * Tracks the inventories that are currently open. All halves of a double chest share one session,
 * keyed by the position of the half that owns the top slots.
 */
final class Sessions {
    private record SessionKey(Instance instance, BlockVec position) {}

    private record Session(
            SessionKey key, List<BlockVec> halves, ContainerKind kind, Inventory inventory) {
        Instance instance() {
            return key.instance();
        }

        int viewersExcluding(Player player) {
            return (int) inventory.getViewers().stream().filter(v -> v != player).count();
        }
    }

    private final Map<SessionKey, Session> sessions = new ConcurrentHashMap<>();
    private final Function<Key, LootTable> lootTables;

    Sessions(Function<Key, LootTable> lootTables) {
        this.lootTables = lootTables;
    }

    Function<Key, LootTable> lootTables() {
        return lootTables;
    }

    void open(Player player, Instance instance, BlockVec position, ContainerKind kind) {
        var block = instance.getBlock(position);
        var halves = ContainerLayout.halves(instance, position, block);

        if (kind.isChest() && halves.stream().anyMatch(half -> isBlocked(instance, half))) {
            return;
        }

        var key = new SessionKey(instance, halves.getFirst());
        var session = sessions.computeIfAbsent(key, k -> create(k, halves, kind, player));

        if (player.getOpenInventory() == session.inventory()) {
            return;
        }

        synchronized (session) {
            if (!player.openInventory(session.inventory())) {
                if (session.inventory().getViewers().isEmpty()) {
                    sessions.remove(key, session);
                }
                return;
            }

            var viewers = session.inventory().getViewers().size();

            if (viewers == 1) {
                play(session, true);
                setBarrelOpen(session, true);
            }
            sendLid(session, viewers);
        }
    }

    /** Releases every inventory the player is viewing, as if it was closed. */
    void release(Player player) {
        for (var session : List.copyOf(sessions.values())) {
            if (session.inventory().getViewers().contains(player)) {
                release(session, player);
            }
        }
    }

    /** Closes the views of every container that includes the position. */
    void closeAt(Instance instance, BlockVec position) {
        for (var session : List.copyOf(sessions.values())) {
            if (session.instance() == instance && session.halves().contains(position)) {
                sessions.remove(session.key(), session);

                for (var viewer : List.copyOf(session.inventory().getViewers())) {
                    viewer.closeInventory();
                }
            }
        }
    }

    private Session create(
            SessionKey key, List<BlockVec> halves, ContainerKind kind, Player player) {
        var instance = key.instance();
        var inventory =
                new Inventory(
                        halves.size() == 2 ? InventoryType.CHEST_6_ROW : InventoryType.CHEST_3_ROW,
                        title(instance, halves, kind));
        var blocks = new ArrayList<Block>();

        for (var half : halves) {
            var original = instance.getBlock(half);
            var block = LootFiller.unpack(lootTables, instance, half, original, player);

            if (!block.equals(original)) {
                instance.setBlock(half, block, false);
            }
            blocks.add(block);
        }

        for (var i = 0; i < blocks.size(); i++) {
            var items = ContainerItems.read(blocks.get(i), ContainerKind.SLOTS_PER_BLOCK);

            for (var slot = 0; slot < items.length; slot++) {
                inventory.setItemStack(i * ContainerKind.SLOTS_PER_BLOCK + slot, items[slot]);
            }
        }

        var session = new Session(key, halves, kind, inventory);
        inventory
                .eventNode()
                .addListener(InventoryItemChangeEvent.class, event -> writeBack(session, event))
                .addListener(
                        InventoryCloseEvent.class, event -> release(session, event.getPlayer()));
        return session;
    }

    private void writeBack(Session session, InventoryItemChangeEvent event) {
        if (sessions.get(session.key()) != session) {
            return;
        }

        var index = event.getSlot() / ContainerKind.SLOTS_PER_BLOCK;
        var half = session.halves().get(index);
        var instance = session.instance();

        if (!instance.isChunkLoaded(half)) {
            return;
        }

        var block = instance.getBlock(half);
        var start = index * ContainerKind.SLOTS_PER_BLOCK;
        var items = new ArrayList<ItemStack>();

        for (var slot = 0; slot < ContainerKind.SLOTS_PER_BLOCK; slot++) {
            items.add(session.inventory().getItemStack(start + slot));
        }

        instance.setBlock(half, ContainerItems.write(block, items), false);
    }

    private void release(Session session, Player player) {
        synchronized (session) {
            if (sessions.get(session.key()) != session) {
                return;
            }

            var remaining = session.viewersExcluding(player);

            if (remaining == 0) {
                sessions.remove(session.key(), session);
                play(session, false);
                setBarrelOpen(session, false);
            }
            sendLid(session, remaining);
        }
    }

    private static boolean isBlocked(Instance instance, BlockVec half) {
        var above = half.add(0, 1, 0);
        return instance.isChunkLoaded(above) && instance.getBlock(above).redstoneConductor();
    }

    private static Component title(Instance instance, List<BlockVec> halves, ContainerKind kind) {
        for (var half : halves) {
            var name = instance.getBlock(half).nbtOrEmpty().get("CustomName");

            if (name != null) {
                var component = Codec.COMPONENT.decode(Transcoder.NBT, name).orElse(null);

                if (component != null) {
                    return component;
                }
            }
        }

        return Component.translatable(kind.titleKey(halves.size() == 2));
    }

    private static void play(Session session, boolean open) {
        var halves = session.halves();
        var x = 0.5 + halves.stream().mapToInt(BlockVec::blockX).average().orElse(0);
        var y = 0.5 + halves.getFirst().blockY();
        var z = 0.5 + halves.stream().mapToInt(BlockVec::blockZ).average().orElse(0);
        var pitch = ThreadLocalRandom.current().nextFloat() * 0.1F + 0.9F;
        var sound = open ? session.kind().openSound(pitch) : session.kind().closeSound(pitch);
        session.instance().playSound(sound, x, y, z);
    }

    private static void sendLid(Session session, int viewers) {
        if (!session.kind().isChest()) {
            return;
        }

        for (var half : session.halves()) {
            session.instance().sendBlockAction(half, (byte) 1, (byte) viewers);
        }
    }

    private static void setBarrelOpen(Session session, boolean open) {
        if (session.kind().isChest()) {
            return;
        }

        var instance = session.instance();
        var position = session.halves().getFirst();

        if (instance.isChunkLoaded(position)) {
            var block = instance.getBlock(position);

            if (block.compare(session.kind().block())) {
                instance.setBlock(position, block.withProperty("open", String.valueOf(open)));
            }
        }
    }
}
