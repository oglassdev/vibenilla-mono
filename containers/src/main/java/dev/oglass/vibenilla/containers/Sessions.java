package dev.oglass.vibenilla.containers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import net.goldenstack.loot.LootTable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.animal.tameable.CatMeta;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
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
        final boolean[] dirty = new boolean[2];
        boolean flushScheduled;

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

        int viewersExcluding(@Nullable Player excluded) {
            return (int) inventory.getViewers().stream().filter(v -> v != excluded).count();
        }

        List<ItemStack> items(int index) {
            var start = index * ContainerKind.SLOTS_PER_BLOCK;
            var items = new ArrayList<ItemStack>();

            for (var slot = 0; slot < ContainerKind.SLOTS_PER_BLOCK; slot++) {
                items.add(inventory.getItemStack(start + slot));
            }
            return items;
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

        if (halves.isEmpty()
                || (kind.isChest()
                        && halves.stream().anyMatch(half -> isBlocked(instance, half)))) {
            return;
        }

        var session = find(instance, halves);

        if (session == null) {
            var key = new SessionKey(instance, halves.getFirst());
            session = create(key, halves, kind, player);
            sessions.put(key, session);
        }

        if (player.getOpenInventory() == session.inventory) {
            return;
        }

        if (!player.openInventory(session.inventory)) {
            if (session.inventory.getViewers().isEmpty()) {
                sessions.remove(session.key, session);
            }
            return;
        }

        if (!session.open) {
            session.open = true;
            play(session, true);
            setBarrelOpen(session, true);
        }
        sendLid(session, session.viewersExcluding(null));
    }

    /** Closes the view the player has of a container, if any. */
    void closeView(Player player) {
        for (var session : List.copyOf(sessions.values())) {
            if (session.inventory.getViewers().contains(player)) {
                player.closeInventory();
                return;
            }
        }
    }

    /**
     * Returns the block with the live contents of any open session that includes the position, so
     * changes that have not been written back yet are not lost.
     */
    Block current(Instance instance, BlockVec position, Block block) {
        var session = find(instance, List.of(position));

        if (session == null) {
            return block;
        }
        return ContainerItems.write(block, session.items(session.halves.indexOf(position)));
    }

    /** Resets a barrel that was saved open when no one is viewing it anymore. */
    void reconcileBarrel(Instance instance, BlockVec position) {
        MinecraftServer.getSchedulerManager()
                .scheduleEndOfTick(
                        () -> {
                            if (!instance.isChunkLoaded(position)
                                    || find(instance, List.of(position)) != null) {
                                return;
                            }

                            var block = instance.getBlock(position);

                            if (block.compare(Block.BARREL)
                                    && "true".equals(block.getProperty("open"))) {
                                instance.setBlock(position, block.withProperty("open", "false"));
                            }
                        });
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
                deferFlush(session);
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

        flush(session);
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
                .addListener(InventoryItemChangeEvent.class, event -> markDirty(session, event));
        return session;
    }

    private @Nullable Session find(Instance instance, List<BlockVec> halves) {
        for (var session : sessions.values()) {
            if (session.instance() == instance
                    && halves.stream().anyMatch(session.halves::contains)) {
                return session;
            }
        }
        return null;
    }

    private void markDirty(Session session, InventoryItemChangeEvent event) {
        if (sessions.get(session.key) != session) {
            return;
        }

        session.dirty[event.getSlot() / ContainerKind.SLOTS_PER_BLOCK] = true;

        if (!session.flushScheduled) {
            session.flushScheduled = true;
            MinecraftServer.getSchedulerManager()
                    .scheduleEndOfTick(
                            () -> {
                                session.flushScheduled = false;
                                flush(session);
                            });
        }
    }

    /** Writes the changed halves into their blocks. */
    private void flush(Session session) {
        for (var index = 0; index < session.halves.size(); index++) {
            if (session.dirty[index]) {
                session.dirty[index] = false;
                write(session, index, session.items(index));
            }
        }
    }

    /**
     * Takes the changed halves now and writes them at the end of the tick, for when the block of a
     * half is being replaced and can't be modified right away.
     */
    private void deferFlush(Session session) {
        var pending = new HashMap<Integer, List<ItemStack>>();

        for (var index = 0; index < session.halves.size(); index++) {
            if (session.dirty[index]) {
                session.dirty[index] = false;
                pending.put(index, session.items(index));
            }
        }

        if (!pending.isEmpty()) {
            MinecraftServer.getSchedulerManager()
                    .scheduleEndOfTick(
                            () -> pending.forEach((index, items) -> write(session, index, items)));
        }
    }

    private void write(Session session, int index, List<ItemStack> items) {
        var half = session.halves.get(index);
        var instance = session.instance();

        if (!instance.isChunkLoaded(half)) {
            return;
        }

        var block = instance.getBlock(half);

        if (block.compare(session.kind.block())) {
            instance.setBlock(half, ContainerItems.write(block, items), false);
        }
    }

    private void release(Session session, Player player) {
        if (sessions.get(session.key) != session) {
            return;
        }

        var viewers = session.viewersExcluding(player);

        if (viewers == 0) {
            flush(session);
            sessions.remove(session.key, session);
        }

        if (!session.open) {
            return;
        }

        if (viewers == 0) {
            session.open = false;
            play(session, false);
            setBarrelOpen(session, false);
        }
        sendLid(session, viewers);
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
