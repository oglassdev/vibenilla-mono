package rocks.minestom.crafting.recipe;

import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import rocks.minestom.crafting.CraftingGrid;

import java.util.ArrayList;
import java.util.List;

public interface CraftingRecipe {
    CraftingRecipeMatch match(CraftingGrid craftingGrid);

    default List<ItemStack> getRemainingItems(CraftingGrid craftingGrid) {
        var remainders = new ArrayList<ItemStack>(craftingGrid.size());

        for (var gridSlot : craftingGrid.slots()) {
            var slotItem = gridSlot.item();
            var useRemainder = slotItem.get(DataComponents.USE_REMAINDER);
            remainders.add(useRemainder == null ? ItemStack.AIR : useRemainder);
        }

        return remainders;
    }
}
