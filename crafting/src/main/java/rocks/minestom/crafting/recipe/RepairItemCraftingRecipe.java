package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import rocks.minestom.crafting.CraftingGrid;

public final class RepairItemCraftingRecipe implements CraftingRecipe {

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() != 2) {
            return null;
        }

        var firstSlot = nonEmptySlots.get(0);
        var secondSlot = nonEmptySlots.get(1);
        var firstItem = firstSlot.item();
        var secondItem = secondSlot.item();

        if (!this.canCombine(firstItem, secondItem)) {
            return null;
        }

        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);
        usage.addTo(firstSlot.slotIndex(), 1);
        usage.addTo(secondSlot.slotIndex(), 1);

        var maxDamage = Math.max(
                firstItem.get(DataComponents.MAX_DAMAGE),
                secondItem.get(DataComponents.MAX_DAMAGE)
        );

        var firstDurability = firstItem.get(DataComponents.MAX_DAMAGE) - firstItem.get(DataComponents.DAMAGE);
        var secondDurability = secondItem.get(DataComponents.MAX_DAMAGE) - secondItem.get(DataComponents.DAMAGE);

        var combinedDurability = firstDurability + secondDurability + maxDamage * 5 / 100;
        var newDamage = Math.max(maxDamage - combinedDurability, 0);

        var result = ItemStack.of(firstItem.material())
                .with(DataComponents.MAX_DAMAGE, maxDamage)
                .with(DataComponents.DAMAGE, newDamage);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }

    private boolean canCombine(ItemStack first, ItemStack second) {
        if (first.material() != second.material()) {
            return false;
        }

        if (first.amount() != 1 || second.amount() != 1) {
            return false;
        }

        if (!first.has(DataComponents.MAX_DAMAGE) || !second.has(DataComponents.MAX_DAMAGE)) {
            return false;
        }

        if (!first.has(DataComponents.DAMAGE) || !second.has(DataComponents.DAMAGE)) {
            return false;
        }

        return true;
    }
}
