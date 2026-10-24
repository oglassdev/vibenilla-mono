package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.MapPostProcessing;
import rocks.minestom.crafting.CraftingGrid;

public final class MapExtendingCraftingRecipe implements CraftingRecipe {

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        if (craftingGrid.width() != 3 || craftingGrid.height() != 3) {
            return null;
        }

        if (craftingGrid.nonEmptySlots().size() != 9) {
            return null;
        }

        CraftingGrid.CraftingGridSlot filledMapSlot = null;
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
                    if (!item.has(DataComponents.MAP_ID)) {
                        return null;
                    }
                    filledMapSlot = gridSlot;
                } else {
                    if (item.material() != Material.PAPER) {
                        return null;
                    }
                }

                usage.addTo(gridSlot.slotIndex(), 1);
            }
        }

        if (filledMapSlot == null) {
            return null;
        }

        var result = filledMapSlot.item()
                .withAmount(1)
                .with(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }
}
