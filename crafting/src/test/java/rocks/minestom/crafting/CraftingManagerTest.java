package rocks.minestom.crafting;

import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.PotDecorations;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

final class CraftingManagerTest {
    private static final Path DATA_PATH = Path.of("data", "minecraft");
    private static final CraftingManager CRAFTING_MANAGER = new CraftingManager(DATA_PATH);

    @ParameterizedTest(name = "{1}")
    @MethodSource("craftingData")
    void craftingProducesExpectedItems(CraftingGrid craftingGrid, Material expectedMaterial, int expectedAmount) {
        var recipeMatch = CRAFTING_MANAGER.match(craftingGrid);
        assertNotNull(recipeMatch);
        assertEquals(expectedMaterial, recipeMatch.result().material());
        assertEquals(expectedAmount, recipeMatch.result().amount());
    }

    @Test
    void decoratedPotRecipeTracksOrder() {
        var craftingGrid = createGrid(3, 3,
                ItemStack.AIR, ItemStack.of(Material.ANGLER_POTTERY_SHERD), ItemStack.AIR,
                ItemStack.of(Material.BLADE_POTTERY_SHERD), ItemStack.AIR, ItemStack.of(Material.ARMS_UP_POTTERY_SHERD),
                ItemStack.AIR, ItemStack.of(Material.MOURNER_POTTERY_SHERD), ItemStack.AIR);

        var recipeMatch = CRAFTING_MANAGER.match(craftingGrid);
        assertNotNull(recipeMatch);
        assertEquals(Material.DECORATED_POT, recipeMatch.result().material());

        PotDecorations decorations = recipeMatch.result().get(DataComponents.POT_DECORATIONS);
        assertNotNull(decorations);
        assertEquals(Material.ANGLER_POTTERY_SHERD, decorations.back());
        assertEquals(Material.BLADE_POTTERY_SHERD, decorations.left());
        assertEquals(Material.ARMS_UP_POTTERY_SHERD, decorations.right());
        assertEquals(Material.MOURNER_POTTERY_SHERD, decorations.front());
    }

    private static Stream<Arguments> craftingData() {
        return Stream.of(
                arguments(createGrid(2, 2,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.AIR,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.AIR),
                        Named.of("minecraft:stick", Material.STICK), 4),

                arguments(createGrid(2, 2,
                        ItemStack.AIR, ItemStack.AIR,
                        ItemStack.AIR, ItemStack.of(Material.OAK_PLANKS)),
                        Named.of("minecraft:oak_button", Material.OAK_BUTTON), 1),

                arguments(createGrid(2, 2,
                        ItemStack.of(Material.WHITE_SHULKER_BOX), ItemStack.AIR,
                        ItemStack.of(Material.RED_DYE), ItemStack.AIR),
                        Named.of("minecraft:red_shulker_box", Material.RED_SHULKER_BOX), 1),

                arguments(createGrid(2, 2,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS)),
                        Named.of("minecraft:crafting_table", Material.CRAFTING_TABLE), 1),

                arguments(createGrid(1, 2,
                        ItemStack.of(Material.COAL), ItemStack.of(Material.STICK)),
                        Named.of("minecraft:torch", Material.TORCH), 4),

                arguments(createGrid(3, 1,
                        ItemStack.of(Material.WHEAT), ItemStack.of(Material.WHEAT), ItemStack.of(Material.WHEAT)),
                        Named.of("minecraft:bread", Material.BREAD), 1),

                arguments(createGrid(3, 3,
                        ItemStack.of(Material.STICK), ItemStack.AIR, ItemStack.of(Material.STICK),
                        ItemStack.of(Material.STICK), ItemStack.of(Material.STICK), ItemStack.of(Material.STICK),
                        ItemStack.of(Material.STICK), ItemStack.AIR, ItemStack.of(Material.STICK)),
                        Named.of("minecraft:ladder", Material.LADDER), 3),

                arguments(createGrid(3, 3,
                        ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.COBBLESTONE),
                        ItemStack.AIR, ItemStack.of(Material.STICK), ItemStack.AIR,
                        ItemStack.AIR, ItemStack.of(Material.STICK), ItemStack.AIR),
                        Named.of("minecraft:stone_pickaxe", Material.STONE_PICKAXE), 1),

                arguments(createGrid(2, 2,
                        ItemStack.AIR, ItemStack.AIR,
                        ItemStack.AIR, ItemStack.of(Material.ACACIA_LOG)),
                        Named.of("minecraft:acacia_planks", Material.ACACIA_PLANKS), 4),

                arguments(createGrid(3, 3,
                        ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.COBBLESTONE),
                        ItemStack.of(Material.COBBLESTONE), ItemStack.AIR, ItemStack.of(Material.COBBLESTONE),
                        ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.COBBLESTONE)),
                        Named.of("minecraft:furnace", Material.FURNACE), 1),

                arguments(createGrid(3, 3,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.AIR, ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS)),
                        Named.of("minecraft:chest", Material.CHEST), 1),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.AIR, ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS)),
                        Named.of("minecraft:oak_boat", Material.OAK_BOAT), 1),

                arguments(createGrid(3, 3,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.AIR, ItemStack.of(Material.STICK), ItemStack.AIR),
                        Named.of("minecraft:oak_sign", Material.OAK_SIGN), 3),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.STICK), ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.STICK), ItemStack.of(Material.OAK_PLANKS)),
                        Named.of("minecraft:oak_fence", Material.OAK_FENCE), 3),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.STICK), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.STICK),
                        ItemStack.of(Material.STICK), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.STICK)),
                        Named.of("minecraft:oak_fence_gate", Material.OAK_FENCE_GATE), 1),

                arguments(createGrid(2, 3,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS)),
                        Named.of("minecraft:oak_door", Material.OAK_DOOR), 3),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS)),
                        Named.of("minecraft:oak_trapdoor", Material.OAK_TRAPDOOR), 2),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.AIR, ItemStack.of(Material.OAK_PLANKS),
                        ItemStack.AIR, ItemStack.of(Material.OAK_PLANKS), ItemStack.AIR),
                        Named.of("minecraft:bowl", Material.BOWL), 4),

                arguments(createGrid(3, 1,
                        ItemStack.of(Material.SUGAR_CANE), ItemStack.of(Material.SUGAR_CANE), ItemStack.of(Material.SUGAR_CANE)),
                        Named.of("minecraft:paper", Material.PAPER), 3),

                arguments(createGrid(1, 1,
                        ItemStack.of(Material.SUGAR_CANE)),
                        Named.of("minecraft:sugar", Material.SUGAR), 1),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.PAPER), ItemStack.of(Material.PAPER), ItemStack.AIR,
                        ItemStack.of(Material.PAPER), ItemStack.of(Material.LEATHER), ItemStack.AIR),
                        Named.of("minecraft:book", Material.BOOK), 1),

                arguments(createGrid(3, 3,
                        ItemStack.AIR, ItemStack.of(Material.STICK), ItemStack.of(Material.STRING),
                        ItemStack.of(Material.STICK), ItemStack.AIR, ItemStack.of(Material.STRING),
                        ItemStack.AIR, ItemStack.of(Material.STICK), ItemStack.of(Material.STRING)),
                        Named.of("minecraft:bow", Material.BOW), 1),

                arguments(createGrid(1, 3,
                        ItemStack.of(Material.FLINT),
                        ItemStack.of(Material.STICK),
                        ItemStack.of(Material.FEATHER)),
                        Named.of("minecraft:arrow", Material.ARROW), 4),

                arguments(createGrid(1, 2,
                        ItemStack.of(Material.STICK),
                        ItemStack.of(Material.COBBLESTONE)),
                        Named.of("minecraft:lever", Material.LEVER), 1),

                arguments(createGrid(3, 3,
                        ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.COBBLESTONE),
                        ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.BOW), ItemStack.of(Material.COBBLESTONE),
                        ItemStack.of(Material.COBBLESTONE), ItemStack.of(Material.REDSTONE), ItemStack.of(Material.COBBLESTONE)),
                        Named.of("minecraft:dispenser", Material.DISPENSER), 1),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.REDSTONE_TORCH), ItemStack.of(Material.REDSTONE), ItemStack.of(Material.REDSTONE_TORCH),
                        ItemStack.of(Material.STONE), ItemStack.of(Material.STONE), ItemStack.of(Material.STONE)),
                        Named.of("minecraft:repeater", Material.REPEATER), 1),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.GLASS), ItemStack.of(Material.GLASS), ItemStack.of(Material.GLASS),
                        ItemStack.of(Material.GLASS), ItemStack.of(Material.GLASS), ItemStack.of(Material.GLASS)),
                        Named.of("minecraft:glass_pane", Material.GLASS_PANE), 16),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.IRON_INGOT), ItemStack.AIR, ItemStack.of(Material.IRON_INGOT),
                        ItemStack.of(Material.IRON_INGOT), ItemStack.of(Material.IRON_INGOT), ItemStack.of(Material.IRON_INGOT)),
                        Named.of("minecraft:minecart", Material.MINECART), 1),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.IRON_INGOT), ItemStack.AIR, ItemStack.of(Material.IRON_INGOT),
                        ItemStack.AIR, ItemStack.of(Material.IRON_INGOT), ItemStack.AIR),
                        Named.of("minecraft:bucket", Material.BUCKET), 1),

                arguments(createGrid(3, 3,
                        ItemStack.of(Material.GOLD_INGOT), ItemStack.of(Material.GOLD_INGOT), ItemStack.of(Material.GOLD_INGOT),
                        ItemStack.of(Material.GOLD_INGOT), ItemStack.of(Material.APPLE), ItemStack.of(Material.GOLD_INGOT),
                        ItemStack.of(Material.GOLD_INGOT), ItemStack.of(Material.GOLD_INGOT), ItemStack.of(Material.GOLD_INGOT)),
                        Named.of("minecraft:golden_apple", Material.GOLDEN_APPLE), 1),

                arguments(createGrid(3, 2,
                        ItemStack.of(Material.WHITE_WOOL), ItemStack.of(Material.WHITE_WOOL), ItemStack.of(Material.WHITE_WOOL),
                        ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.OAK_PLANKS)),
                        Named.of("minecraft:white_bed", Material.WHITE_BED), 1),

                arguments(createGrid(2, 2,
                        ItemStack.of(Material.IRON_INGOT), ItemStack.AIR,
                        ItemStack.AIR, ItemStack.of(Material.IRON_INGOT)),
                        Named.of("minecraft:shears", Material.SHEARS), 1)
        );
    }

    private static CraftingGrid createGrid(int width, int height, ItemStack... contents) {
        var totalSlots = width * height;
        var gridSlots = new CraftingGrid.CraftingGridSlot[totalSlots];

        for (var slotIndex = 0; slotIndex < totalSlots; slotIndex++) {
            var slotItem = slotIndex < contents.length ? contents[slotIndex] : ItemStack.AIR;
            gridSlots[slotIndex] = new CraftingGrid.CraftingGridSlot(slotIndex, slotItem);
        }

        return new CraftingGrid(width, height, gridSlots);
    }
}
