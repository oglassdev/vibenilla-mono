package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.WrittenBookContent;
import rocks.minestom.crafting.CraftingGrid;
import rocks.minestom.crafting.Ingredient;
import rocks.minestom.crafting.ItemTagManager;

import java.util.ArrayList;
import java.util.List;

public final class BookCloningCraftingRecipe implements CraftingRecipe {
    private final Ingredient bookCloningTargetIngredient;

    public BookCloningCraftingRecipe(ItemTagManager tagManager) {
        this.bookCloningTargetIngredient = Ingredient.ofTag(tagManager.materials(Key.key("minecraft:book_cloning_target")));
    }

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() < 2) {
            return null;
        }

        CraftingGrid.CraftingGridSlot writtenBookSlot = null;
        var blankBookCount = 0;
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var gridSlot : nonEmptySlots) {
            var item = gridSlot.item();

            if (item.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                if (writtenBookSlot != null) {
                    return null;
                }
                writtenBookSlot = gridSlot;
            } else if (this.bookCloningTargetIngredient != null && this.bookCloningTargetIngredient.matches(item)) {
                blankBookCount++;
                usage.addTo(gridSlot.slotIndex(), 1);
            } else {
                return null;
            }
        }

        if (writtenBookSlot == null || blankBookCount < 1) {
            return null;
        }

        var writtenBook = writtenBookSlot.item();
        var content = writtenBook.get(DataComponents.WRITTEN_BOOK_CONTENT);

        if (content == null || content.generation() >= 2) {
            return null;
        }

        usage.addTo(writtenBookSlot.slotIndex(), 1);

        var newContent = new WrittenBookContent(
                content.title(),
                content.author(),
                content.generation() + 1,
                content.pages(),
                content.resolved()
        );

        var result = writtenBook.with(DataComponents.WRITTEN_BOOK_CONTENT, newContent).withAmount(blankBookCount);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }

    @Override
    public List<ItemStack> getRemainingItems(CraftingGrid craftingGrid) {
        var remainders = new ArrayList<ItemStack>(craftingGrid.size());

        for (var gridSlot : craftingGrid.slots()) {
            var slotItem = gridSlot.item();

            if (slotItem.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                remainders.add(slotItem.withAmount(1));
                continue;
            }

            var useRemainder = slotItem.get(DataComponents.USE_REMAINDER);
            remainders.add(useRemainder == null ? ItemStack.AIR : useRemainder);
        }

        return remainders;
    }
}
