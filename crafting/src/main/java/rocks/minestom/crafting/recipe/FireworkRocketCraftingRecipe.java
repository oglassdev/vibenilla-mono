package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;
import rocks.minestom.crafting.CraftingGrid;

import java.util.ArrayList;

public final class FireworkRocketCraftingRecipe implements CraftingRecipe {

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() < 2) {
            return null;
        }

        var hasPaper = false;
        var gunpowderCount = 0;
        var explosions = new ArrayList<FireworkExplosion>();
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var gridSlot : nonEmptySlots) {
            var item = gridSlot.item();
            var material = item.material();

            if (material == Material.PAPER) {
                if (hasPaper) {
                    return null;
                }
                hasPaper = true;
                usage.addTo(gridSlot.slotIndex(), 1);
            } else if (material == Material.GUNPOWDER) {
                if (++gunpowderCount > 3) {
                    return null;
                }
                usage.addTo(gridSlot.slotIndex(), 1);
            } else if (material == Material.FIREWORK_STAR) {
                var explosion = item.get(DataComponents.FIREWORK_EXPLOSION);
                if (explosion != null) {
                    explosions.add(explosion);
                }
                usage.addTo(gridSlot.slotIndex(), 1);
            } else {
                return null;
            }
        }

        if (!hasPaper || gunpowderCount < 1) {
            return null;
        }

        var fireworks = new FireworkList(gunpowderCount, explosions);
        var result = ItemStack.of(Material.FIREWORK_ROCKET, 3).with(DataComponents.FIREWORKS, fireworks);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }
}
