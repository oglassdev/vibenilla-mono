package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.item.ItemStack;
import rocks.minestom.crafting.CraftingGrid;
import rocks.minestom.crafting.Ingredient;

public final class ShapedCraftingRecipe implements CraftingRecipe {
    private final int patternWidth;
    private final int patternHeight;
    private final Ingredient[] patternIngredients;
    private final ItemStack resultStack;
    private final int requiredIngredientCount;

    public ShapedCraftingRecipe(int patternWidth, int patternHeight, Ingredient[] patternIngredients, ItemStack resultStack) {
        this.patternWidth = patternWidth;
        this.patternHeight = patternHeight;
        this.patternIngredients = patternIngredients;
        this.resultStack = resultStack;
        var presentIngredientCount = 0;

        for (var ingredient : patternIngredients) {
            if (ingredient != null && !ingredient.isEmpty()) {
                presentIngredientCount++;
            }
        }

        this.requiredIngredientCount = presentIngredientCount;
    }

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var trimmedGrid = craftingGrid.trimmed();

        if (trimmedGrid.ingredientCount() != this.requiredIngredientCount) {
            return null;
        }

        if (trimmedGrid.width() != this.patternWidth || trimmedGrid.height() != this.patternHeight) {
            return null;
        }

        var usage = this.tryMatch(trimmedGrid, false);

        if (usage == null) {
            usage = this.tryMatch(trimmedGrid, true);
        }

        if (usage == null) {
            return null;
        }

        return new CraftingRecipeMatch(this, this.resultStack, usage, craftingGrid);
    }

    private Int2IntOpenHashMap tryMatch(CraftingGrid.TrimmedGrid trimmedGrid, boolean mirrorPattern) {
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var rowIndex = 0; rowIndex < this.patternHeight; rowIndex++) {
            for (var columnIndex = 0; columnIndex < this.patternWidth; columnIndex++) {
                var ingredientIndex = mirrorPattern
                        ? (this.patternWidth - 1 - columnIndex) + rowIndex * this.patternWidth
                        : columnIndex + rowIndex * this.patternWidth;

                var ingredient = this.patternIngredients[ingredientIndex];
                var gridSlot = trimmedGrid.slot(columnIndex, rowIndex);
                var slotItem = gridSlot.item();

                if (ingredient == null || ingredient.isEmpty()) {
                    if (!slotItem.isAir()) {
                        return null;
                    }

                    continue;
                }

                if (slotItem.isAir() || !ingredient.matches(slotItem)) {
                    return null;
                }

                usage.addTo(gridSlot.slotIndex(), 1);
            }
        }

        return usage;
    }

    public ItemStack result() {
        return this.resultStack;
    }
}
