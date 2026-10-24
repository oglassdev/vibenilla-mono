package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import rocks.minestom.crafting.CraftingGrid;

public final class TippedArrowCraftingRecipe implements CraftingRecipe {

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        if (craftingGrid.width() != 3 || craftingGrid.height() != 3) {
            return null;
        }

        if (craftingGrid.nonEmptySlots().size() != 9) {
            return null;
        }

        CraftingGrid.CraftingGridSlot potionSlot = null;
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var rowIndex = 0; rowIndex < 3; rowIndex++) {
            for (var columnIndex = 0; columnIndex < 3; columnIndex++) {
                var gridSlot = craftingGrid.slot(columnIndex, rowIndex);
                var item = gridSlot.item();

                if (item.isAir()) {
                    return null;
                }

                if (columnIndex == 1 && rowIndex == 1) {
                    if (item.material() != Material.LINGERING_POTION) {
                        return null;
                    }
                    potionSlot = gridSlot;
                } else {
                    if (item.material() != Material.ARROW) {
                        return null;
                    }
                }

                usage.addTo(gridSlot.slotIndex(), 1);
            }
        }

        if (potionSlot == null) {
            return null;
        }

        var potionContents = potionSlot.item().get(DataComponents.POTION_CONTENTS);
        var result = ItemStack.of(Material.TIPPED_ARROW, 8);

        if (potionContents != null) {
            result = result.with(DataComponents.POTION_CONTENTS, potionContents);
        }

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }
}
