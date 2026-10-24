package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import rocks.minestom.crafting.CraftingGrid;

import java.util.ArrayList;
import java.util.List;

public final class MapCloningCraftingRecipe implements CraftingRecipe {

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() < 2) {
            return null;
        }

        CraftingGrid.CraftingGridSlot filledMapSlot = null;
        var emptyMapCount = 0;
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var gridSlot : nonEmptySlots) {
            var item = gridSlot.item();

            if (item.has(DataComponents.MAP_ID)) {
                if (filledMapSlot != null) {
                    return null;
                }
                filledMapSlot = gridSlot;
            } else if (item.material() == Material.MAP) {
                emptyMapCount++;
                usage.addTo(gridSlot.slotIndex(), 1);
            } else {
                return null;
            }
        }

        if (filledMapSlot == null || emptyMapCount < 1) {
            return null;
        }

        usage.addTo(filledMapSlot.slotIndex(), 1);

        var result = filledMapSlot.item().withAmount(emptyMapCount + 1);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }

    @Override
    public List<ItemStack> getRemainingItems(CraftingGrid craftingGrid) {
        var remainders = new ArrayList<ItemStack>(craftingGrid.size());

        for (var gridSlot : craftingGrid.slots()) {
            var slotItem = gridSlot.item();
            var useRemainder = slotItem.get(DataComponents.USE_REMAINDER);
            remainders.add(useRemainder == null ? ItemStack.AIR : useRemainder);
        }

        return remainders;
    }
}
