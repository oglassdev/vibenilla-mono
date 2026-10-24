package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.PotDecorations;
import rocks.minestom.crafting.CraftingGrid;
import rocks.minestom.crafting.Ingredient;
import rocks.minestom.crafting.ItemTagManager;

public final class DecoratedPotCraftingRecipe implements CraftingRecipe {
    private final Ingredient shardIngredient;

    public DecoratedPotCraftingRecipe(ItemTagManager tagManager) {
        this.shardIngredient = Ingredient.ofTag(tagManager.materials(Key.key("minecraft:decorated_pot_ingredients")));
    }

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        if (craftingGrid.width() != 3 || craftingGrid.height() != 3) {
            return null;
        }

        if (craftingGrid.nonEmptySlots().size() != 4) {
            return null;
        }

        var backShardSlot = craftingGrid.slot(1, 0);
        var leftShardSlot = craftingGrid.slot(0, 1);
        var rightShardSlot = craftingGrid.slot(2, 1);
        var frontShardSlot = craftingGrid.slot(1, 2);

        if (!this.matchesShard(backShardSlot.item()) || !this.matchesShard(leftShardSlot.item()) || !this.matchesShard(rightShardSlot.item()) || !this.matchesShard(frontShardSlot.item())) {
            return null;
        }

        var decorations = new PotDecorations(
                backShardSlot.item(),
                leftShardSlot.item(),
                rightShardSlot.item(),
                frontShardSlot.item());

        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);
        usage.addTo(backShardSlot.slotIndex(), 1);
        usage.addTo(leftShardSlot.slotIndex(), 1);
        usage.addTo(rightShardSlot.slotIndex(), 1);
        usage.addTo(frontShardSlot.slotIndex(), 1);

        return new CraftingRecipeMatch(
                this,
                ItemStack.of(Material.DECORATED_POT).with(DataComponents.POT_DECORATIONS, decorations),
                usage, craftingGrid);
    }

    private boolean matchesShard(ItemStack shardCandidate) {
        return this.shardIngredient != null && this.shardIngredient.matches(shardCandidate);
    }
}
