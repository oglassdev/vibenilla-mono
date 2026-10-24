package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
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

    private final Path directory;
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

    /** The saved data for a player, or null when they have never joined. */
    @Nullable CompoundBinaryTag read(UUID uuid) {
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
     * Applies everything except position and respawn point, which are used to spawn. A dead player
     * comes back alive with full health and food.
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

    /**
     * Snapshots a player who is in a world and queues the write; players still configuring keep
     * their old data.
     */
    void save(Player player) {
        player.acquirable()
                .sync(
                        locked -> {
                            if (locked.getInstance() == null) {
                                return;
                            }
                            var uuid = locked.getUuid();
                            var tag = serialize(locked);
                            try {
                                io.execute(() -> write(uuid, tag));
                            } catch (RejectedExecutionException e) {
                                // Closed during shutdown, after every player was already saved.
                            }
                        });
    }

    /** Waits for queued writes and stops accepting new ones. */
    void close() {
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
