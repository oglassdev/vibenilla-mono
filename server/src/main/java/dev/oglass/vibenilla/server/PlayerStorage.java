package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jspecify.annotations.Nullable;

/**
 * Per-player NBT files in {@code playerdata/<uuid>.dat}, using vanilla field names and slots.
 * Snapshots are taken under the player's tick lock; every file access runs on one IO thread so
 * writes and reads of the same file never interleave.
 *
 * <p>A player's session spans {@link #join} until its final write completes. A new session of the
 * same UUID reads only after the previous one has been written, and {@link #close} saves every
 * session still unsaved, including players whose disconnect has not been processed yet.
 */
final class PlayerStorage {
    private static final float FULL_HEALTH = 20f;

    // Minestom inventory slot <-> vanilla Inventory slot
    private static final int[][] SLOTS = slotMap();

    private static final int[] PLAYER_GRID = {
        PlayerInventoryUtils.CRAFT_SLOT_1,
        PlayerInventoryUtils.CRAFT_SLOT_2,
        PlayerInventoryUtils.CRAFT_SLOT_3,
        PlayerInventoryUtils.CRAFT_SLOT_4
    };
    private static final int[] TABLE_GRID = {1, 2, 3, 4, 5, 6, 7, 8, 9};

    private static final int SESSION_WAIT_SECONDS = 5;

    private record Session(Player player, AtomicBoolean claimed, CompletableFuture<Void> written) {
        Session(Player player) {
            this(player, new AtomicBoolean(), new CompletableFuture<>());
        }
    }

    private final Path directory;
    private final ConcurrentHashMap<Player, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService io =
            Executors.newSingleThreadExecutor(
                    task -> Thread.ofPlatform().daemon().name("player-data-io").unstarted(task));

    PlayerStorage(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Starts the player's session. Returns false after kicking the player when another session of
     * the same UUID is still online. Waits for a previous disconnected session of the same UUID to
     * finish writing.
     */
    boolean join(Player player) {
        var uuid = player.getUuid();
        List<Session> previous;
        synchronized (sessions) {
            previous =
                    sessions.values().stream()
                            .filter(s -> s.player().getUuid().equals(uuid))
                            .toList();
            if (previous.stream().anyMatch(s -> s.player().isOnline())) {
                player.kick(Component.translatable("multiplayer.disconnect.duplicate_login"));
                return false;
            }
            sessions.put(player, new Session(player));
        }
        previous.forEach(s -> await(s, SESSION_WAIT_SECONDS));
        return true;
    }

    /** The joined player's saved data, or null when they have never joined or went offline. */
    @Nullable CompoundBinaryTag load(Player player) {
        if (!player.isOnline()) {
            finish(player);
            return null;
        }
        return read(player.getUuid());
    }

    private @Nullable CompoundBinaryTag read(UUID uuid) {
        try {
            return io.submit(() -> readFile(uuid)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | RejectedExecutionException e) {
            MinecraftServer.LOGGER.error("Failed to read player data for {}", uuid, e);
            return null;
        }
    }

    private @Nullable CompoundBinaryTag readFile(UUID uuid) {
        var file = file(uuid);
        if (!Files.exists(file)) {
            return null;
        }
        try (var in = Files.newInputStream(file)) {
            return BinaryTagIO.reader().readNamed(in, BinaryTagIO.Compression.GZIP).getValue();
        } catch (IOException e) {
            MinecraftServer.LOGGER.error("Failed to read player data {}", file, e);
            return null;
        }
    }

    /** True when the saved data is of a player who logged out on the death screen. */
    static boolean dead(CompoundBinaryTag data) {
        return data.getFloat("Health", FULL_HEALTH) <= 0;
    }

    /** The dimension the player was in, defaulting to the overworld. */
    static Key dimension(CompoundBinaryTag data) {
        var name = data.getString("Dimension", "minecraft:overworld");
        return Key.parseable(name) ? Key.key(name) : WorldStorage.OVERWORLD;
    }

    /** Position the player should spawn at, or null if the data has none. */
    static @Nullable Pos position(CompoundBinaryTag data) {
        var pos = data.getList("Pos", BinaryTagTypes.DOUBLE);
        var rotation = data.getList("Rotation", BinaryTagTypes.FLOAT);
        if (pos.size() != 3 || rotation.size() != 2) {
            return null;
        }
        return new Pos(
                pos.getDouble(0),
                pos.getDouble(1),
                pos.getDouble(2),
                rotation.getFloat(0),
                rotation.getFloat(1));
    }

    /** Respawn point stored in the data, or null if none was saved. */
    static @Nullable Pos respawnPoint(CompoundBinaryTag data) {
        var spawn = data.getList("SpawnPos", BinaryTagTypes.DOUBLE);
        if (spawn.size() != 3) {
            return null;
        }
        return new Pos(
                spawn.getDouble(0),
                spawn.getDouble(1),
                spawn.getDouble(2),
                data.getFloat("SpawnAngle"),
                0);
    }

    /**
     * Applies everything except dimension, position and respawn point, which are used to spawn. A
     * dead player comes back alive with full health and food.
     */
    static void restore(Player player, CompoundBinaryTag data) {
        var inventory = player.getInventory();
        inventory.clear();
        for (var tag : data.getList("Inventory", BinaryTagTypes.COMPOUND)) {
            var item = (CompoundBinaryTag) tag;
            var slot = minestomSlot(item.getByte("Slot"));
            if (slot >= 0) {
                inventory.setItemStack(slot, readItem(item));
            }
        }
        for (var tag : data.getList("Overflow", BinaryTagTypes.COMPOUND)) {
            var stack = readItem((CompoundBinaryTag) tag);
            if (!inventory.addItemStack(stack)) {
                player.dropItem(stack);
            }
        }
        var dead = dead(data);
        player.setHeldItemSlot((byte) data.getInt("SelectedItemSlot"));
        player.setGameMode(GameMode.values()[data.getInt("playerGameType")]);
        player.setHealth(dead ? FULL_HEALTH : data.getFloat("Health", FULL_HEALTH));
        player.setFood(dead ? 20 : data.getInt("foodLevel", 20));
        player.setFoodSaturation(dead ? 5f : data.getFloat("foodSaturationLevel", 5f));
        player.setLevel(data.getInt("XpLevel"));
        player.setExp(data.getFloat("XpP"));
    }

    private static ItemStack readItem(CompoundBinaryTag tag) {
        return ItemStack.fromItemNBT(tag, MinecraftServer.process());
    }

    /** Queues a write for a player who is in a world. Failures are logged, never thrown. */
    void save(Player player) {
        try {
            snapshotAndQueue(player);
        } catch (RuntimeException e) {
            MinecraftServer.LOGGER.error("Failed to save {}", player.getUsername(), e);
        }
    }

    /** Queues the final write of the player's session; a session is saved at most once. */
    void finish(Player player) {
        var session = sessions.get(player);
        if (session == null || !session.claimed().compareAndSet(false, true)) {
            return;
        }
        var queued = CompletableFuture.<Void>completedFuture(null);
        try {
            var write = snapshotAndQueue(player);
            if (write != null) {
                queued = write;
            }
        } catch (RuntimeException e) {
            MinecraftServer.LOGGER.error("Failed to save {}", player.getUsername(), e);
        }
        queued.whenComplete(
                (result, error) -> {
                    sessions.remove(player, session);
                    session.written().complete(null);
                });
    }

    /**
     * Players not yet assigned to a tick thread cannot be acquired from another thread, and players
     * without an instance keep their old data. The write is queued under the lock so writes of one
     * player stay in snapshot order.
     */
    private @Nullable CompletableFuture<Void> snapshotAndQueue(Player player) {
        if (player.acquirable().assignedThread() == null) {
            return null;
        }
        return player.acquirable()
                .applySync(
                        locked -> {
                            if (locked.getInstance() == null) {
                                return null;
                            }
                            var uuid = locked.getUuid();
                            var tag = serialize(locked);
                            return CompletableFuture.runAsync(() -> write(uuid, tag), io);
                        });
    }

    private static void await(Session session, int seconds) {
        try {
            session.written().get(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            MinecraftServer.LOGGER.error("Timed out saving {}", session.player().getUsername(), e);
        }
    }

    /** Saves every unsaved session, waits for all writes and stops accepting new ones. */
    void close() {
        var open = sessions.values().stream().toList();
        open.forEach(s -> finish(s.player()));
        open.forEach(s -> await(s, 30));
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                MinecraftServer.LOGGER.error("Timed out writing player data");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(UUID uuid, CompoundBinaryTag tag) {
        var file = file(uuid);
        var temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) {
                BinaryTagIO.writer()
                        .writeNamed(Map.entry("", tag), out, BinaryTagIO.Compression.GZIP);
            }
            AtomicFiles.replace(temp, file);
        } catch (IOException e) {
            MinecraftServer.LOGGER.error("Failed to save player data {}", file, e);
        }
    }

    /**
     * Reads the player's state without changing it. The cursor item and crafting grids are merged
     * into the saved inventory where they fit; the rest is stored as {@code Overflow}. The crafting
     * result is derived and never saved.
     */
    static CompoundBinaryTag serialize(Player player) {
        var position = player.getPosition();
        var respawn = player.getRespawnPoint();
        var items = player.getInventory();

        var main = new ItemStack[PlayerInventory.INNER_INVENTORY_SIZE];
        for (var i = 0; i < main.length; i++) {
            main[i] = items.getItemStack(i);
        }
        var overflow = new ArrayList<ItemStack>();
        stash(main, overflow, items.getCursorItem());
        for (var slot : PLAYER_GRID) {
            stash(main, overflow, items.getItemStack(slot));
        }
        if (player.getOpenInventory() instanceof Inventory open
                && open.getInventoryType() == InventoryType.CRAFTING) {
            for (var slot : TABLE_GRID) {
                stash(main, overflow, open.getItemStack(slot));
            }
        }

        var inventory = ListBinaryTag.builder(BinaryTagTypes.COMPOUND);
        for (var slot : SLOTS) {
            var stack = slot[0] < main.length ? main[slot[0]] : items.getItemStack(slot[0]);
            if (!stack.isAir()) {
                inventory.add(item(stack, slot[1]));
            }
        }
        var overflowList = ListBinaryTag.builder(BinaryTagTypes.COMPOUND);
        for (var stack : overflow) {
            overflowList.add(item(stack, 0));
        }

        return CompoundBinaryTag.builder()
                .putString("Dimension", player.getInstance().getDimensionName())
                .put("Pos", doubles(position.x(), position.y(), position.z()))
                .put("Rotation", floats(position.yaw(), position.pitch()))
                .put("SpawnPos", doubles(respawn.x(), respawn.y(), respawn.z()))
                .putFloat("SpawnAngle", respawn.yaw())
                .putFloat("Health", player.getHealth())
                .putInt("foodLevel", player.getFood())
                .putFloat("foodSaturationLevel", player.getFoodSaturation())
                .putInt("XpLevel", player.getLevel())
                .putFloat("XpP", player.getExp())
                .putInt("playerGameType", player.getGameMode().ordinal())
                .putInt("SelectedItemSlot", player.getHeldSlot())
                .put("Inventory", inventory.build())
                .put("Overflow", overflowList.build())
                .build();
    }

    private static CompoundBinaryTag item(ItemStack stack, int slot) {
        return CompoundBinaryTag.builder()
                .put(stack.toItemNBT(MinecraftServer.process()))
                .putByte("Slot", (byte) slot)
                .build();
    }

    /** Merges a stack into similar stacks or empty slots of {@code main}, else into overflow. */
    private static void stash(ItemStack[] main, ArrayList<ItemStack> overflow, ItemStack stack) {
        var remaining = stack;
        for (var i = 0; i < main.length && !remaining.isAir(); i++) {
            var existing = main[i];
            if (!existing.isAir()
                    && existing.isSimilar(remaining)
                    && existing.amount() < existing.maxStackSize()) {
                var moved =
                        Math.min(existing.maxStackSize() - existing.amount(), remaining.amount());
                main[i] = existing.withAmount(existing.amount() + moved);
                remaining = remaining.withAmount(remaining.amount() - moved);
            }
        }
        for (var i = 0; i < main.length && !remaining.isAir(); i++) {
            if (main[i].isAir()) {
                main[i] = remaining;
                remaining = ItemStack.AIR;
            }
        }
        if (!remaining.isAir()) {
            overflow.add(remaining);
        }
    }

    private Path file(UUID uuid) {
        return directory.resolve(uuid + ".dat");
    }

    private static ListBinaryTag doubles(double... values) {
        var list = ListBinaryTag.builder(BinaryTagTypes.DOUBLE);
        for (var value : values) {
            list.add(DoubleBinaryTag.doubleBinaryTag(value));
        }
        return list.build();
    }

    private static ListBinaryTag floats(float... values) {
        var list = ListBinaryTag.builder(BinaryTagTypes.FLOAT);
        for (var value : values) {
            list.add(FloatBinaryTag.floatBinaryTag(value));
        }
        return list.build();
    }

    private static int minestomSlot(int vanillaSlot) {
        for (var slot : SLOTS) {
            if (slot[1] == vanillaSlot) {
                return slot[0];
            }
        }
        return -1;
    }

    private static int[][] slotMap() {
        var slots = new int[PlayerInventory.INNER_INVENTORY_SIZE + 5][];
        for (var i = 0; i < PlayerInventory.INNER_INVENTORY_SIZE; i++) {
            slots[i] = new int[] {i, i};
        }
        var n = PlayerInventory.INNER_INVENTORY_SIZE;
        slots[n++] = new int[] {PlayerInventoryUtils.BOOTS_SLOT, 100};
        slots[n++] = new int[] {PlayerInventoryUtils.LEGGINGS_SLOT, 101};
        slots[n++] = new int[] {PlayerInventoryUtils.CHESTPLATE_SLOT, 102};
        slots[n++] = new int[] {PlayerInventoryUtils.HELMET_SLOT, 103};
        slots[n] = new int[] {PlayerInventoryUtils.OFFHAND_SLOT, -106};
        return slots;
    }
}
