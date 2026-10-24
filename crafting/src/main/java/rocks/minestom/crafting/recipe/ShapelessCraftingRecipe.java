package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;
import java.util.List;
import net.minestom.server.item.ItemStack;
import rocks.minestom.crafting.CraftingGrid;
import rocks.minestom.crafting.Ingredient;

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

        if (!this.canAssignAll(nonEmptySlots)) {
            return null;
        }

        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var gridSlot : nonEmptySlots) {
            usage.addTo(gridSlot.slotIndex(), 1);
        }

        return new CraftingRecipeMatch(this, this.resultStack, usage, craftingGrid);
    }

    private boolean canAssignAll(List<CraftingGrid.CraftingGridSlot> slots) {
        var ingredientOwners = new int[this.requiredIngredients.size()];
        Arrays.fill(ingredientOwners, -1);

        for (var slotOffset = 0; slotOffset < slots.size(); slotOffset++) {
            var visited = new boolean[ingredientOwners.length];

            if (!this.assign(slots, slotOffset, visited, ingredientOwners)) {
                return false;
            }
        }

        return true;
    }

    private boolean assign(
            List<CraftingGrid.CraftingGridSlot> slots,
            int slotOffset,
            boolean[] visited,
            int[] ingredientOwners) {
        var slotItem = slots.get(slotOffset).item();

        for (var ingredientIndex = 0; ingredientIndex < visited.length; ingredientIndex++) {
            if (visited[ingredientIndex]
                    || !this.requiredIngredients.get(ingredientIndex).matches(slotItem)) {
                continue;
            }

            visited[ingredientIndex] = true;
            var owner = ingredientOwners[ingredientIndex];

            if (owner == -1 || this.assign(slots, owner, visited, ingredientOwners)) {
                ingredientOwners[ingredientIndex] = slotOffset;
                return true;
            }
        }

        return false;
    }
}
