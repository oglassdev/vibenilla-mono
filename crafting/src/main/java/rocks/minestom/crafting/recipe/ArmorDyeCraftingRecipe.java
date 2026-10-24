package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.color.Color;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import rocks.minestom.crafting.CraftingGrid;
import rocks.minestom.crafting.Ingredient;
import rocks.minestom.crafting.ItemTagManager;

import java.util.ArrayList;
import java.util.Map;

public final class ArmorDyeCraftingRecipe implements CraftingRecipe {
    private static final Map<Material, DyeColor> DYE_COLORS = Map.ofEntries(
            Map.entry(Material.WHITE_DYE, DyeColor.WHITE),
            Map.entry(Material.ORANGE_DYE, DyeColor.ORANGE),
            Map.entry(Material.MAGENTA_DYE, DyeColor.MAGENTA),
            Map.entry(Material.LIGHT_BLUE_DYE, DyeColor.LIGHT_BLUE),
            Map.entry(Material.YELLOW_DYE, DyeColor.YELLOW),
            Map.entry(Material.LIME_DYE, DyeColor.LIME),
            Map.entry(Material.PINK_DYE, DyeColor.PINK),
            Map.entry(Material.GRAY_DYE, DyeColor.GRAY),
            Map.entry(Material.LIGHT_GRAY_DYE, DyeColor.LIGHT_GRAY),
            Map.entry(Material.CYAN_DYE, DyeColor.CYAN),
            Map.entry(Material.PURPLE_DYE, DyeColor.PURPLE),
            Map.entry(Material.BLUE_DYE, DyeColor.BLUE),
            Map.entry(Material.BROWN_DYE, DyeColor.BROWN),
            Map.entry(Material.GREEN_DYE, DyeColor.GREEN),
            Map.entry(Material.RED_DYE, DyeColor.RED),
            Map.entry(Material.BLACK_DYE, DyeColor.BLACK)
    );

    private final Ingredient dyeableIngredient;

    public ArmorDyeCraftingRecipe(ItemTagManager tagManager) {
        this.dyeableIngredient = Ingredient.ofTag(tagManager.materials(Key.key("minecraft:dyeable")));
    }

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() < 2) {
            return null;
        }

        ItemStack dyeableItem = null;
        CraftingGrid.CraftingGridSlot dyeableSlot = null;
        var dyeColors = new ArrayList<RGBLike>();
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var gridSlot : nonEmptySlots) {
            var item = gridSlot.item();

            if (this.dyeableIngredient != null && this.dyeableIngredient.matches(item)) {
                if (dyeableItem != null) {
                    return null;
                }
                dyeableItem = item;
                dyeableSlot = gridSlot;
            } else {
                var dyeColor = DYE_COLORS.get(item.material());
                if (dyeColor == null) {
                    return null;
                }
                dyeColors.add(dyeColor.color());
                usage.addTo(gridSlot.slotIndex(), 1);
            }
        }

        if (dyeableItem == null || dyeColors.isEmpty()) {
            return null;
        }

        usage.addTo(dyeableSlot.slotIndex(), 1);

        var existingColor = dyeableItem.get(DataComponents.DYED_COLOR);
        Color baseColor;

        if (existingColor != null) {
            baseColor = Color.fromRGBLike(existingColor);
        } else {
            baseColor = new Color(0xA06540);
        }

        var newColor = baseColor.mixWith(dyeColors.toArray(new RGBLike[0]));
        var result = dyeableItem.with(DataComponents.DYED_COLOR, newColor);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }
}
