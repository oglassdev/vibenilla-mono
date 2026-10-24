package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.item.ItemStack;
import rocks.minestom.crafting.CraftingGrid;
import rocks.minestom.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

public final class ShapelessCraftingRecipe implements CraftingRecipe {
    private final List<Ingredient> requiredIngredients;
    private final ItemStack resultStack;

    public ShapelessCraftingRecipe(List<Ingredient> ingredients, ItemStack resultStack) {
        this.requiredIngredients = List.copyOf(ingredients);
        this.resultStack = resultStack;
    }

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() != this.requiredIngredients.size()) {
            return null;
        }

        var remainingIngredients = new ArrayList<>(this.requiredIngredients);
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var gridSlot : nonEmptySlots) {
            var slotItem = gridSlot.item();
            var matchedIngredient = false;

            for (var ingredientIndex = 0; ingredientIndex < remainingIngredients.size(); ingredientIndex++) {
                var ingredient = remainingIngredients.get(ingredientIndex);

                if (ingredient.matches(slotItem)) {
                    remainingIngredients.remove(ingredientIndex);
                    usage.addTo(gridSlot.slotIndex(), 1);
                    matchedIngredient = true;
                    break;
                }
            }

            if (!matchedIngredient) {
                return null;
            }
        }

        if (!remainingIngredients.isEmpty()) {
            return null;
        }

        return new CraftingRecipeMatch(this, this.resultStack, usage, craftingGrid);
    }
}
