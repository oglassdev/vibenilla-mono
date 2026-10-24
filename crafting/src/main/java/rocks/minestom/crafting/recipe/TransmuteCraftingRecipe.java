package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import rocks.minestom.crafting.CraftingGrid;
import rocks.minestom.crafting.Ingredient;

public final class TransmuteCraftingRecipe implements CraftingRecipe {
    private final Ingredient baseIngredient;
    private final Ingredient catalystIngredient;
    private final Material resultMaterial;
    private final int resultCount;
    private final DataComponentMap componentPatch;

    public TransmuteCraftingRecipe(Ingredient baseIngredient, Ingredient catalystIngredient, Material resultMaterial, int resultCount, DataComponentMap componentPatch) {
        this.baseIngredient = baseIngredient;
        this.catalystIngredient = catalystIngredient;
        this.resultMaterial = resultMaterial;
        this.resultCount = resultCount;
        this.componentPatch = componentPatch;
    }

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() != 2) {
            return null;
        }

        CraftingGrid.CraftingGridSlot baseIngredientSlot = null;
        CraftingGrid.CraftingGridSlot catalystSlot = null;

        for (var gridSlot : nonEmptySlots) {
            if (baseIngredientSlot == null && this.baseIngredient.matches(gridSlot.item())) {
                baseIngredientSlot = gridSlot;
            } else if (catalystSlot == null && this.catalystIngredient.matches(gridSlot.item())) {
                catalystSlot = gridSlot;
            }
        }

        if (baseIngredientSlot == null || catalystSlot == null) {
            return null;
        }

        var resultStack = this.apply(baseIngredientSlot.item());

        if (resultStack.isSimilar(baseIngredientSlot.item()) && resultStack.amount() == baseIngredientSlot.item().amount()) {
            return null;
        }

        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);
        usage.addTo(baseIngredientSlot.slotIndex(), 1);
        usage.addTo(catalystSlot.slotIndex(), 1);
        return new CraftingRecipeMatch(this, resultStack, usage, craftingGrid);
    }

    private ItemStack apply(ItemStack baseStack) {
        var transformedStack = baseStack.withMaterial(this.resultMaterial).withAmount(this.resultCount);

        if (this.componentPatch != null && !this.componentPatch.isEmpty()) {
            for (var entry : this.componentPatch.entrySet()) {
                @SuppressWarnings("unchecked")
                var component = (DataComponent<Object>) entry.component();
                transformedStack = transformedStack.with(component, entry.value());
            }
        }

        return transformedStack;
    }
}
