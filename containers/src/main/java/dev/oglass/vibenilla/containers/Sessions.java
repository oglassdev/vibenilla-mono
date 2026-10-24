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
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.animal.tameable.CatMeta;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the inventories that are currently open. All halves of a double chest share one session,
 * keyed by the position of the half that owns the top slots.
 *
 * <p>Sessions hold no locks, so they can't deadlock with instance mutations. Like the rest of the
 * server's block and inventory handling they assume one dispatcher thread, which is Minestom's
 * default ({@code minestom.dispatcher-threads}).
 */
final class Sessions {
    private record SessionKey(Instance instance, BlockVec position) {}

    private final class Session {
        final SessionKey key;
        final List<BlockVec> halves;
        final ContainerKind kind;
        final Inventory inventory;
        boolean open;

        Session(SessionKey key, List<BlockVec> halves, ContainerKind kind, Component title) {
            this.key = key;
            this.halves = halves;
            this.kind = kind;
            this.inventory =
                    new Inventory(
                            halves.size() == 2
                                    ? InventoryType.CHEST_6_ROW
                                    : InventoryType.CHEST_3_ROW,
                            title) {
                        @Override
                        public boolean removeViewer(Player player) {
                            var removed = super.removeViewer(player);

                            if (removed) {
                                release(Session.this, player);
                            }
                            return removed;
                        }
                    };
        }

        Instance instance() {
            return key.instance();
        }

        /** Counts the viewers that animate the container, which excludes spectators. */
        int openers(@Nullable Player excluded) {
            return (int)
                    inventory.getViewers().stream()
                            .filter(v -> v != excluded && v.getGameMode() != GameMode.SPECTATOR)
                            .count();
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
        var spectator = player.getGameMode() == GameMode.SPECTATOR;

        if (kind.isChest() && halves.stream().anyMatch(half -> isBlocked(instance, half))) {
            return;
        }

        var key = new SessionKey(instance, halves.getFirst());
        var session = sessions.get(key);

        if (session == null) {
            if (spectator
                    && halves.stream()
                            .anyMatch(half -> LootFiller.hasLootTable(instance.getBlock(half)))) {
                return;
            }
            session = sessions.computeIfAbsent(key, k -> create(k, halves, kind, player));
        }

        if (player.getOpenInventory() == session.inventory) {
            return;
        }

        if (!player.openInventory(session.inventory)) {
            if (session.inventory.getViewers().isEmpty()) {
                sessions.remove(key, session);
            }
            return;
        }

        if (!spectator) {
            if (!session.open) {
                session.open = true;
                play(session, true);
                setBarrelOpen(session, true);
            }
            sendLid(session, session.openers(null));
        }
    }

    /** Releases every inventory the player is viewing, as if it was closed. */
    void release(Player player) {
        for (var session : List.copyOf(sessions.values())) {
            if (session.inventory.getViewers().contains(player)) {
                release(session, player);
            }
        }
    }

    /** Closes the views of every container that includes the position. */
    void closeAt(Instance instance, BlockVec position) {
        for (var session : List.copyOf(sessions.values())) {
            if (session.instance() == instance && session.halves.contains(position)) {
                close(session, false);
            }
        }
    }

    void closeChunk(Instance instance, int chunkX, int chunkZ) {
        for (var session : List.copyOf(sessions.values())) {
            if (session.instance() == instance
                    && session.halves.stream()
                            .anyMatch(half -> half.chunkX() == chunkX && half.chunkZ() == chunkZ)) {
                close(session, true);
            }
        }
    }

    void closeInstance(Instance instance) {
        for (var session : List.copyOf(sessions.values())) {
            if (session.instance() == instance) {
                close(session, true);
            }
        }
    }

    /**
     * Invalidates the session before closing its views, so nothing is written back afterwards. The
     * barrel is only reset when its block is not about to change.
     */
    private void close(Session session, boolean resetBarrel) {
        if (!sessions.remove(session.key, session)) {
            return;
        }

        var wasOpen = session.open;
        session.open = false;

        for (var viewer : List.copyOf(session.inventory.getViewers())) {
            viewer.closeInventory();
        }

        if (wasOpen) {
            play(session, false);
            sendLid(session, 0);

            if (resetBarrel) {
                setBarrelOpen(session, false);
            }
        }
    }

    private Session create(
            SessionKey key, List<BlockVec> halves, ContainerKind kind, Player player) {
        var instance = key.instance();
        var session = new Session(key, halves, kind, title(instance, halves, kind));
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
                session.inventory.setItemStack(
                        i * ContainerKind.SLOTS_PER_BLOCK + slot, items[slot]);
            }
        }

        session.inventory
                .eventNode()
                .addListener(InventoryItemChangeEvent.class, event -> writeBack(session, event))
                .addListener(
                        InventoryPreClickEvent.class,
                        event -> {
                            if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                                event.setCancelled(true);
                            }
                        });
        return session;
    }

    private void writeBack(Session session, InventoryItemChangeEvent event) {
        if (sessions.get(session.key) != session) {
            return;
        }

        var index = event.getSlot() / ContainerKind.SLOTS_PER_BLOCK;
        var half = session.halves.get(index);
        var instance = session.instance();

        if (!instance.isChunkLoaded(half)) {
            return;
        }

        var block = instance.getBlock(half);

        if (!block.compare(session.kind.block())) {
            return;
        }

        var start = index * ContainerKind.SLOTS_PER_BLOCK;
        var items = new ArrayList<ItemStack>();

        for (var slot = 0; slot < ContainerKind.SLOTS_PER_BLOCK; slot++) {
            items.add(session.inventory.getItemStack(start + slot));
        }

        instance.setBlock(half, ContainerItems.write(block, items), false);
    }

    private void release(Session session, Player player) {
        if (sessions.get(session.key) != session) {
            return;
        }

        var viewers = session.inventory.getViewers().stream().filter(v -> v != player).count();
        var openers = session.openers(player);

        if (viewers == 0) {
            sessions.remove(session.key, session);
        }

        if (!session.open) {
            return;
        }

        if (openers == 0) {
            session.open = false;
            play(session, false);
            setBarrelOpen(session, false);
        }
        sendLid(session, openers);
    }

    private static boolean isBlocked(Instance instance, BlockVec half) {
        var above = half.add(0, 1, 0);
        return instance.isChunkLoaded(above)
                && (instance.getBlock(above).redstoneConductor() || hasSittingCat(instance, above));
    }

    private static boolean hasSittingCat(Instance instance, BlockVec cell) {
        for (var entity : instance.getNearbyEntities(cell.add(0.5, 0.5, 0.5), 2)) {
            if (entity.getEntityMeta() instanceof CatMeta cat && cat.isSitting()) {
                var box = entity.getBoundingBox();
                var position = entity.getPosition();

                if (position.x() + box.minX() < cell.x() + 1
                        && position.x() + box.maxX() > cell.x()
                        && position.y() + box.minY() < cell.y() + 1
                        && position.y() + box.maxY() > cell.y()
                        && position.z() + box.minZ() < cell.z() + 1
                        && position.z() + box.maxZ() > cell.z()) {
                    return true;
                }
            }
        }

        return false;
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
        var halves = session.halves;
        var x = 0.5 + halves.stream().mapToInt(BlockVec::blockX).average().orElse(0);
        var y = 0.5 + halves.getFirst().blockY();
        var z = 0.5 + halves.stream().mapToInt(BlockVec::blockZ).average().orElse(0);
        var pitch = ThreadLocalRandom.current().nextFloat() * 0.1F + 0.9F;
        var sound = open ? session.kind.openSound(pitch) : session.kind.closeSound(pitch);
        session.instance().playSound(sound, x, y, z);
    }

    private static void sendLid(Session session, int viewers) {
        if (!session.kind.isChest()) {
            return;
        }

        var instance = session.instance();

        for (var half : session.halves) {
            if (instance.isChunkLoaded(half)) {
                instance.sendBlockAction(half, (byte) 1, (byte) viewers);
            }
        }
    }

    private static void setBarrelOpen(Session session, boolean open) {
        if (session.kind.isChest()) {
            return;
        }

        var instance = session.instance();
        var position = session.halves.getFirst();

        if (instance.isChunkLoaded(position)) {
            var block = instance.getBlock(position);

            if (block.compare(session.kind.block())) {
                instance.setBlock(position, block.withProperty("open", String.valueOf(open)));
            }
        }
    }
}
