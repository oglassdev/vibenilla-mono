package rocks.minestom.crafting;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.crafting.recipe.CraftingRecipeMatch;

public final class CraftingWorkspace {
    private final CraftingManager manager;
    private final AbstractInventory inventory;
    private final Player owner;
    private final int resultSlot;
    private final int[] gridSlots;
    private final IntSet gridSlotSet;
    private final int gridWidth;
    private final int gridHeight;

    private boolean updatingGrid;
    private boolean updatingResult;
    private @Nullable CraftingRecipeMatch currentMatch;

    CraftingWorkspace(CraftingManager manager, Player owner, AbstractInventory inventory, int resultSlot, int[] gridSlots, int gridWidth, int gridHeight) {
        this.manager = manager;
        this.owner = owner;
        this.inventory = inventory;
        this.resultSlot = resultSlot;
        this.gridSlots = gridSlots;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;

        var trackedSlots = new IntOpenHashSet();

        for (var slot : gridSlots) {
            trackedSlots.add(slot);
        }

        this.gridSlotSet = IntSets.unmodifiable(trackedSlots);
    }

    public Player owner() {
        return this.owner;
    }

    public int resultSlot() {
        return this.resultSlot;
    }

    public boolean isGridSlot(int slot) {
        return this.gridSlotSet.contains(slot);
    }

    public boolean isUpdatingGrid() {
        return this.updatingGrid;
    }

    public boolean isUpdatingResult() {
        return this.updatingResult;
    }

    public @Nullable CraftingRecipeMatch currentMatch() {
        return this.currentMatch;
    }

    public void refresh() {
        var grid = this.createGrid();
        var recipeMatch = this.manager.match(grid);
        this.currentMatch = recipeMatch;
        this.updateResult(recipeMatch == null ? ItemStack.AIR : recipeMatch.result());
    }

    private CraftingGrid createGrid() {
        var slots = new CraftingGrid.CraftingGridSlot[this.gridSlots.length];

        for (var rowIndex = 0; rowIndex < this.gridHeight; rowIndex++) {
            for (var columnIndex = 0; columnIndex < this.gridWidth; columnIndex++) {
                var slotIndex = this.gridSlots[columnIndex + rowIndex * this.gridWidth];
                slots[columnIndex + rowIndex * this.gridWidth] = new CraftingGrid.CraftingGridSlot(slotIndex, this.inventory.getItemStack(slotIndex));
            }
        }

        return new CraftingGrid(this.gridWidth, this.gridHeight, slots);
    }

    public void craft(Player player) {
        var recipeMatch = this.currentMatch;

        if (recipeMatch == null) {
            return;
        }

        this.currentMatch = null;
        this.updatingGrid = true;

        try {
            this.consumeInputs(recipeMatch);
            this.handleRemainders(player, recipeMatch);
        } finally {
            this.updatingGrid = false;
        }

        this.refresh();
    }

    private void consumeInputs(CraftingRecipeMatch recipeMatch) {
        for (var usageEntry : recipeMatch.usage().int2IntEntrySet()) {
            var slotIndex = usageEntry.getIntKey();
            var usageAmount = usageEntry.getIntValue();

            if (usageAmount <= 0) {
                continue;
            }

            var slotItem = this.inventory.getItemStack(slotIndex);
            var remaining = slotItem.consume(usageAmount);
            this.inventory.setItemStack(slotIndex, remaining);
        }
    }

    private void handleRemainders(Player player, CraftingRecipeMatch recipeMatch) {
        var remainders = recipeMatch.recipe().getRemainingItems(recipeMatch.grid());
        var gridSlots = recipeMatch.grid().slots();

        for (var slotOffset = 0; slotOffset < gridSlots.length; slotOffset++) {
            var remainder = remainders.get(slotOffset);

            if (remainder == null || remainder.isAir()) {
                continue;
            }

            var slotIndex = gridSlots[slotOffset].slotIndex();
            var currentStack = this.inventory.getItemStack(slotIndex);

            if (currentStack.isAir()) {
                this.inventory.setItemStack(slotIndex, remainder);
            } else if (currentStack.isSimilar(remainder)) {
                this.inventory.setItemStack(slotIndex, currentStack.withAmount(currentStack.amount() + remainder.amount()));
            } else if (!player.getInventory().addItemStack(remainder)) {
                player.dropItem(remainder);
            }
        }
    }

    private void updateResult(ItemStack stack) {
        this.updatingResult = true;

        try {
            this.inventory.setItemStack(this.resultSlot, stack);
        } finally {
            this.updatingResult = false;
        }
    }

    public void returnGridItems(Player player) {
        this.updatingGrid = true;

        try {
            for (var slotIndex : this.gridSlots) {
                var gridItem = this.inventory.getItemStack(slotIndex);

                if (gridItem.isAir()) {
                    continue;
                }

                this.inventory.setItemStack(slotIndex, ItemStack.AIR);

                if (!player.getInventory().addItemStack(gridItem)) {
                    player.dropItem(gridItem);
                }
            }

            this.inventory.setItemStack(this.resultSlot, ItemStack.AIR);
        } finally {
            this.updatingGrid = false;
        }
    }
}
