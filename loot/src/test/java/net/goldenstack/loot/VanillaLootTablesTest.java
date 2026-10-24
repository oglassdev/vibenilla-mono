package net.goldenstack.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
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

        var configured = System.getenv("VIBENILLA_VANILLA_DATA");
        var data =
                configured != null
                        ? Path.of(configured)
                        : Path.of("..", "server", "run", "vanilla", "data", "minecraft");
        var lootTables = data.resolve("loot_table");
        assumeTrue(Files.isDirectory(lootTables), "vanilla data is not extracted at " + data);

        tables = Trove.readTables(lootTables);
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

    private static List<ItemStack> generate(String table, Block block, ItemStack tool) {
        var context =
                LootContext.from(
                        VanillaInterface.defaults(),
                        Map.of(
                                LootContext.RANDOM,
                                new Random(1L),
                                LootContext.BLOCK_STATE,
                                block,
                                LootContext.TOOL,
                                tool));
        return tables.get(Key.key("minecraft", table)).generate(context);
    }
}
