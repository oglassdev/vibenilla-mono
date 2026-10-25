package rocks.minestom.crafting;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;
import net.minestom.server.component.DataComponents;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.crafting.book.BookRecipe;

/** Moves a recipe's ingredients from the player's inventory into a crafting grid. */
final class RecipePlacer {
    enum Result {
        PLACED,
        NOTHING,
        MISSING_ITEMS
    }

    private static final int MAX_AMOUNT = 64;

    private RecipePlacer() {}

    /**
     * Returns the grid's contents to the inventory, then fills the grid with one set of
     * ingredients, or as many sets as fit for {@code all}. When the grid already holds the recipe
     * and {@code all} is false, one more set is added. Nothing changes if the grid's current
     * contents do not fit back into the inventory; the grid is only emptied if the recipe cannot be
     * filled.
     */
    static Result place(
            AbstractInventory grid,
            int[] gridSlots,
            int gridWidth,
            int gridHeight,
            PlayerInventory source,
            BookRecipe.Layout layout,
            boolean all,
            boolean matching) {
        var targets = targets(layout, gridWidth, gridHeight);
        if (targets == null) {
            return Result.NOTHING;
        }

        var main = new ItemStack[PlayerInventory.INNER_INVENTORY_SIZE];
        for (var slot = 0; slot < main.length; slot++) {
            main[slot] = source.getItemStack(slot);
        }

        var smallestStack = MAX_AMOUNT;
        for (var slot : gridSlots) {
            var stack = grid.getItemStack(slot);
            if (stack.isAir()) {
                continue;
            }
            smallestStack = Math.min(smallestStack, stack.amount());
            if (!insert(main, stack)) {
                return Result.NOTHING;
            }
        }

        var counts = new Object2IntOpenHashMap<Material>();
        for (var stack : main) {
            if (isPlain(stack)) {
                counts.addTo(stack.material(), stack.amount());
            }
        }

        var wanted = all ? MAX_AMOUNT : matching ? smallestStack + 1 : 1;
        Material[] chosen = null;
        var amount = wanted;
        while (amount >= 1 && (chosen = assign(targets, counts, amount)) == null) {
            amount--;
        }
        if (chosen != null && matching && !all && amount < wanted) {
            return Result.NOTHING;
        }

        var placed = new ItemStack[targets.length];
        Arrays.fill(placed, ItemStack.AIR);
        if (chosen != null) {
            for (var index = 0; index < targets.length; index++) {
                if (chosen[index] != null) {
                    placed[index] = take(main, chosen[index], amount);
                }
            }
        }

        for (var index = 0; index < targets.length; index++) {
            if (!grid.getItemStack(gridSlots[index]).equals(placed[index])) {
                grid.setItemStack(gridSlots[index], placed[index]);
            }
        }
        for (var slot = 0; slot < main.length; slot++) {
            if (!source.getItemStack(slot).equals(main[slot])) {
                source.setItemStack(slot, main[slot]);
            }
        }
        return chosen == null ? Result.MISSING_ITEMS : Result.PLACED;
    }

    private static Ingredient @Nullable [] targets(
            BookRecipe.Layout layout, int width, int height) {
        var targets = new Ingredient[width * height];
        if (!layout.shaped()) {
            if (layout.slots().length > targets.length) {
                return null;
            }
            System.arraycopy(layout.slots(), 0, targets, 0, layout.slots().length);
            return targets;
        }

        if (layout.width() > width || layout.height() > height) {
            return null;
        }
        for (var row = 0; row < layout.height(); row++) {
            for (var column = 0; column < layout.width(); column++) {
                targets[column + row * width] = layout.slots()[column + row * layout.width()];
            }
        }
        return targets;
    }

    /** Picks a material for every ingredient so each slot gets {@code amount} items. */
    private static Material @Nullable [] assign(
            Ingredient[] targets, Object2IntOpenHashMap<Material> counts, int amount) {
        var remaining = new Object2IntOpenHashMap<>(counts);
        var order =
                IntStream.range(0, targets.length)
                        .filter(index -> targets[index] != null)
                        .boxed()
                        .sorted(
                                Comparator.comparingLong(
                                        index ->
                                                targets[index].getMaterials().stream()
                                                        .filter(m -> counts.getInt(m) >= amount)
                                                        .count()))
                        .toList();

        var chosen = new Material[targets.length];
        for (var index : order) {
            Material best = null;
            for (var material : targets[index].getMaterials()) {
                var available = remaining.getInt(material);
                if (available >= amount
                        && amount <= material.maxStackSize()
                        && (best == null || available > remaining.getInt(best))) {
                    best = material;
                }
            }
            if (best == null) {
                return null;
            }
            chosen[index] = best;
            remaining.addTo(best, -amount);
        }
        return chosen;
    }

    private static ItemStack take(ItemStack[] main, Material material, int amount) {
        var taken = ItemStack.AIR;
        for (var slot = 0; slot < main.length && taken.amount() < amount; slot++) {
            var stack = main[slot];
            if (!isPlain(stack)
                    || stack.material() != material
                    || (!taken.isAir() && !taken.isSimilar(stack))) {
                continue;
            }
            var moved = Math.min(stack.amount(), amount - taken.amount());
            taken = stack.withAmount(taken.amount() + moved);
            main[slot] = stack.consume(moved);
        }
        return taken;
    }

    private static boolean insert(ItemStack[] main, ItemStack stack) {
        var remaining = stack;
        for (var slot = 0; slot < main.length && !remaining.isAir(); slot++) {
            var existing = main[slot];
            if (!existing.isAir()
                    && existing.isSimilar(remaining)
                    && existing.amount() < existing.maxStackSize()) {
                var moved =
                        Math.min(existing.maxStackSize() - existing.amount(), remaining.amount());
                main[slot] = existing.withAmount(existing.amount() + moved);
                remaining = remaining.consume(moved);
            }
        }
        for (var slot = 0; slot < main.length && !remaining.isAir(); slot++) {
            if (main[slot].isAir()) {
                main[slot] = remaining;
                remaining = ItemStack.AIR;
            }
        }
        return remaining.isAir();
    }

    /** Stacks that vanilla lets recipes consume from: undamaged, unenchanted and unnamed. */
    private static boolean isPlain(ItemStack stack) {
        if (stack.isAir() || stack.get(DataComponents.DAMAGE, 0) > 0) {
            return false;
        }
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return (enchantments == null || enchantments.enchantments().isEmpty())
                && stack.get(DataComponents.CUSTOM_NAME) == null;
    }
}
