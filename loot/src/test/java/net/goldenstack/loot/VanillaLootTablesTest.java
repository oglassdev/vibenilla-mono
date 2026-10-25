package net.goldenstack.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs against the extracted vanilla datapack, which is located through the {@code
 * VIBENILLA_VANILLA_DATA} environment variable or {@code server/run/vanilla/data/minecraft}.
 */
class VanillaLootTablesTest {

    private static Map<Key, LootTable> tables;

    @BeforeAll
    static void readVanillaTables() {
        MinecraftServer.init();

        tables = Trove.readTables(Path.of("data", "minecraft", "loot_table"));
    }

    @Test
    void parsesEveryVanillaTable() {
        assertEquals(true, tables.size() > 1000);
    }

    @Test
    void stoneDropsCobblestoneWithoutSilkTouch() {
        var items = generate("blocks/stone", Block.STONE, ItemStack.of(Material.IRON_PICKAXE));

        assertEquals(List.of(ItemStack.of(Material.COBBLESTONE)), items);
    }

    @Test
    void stoneDropsItselfWithSilkTouch() {
        var pickaxe =
                ItemStack.of(Material.IRON_PICKAXE)
                        .with(
                                DataComponents.ENCHANTMENTS,
                                new EnchantmentList(Map.of(Enchantment.SILK_TOUCH, 1)));

        var items = generate("blocks/stone", Block.STONE, pickaxe);

        assertEquals(List.of(ItemStack.of(Material.STONE)), items);
    }

    @Test
    void wheatOnlyDropsWheatWhenFullyGrown() {
        var tool = ItemStack.of(Material.IRON_PICKAXE);

        var grown = generate("blocks/wheat", Block.WHEAT.withProperty("age", "7"), tool);
        var young = generate("blocks/wheat", Block.WHEAT.withProperty("age", "3"), tool);

        assertEquals(true, grown.stream().anyMatch(item -> item.material() == Material.WHEAT));
        assertEquals(List.of(ItemStack.of(Material.WHEAT_SEEDS)), young);
    }

    @Test
    void creeperDropsOnlyGunpowderWithoutASkeletonKiller() {
        for (var seed = 0L; seed < 20L; seed++) {
            var items = generate("entities/creeper", Block.AIR, ItemStack.AIR, new Random(seed));

            assertEquals(
                    true, items.stream().allMatch(item -> item.material() == Material.GUNPOWDER));
        }
    }

    private static List<ItemStack> generate(String table, Block block, ItemStack tool) {
        return generate(table, block, tool, new Random(1L));
    }

    private static List<ItemStack> generate(
            String table, Block block, ItemStack tool, Random random) {
        var context =
                LootContext.from(
                        VanillaInterface.defaults(),
                        Map.of(
                                LootContext.RANDOM,
                                random,
                                LootContext.WORLD,
                                MinecraftServer.getInstanceManager().createInstanceContainer(),
                                LootContext.BLOCK_STATE,
                                block,
                                LootContext.TOOL,
                                tool));
        return tables.get(Key.key("minecraft", table)).generate(context);
    }
}
