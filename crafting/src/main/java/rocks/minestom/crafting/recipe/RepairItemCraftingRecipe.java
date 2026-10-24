package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import rocks.minestom.crafting.CraftingGrid;

public final class RepairItemCraftingRecipe implements CraftingRecipe {
    private static final Set<RegistryKey<Enchantment>> CURSES =
            Set.of(Enchantment.BINDING_CURSE, Enchantment.VANISHING_CURSE);

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

        var maxDamage =
                Math.max(
                        firstItem.get(DataComponents.MAX_DAMAGE),
                        secondItem.get(DataComponents.MAX_DAMAGE));

        var firstDurability =
                firstItem.get(DataComponents.MAX_DAMAGE) - firstItem.get(DataComponents.DAMAGE);
        var secondDurability =
                secondItem.get(DataComponents.MAX_DAMAGE) - secondItem.get(DataComponents.DAMAGE);

        var combinedDurability = firstDurability + secondDurability + maxDamage * 5 / 100;
        var newDamage = Math.max(maxDamage - combinedDurability, 0);

        var result =
                ItemStack.of(firstItem.material())
                        .with(DataComponents.MAX_DAMAGE, maxDamage)
                        .with(DataComponents.DAMAGE, newDamage);
        var curses = this.mergeCurses(firstItem, secondItem);

        if (!curses.isEmpty()) {
            result = result.with(DataComponents.ENCHANTMENTS, new EnchantmentList(curses));
        }

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }

    private Map<RegistryKey<Enchantment>, Integer> mergeCurses(ItemStack first, ItemStack second) {
        var curses = new HashMap<RegistryKey<Enchantment>, Integer>();

        for (var item : List.of(first, second)) {
            var enchantments = item.get(DataComponents.ENCHANTMENTS);

            if (enchantments == null) {
                continue;
            }

            for (var entry : enchantments.enchantments().entrySet()) {
                if (CURSES.contains(entry.getKey())) {
                    curses.merge(entry.getKey(), entry.getValue(), Math::max);
                }
            }
        }

        return curses;
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
