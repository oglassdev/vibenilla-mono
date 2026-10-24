package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
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
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jspecify.annotations.Nullable;

/** Per-player NBT files in {@code playerdata/<uuid>.dat}, using vanilla field names and slots. */
final class PlayerStorage {
    private static final float FULL_HEALTH = 20f;

    // Minestom inventory slot <-> vanilla Inventory slot
    private static final int[][] SLOTS = slotMap();

    private final Path directory;

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

    /** Applies everything except position and respawn point, which are used to spawn. */
    static void restore(Player player, CompoundBinaryTag data) {
        var inventory = player.getInventory();
        inventory.clear();
        for (var tag : data.getList("Inventory", BinaryTagTypes.COMPOUND)) {
            var item = (CompoundBinaryTag) tag;
            var slot = minestomSlot(item.getByte("Slot"));
            if (slot >= 0) {
                inventory.setItemStack(
                        slot, ItemStack.fromItemNBT(item, MinecraftServer.process()));
            }
        }
        player.setHeldItemSlot((byte) data.getInt("SelectedItemSlot"));
        player.setGameMode(GameMode.values()[data.getInt("playerGameType")]);
        player.setHealth(data.getFloat("Health", FULL_HEALTH));
        player.setFood(data.getInt("foodLevel", 20));
        player.setFoodSaturation(data.getFloat("foodSaturationLevel", 5f));
        player.setLevel(data.getInt("XpLevel"));
        player.setExp(data.getFloat("XpP"));
    }

    /** Saves a player who is in a world; players still configuring keep their old data. */
    void save(Player player) {
        if (player.getInstance() == null) {
            return;
        }
        var tag = serialize(player);
        var file = file(player.getUuid());
        var temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) {
                BinaryTagIO.writer()
                        .writeNamed(Map.entry("", tag), out, BinaryTagIO.Compression.GZIP);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            MinecraftServer.LOGGER.error("Failed to save player data {}", file, e);
        }
    }

    static CompoundBinaryTag serialize(Player player) {
        // A dead player is saved as if already respawned.
        var dead = player.isDead();
        var respawn = player.getRespawnPoint();
        var position = dead ? respawn : player.getPosition();

        var inventory = ListBinaryTag.builder(BinaryTagTypes.COMPOUND);
        var items = player.getInventory();
        for (var slot : SLOTS) {
            var stack = items.getItemStack(slot[0]);
            if (!stack.isAir()) {
                inventory.add(
                        CompoundBinaryTag.builder()
                                .put(stack.toItemNBT(MinecraftServer.process()))
                                .putByte("Slot", (byte) slot[1])
                                .build());
            }
        }

        return CompoundBinaryTag.builder()
                .put("Pos", doubles(position.x(), position.y(), position.z()))
                .put("Rotation", floats(position.yaw(), position.pitch()))
                .put("SpawnPos", doubles(respawn.x(), respawn.y(), respawn.z()))
                .putFloat("SpawnAngle", respawn.yaw())
                .putFloat("Health", dead ? FULL_HEALTH : player.getHealth())
                .putInt("foodLevel", dead ? 20 : player.getFood())
                .putFloat("foodSaturationLevel", dead ? 5f : player.getFoodSaturation())
                .putInt("XpLevel", dead ? 0 : player.getLevel())
                .putFloat("XpP", dead ? 0f : player.getExp())
                .putInt("playerGameType", player.getGameMode().ordinal())
                .putInt("SelectedItemSlot", player.getHeldSlot())
                .put("Inventory", inventory.build())
                .build();
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
