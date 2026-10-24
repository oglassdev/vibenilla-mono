package dev.oglass.vibenilla.containers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.goldenstack.loot.LootEntry;
import net.goldenstack.loot.LootFunction;
import net.goldenstack.loot.LootNumber;
import net.goldenstack.loot.LootPool;
import net.goldenstack.loot.LootTable;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

@EnvTest
final class ContainersTest {
    private static final BlockVec POSITION = new BlockVec(8, 40, 8);
    private static final BlockVec EAST = new BlockVec(9, 40, 8);

    private static Block chest(Env env, String type) {
        return Block.CHEST
                .withProperties(Map.of("facing", "north", "type", type))
                .withHandler(env.process().block().getHandlerOrDummy("minecraft:chest"));
    }

    private static void interact(Player player, BlockVec position) {
        var instance = player.getInstance();
        var block = instance.getBlock(position);
        block.handler()
                .onInteract(
                        new net.minestom.server.instance.block.BlockHandler.Interaction(
                                block,
                                instance,
                                BlockFace.TOP,
                                position,
                                new Vec(0.5, 1, 0.5),
                                player,
                                PlayerHand.MAIN));
    }

    private static Instance instanceWith(Env env) {
        env.process()
                .eventHandler()
                .addChild(Containers.register(env.process().block(), key -> null));
        return env.createFlatInstance();
    }

    @Test
    void contentsAreStoredInVanillaItemsFormat(Env env) {
        var instance = instanceWith(env);
        instance.setBlock(POSITION, chest(env, "single"));
        var player = env.createPlayer(instance, new Pos(8, 41, 10));

        interact(player, POSITION);
        var inventory = player.getOpenInventory();
        inventory.setItemStack(3, ItemStack.of(Material.DIAMOND, 5));

        var items = instance.getBlock(POSITION).nbt().getList("Items", BinaryTagTypes.COMPOUND);
        var entry = (CompoundBinaryTag) items.getCompound(0);
        assertEquals(1, items.size());
        assertEquals(3, entry.getByte("Slot"));
        assertEquals("minecraft:diamond", entry.getString("id"));
        assertEquals(5, entry.getInt("count"));

        player.closeInventory();
        interact(player, POSITION);
        assertEquals(ItemStack.of(Material.DIAMOND, 5), player.getOpenInventory().getItemStack(3));
    }

    @Test
    void doubleChestHalvesShareOneInventory(Env env) {
        var instance = instanceWith(env);
        instance.setBlock(POSITION, chest(env, "left"));
        instance.setBlock(EAST, chest(env, "right"));
        var first = env.createPlayer(instance, new Pos(8, 41, 10));
        var second = env.createPlayer(instance, new Pos(9, 41, 10));

        interact(first, POSITION);
        interact(second, EAST);

        assertSame(first.getOpenInventory(), second.getOpenInventory());
        assertEquals(54, first.getOpenInventory().getSize());

        first.getOpenInventory().setItemStack(0, ItemStack.of(Material.STONE));
        first.getOpenInventory().setItemStack(30, ItemStack.of(Material.DIRT));

        assertEquals(
                "minecraft:stone",
                instance.getBlock(EAST).nbt().getList("Items").getCompound(0).getString("id"));
        assertEquals(
                "minecraft:dirt",
                instance.getBlock(POSITION).nbt().getList("Items").getCompound(0).getString("id"));
        assertEquals(
                3,
                instance.getBlock(POSITION).nbt().getList("Items").getCompound(0).getByte("Slot"));
    }

    @Test
    void breakingDropsContentsAndClosesViews(Env env) {
        var instance = instanceWith(env);
        instance.setBlock(POSITION, chest(env, "single"));
        var player = env.createPlayer(instance, new Pos(8, 41, 10));

        interact(player, POSITION);
        var inventory = player.getOpenInventory();
        inventory.setItemStack(0, ItemStack.of(Material.GOLD_INGOT, 2));
        instance.setBlock(POSITION, Block.AIR);
        env.tick();

        var drops = instance.getEntities().stream().filter(ItemEntity.class::isInstance).toList();
        assertEquals(1, drops.size());
        assertEquals(
                Material.GOLD_INGOT, ((ItemEntity) drops.getFirst()).getItemStack().material());
        assertFalse(player.getOpenInventory() == inventory);
    }

    @Test
    void lootTableIsGeneratedOnFirstOpen(Env env) {
        var table =
                new LootTable(
                        List.of(
                                new LootPool(
                                        new LootNumber.Constant(1.0),
                                        new LootNumber.Constant(0.0),
                                        List.of(
                                                new LootEntry.Item(
                                                        List.of(),
                                                        List.of(
                                                                new LootFunction.SetCount(
                                                                        List.of(),
                                                                        new LootNumber.Constant(
                                                                                3.0),
                                                                        false)),
                                                        1L,
                                                        0L,
                                                        Material.EMERALD)),
                                        List.of(),
                                        List.of())),
                        List.of(),
                        null);
        Containers.register(env.process().block(), key -> table);
        var instance = env.createFlatInstance();
        var looted =
                chest(env, "single")
                        .withNbt(
                                CompoundBinaryTag.builder()
                                        .putString("LootTable", "test:chest")
                                        .putLong("LootTableSeed", 7L)
                                        .build());
        instance.setBlock(POSITION, looted);
        var player = env.createPlayer(instance, new Pos(8, 41, 10));

        interact(player, POSITION);

        var emeralds =
                List.of(player.getOpenInventory().getItemStacks()).stream()
                        .filter(item -> item.material() == Material.EMERALD)
                        .mapToInt(ItemStack::amount)
                        .sum();
        assertEquals(3, emeralds);
        assertNull(instance.getBlock(POSITION).nbt().get("LootTable"));
        assertTrue(instance.getBlock(POSITION).nbt().get("Items") != null);
    }

    @Test
    void unloadingTheChunkClosesTheView(Env env) {
        var instance = instanceWith(env);
        instance.setBlock(POSITION, chest(env, "single"));
        var player = env.createPlayer(instance, new Pos(8, 41, 10));

        interact(player, POSITION);
        var inventory = player.getOpenInventory();
        inventory.setItemStack(0, ItemStack.of(Material.DIAMOND));
        assertTrue(instance.getBlock(POSITION).nbt().get("Items") != null);

        instance.unloadChunk(instance.getChunkAt(POSITION));

        assertFalse(player.getOpenInventory() == inventory);
        assertTrue(inventory.getViewers().isEmpty());
        inventory.setItemStack(1, ItemStack.of(Material.DIRT));
    }

    @Test
    void replacingTheOpenInventoryReleasesTheContainer(Env env) {
        var instance = instanceWith(env);
        instance.setBlock(
                POSITION,
                Block.BARREL
                        .withProperty("facing", "up")
                        .withHandler(env.process().block().getHandlerOrDummy("minecraft:barrel")));
        var player = env.createPlayer(instance, new Pos(8, 41, 10));

        interact(player, POSITION);
        assertEquals("true", instance.getBlock(POSITION).getProperty("open"));

        player.openInventory(new Inventory(InventoryType.CHEST_1_ROW, "other"));

        assertEquals("false", instance.getBlock(POSITION).getProperty("open"));
    }

    @Test
    void breakingOneHalfKeepsTheOtherHalfsContents(Env env) {
        var instance = instanceWith(env);
        instance.setBlock(POSITION, chest(env, "left"));
        instance.setBlock(EAST, chest(env, "right"));
        var player = env.createPlayer(instance, new Pos(8, 41, 10));

        interact(player, POSITION);
        var inventory = player.getOpenInventory();
        inventory.setItemStack(0, ItemStack.of(Material.STONE));
        inventory.setItemStack(30, ItemStack.of(Material.DIRT));
        instance.setBlock(EAST, Block.AIR);
        env.tick();

        var drops = instance.getEntities().stream().filter(ItemEntity.class::isInstance).toList();
        assertEquals(1, drops.size());
        assertEquals(Material.STONE, ((ItemEntity) drops.getFirst()).getItemStack().material());
        assertFalse(player.getOpenInventory() == inventory);
        assertEquals(
                "minecraft:dirt",
                instance.getBlock(POSITION).nbt().getList("Items").getCompound(0).getString("id"));
    }
}
