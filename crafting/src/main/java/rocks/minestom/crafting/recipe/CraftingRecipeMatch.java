package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.item.ItemStack;
import rocks.minestom.crafting.CraftingGrid;

public record CraftingRecipeMatch(CraftingRecipe recipe, ItemStack result, Int2IntOpenHashMap usage, CraftingGrid grid) {

}
