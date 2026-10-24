package rocks.minestom.crafting;

import net.minestom.server.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CraftingGrid {
    private final int width;
    private final int height;
    private final CraftingGridSlot[] slots;

    CraftingGrid(int width, int height, CraftingGridSlot[] slots) {
        this.width = width;
        this.height = height;
        this.slots = slots;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int size() {
        return this.slots.length;
    }

    public CraftingGridSlot slot(int columnIndex, int rowIndex) {
        return this.slots[columnIndex + rowIndex * this.width];
    }

    public CraftingGridSlot[] slots() {
        return this.slots;
    }

    public List<CraftingGridSlot> nonEmptySlots() {
        var nonEmptySlots = new ArrayList<CraftingGridSlot>();

        for (var gridSlot : this.slots) {
            if (!gridSlot.item().isAir()) {
                nonEmptySlots.add(gridSlot);
            }
        }

        return nonEmptySlots;
    }

    public TrimmedGrid trimmed() {
        var minColumnIndex = this.width;
        var minRowIndex = this.height;
        var maxColumnIndex = -1;
        var maxRowIndex = -1;
        var filledSlotCount = 0;

        for (var rowIndex = 0; rowIndex < this.height; rowIndex++) {
            for (var columnIndex = 0; columnIndex < this.width; columnIndex++) {
                var gridItem = this.slot(columnIndex, rowIndex).item();

                if (!gridItem.isAir()) {
                    filledSlotCount++;
                    minColumnIndex = Math.min(minColumnIndex, columnIndex);
                    minRowIndex = Math.min(minRowIndex, rowIndex);
                    maxColumnIndex = Math.max(maxColumnIndex, columnIndex);
                    maxRowIndex = Math.max(maxRowIndex, rowIndex);
                }
            }
        }

        if (filledSlotCount == 0) {
            return new TrimmedGrid(0, 0, 0, 0, new CraftingGridSlot[0], 0);
        }

        var trimmedWidth = maxColumnIndex - minColumnIndex + 1;
        var trimmedHeight = maxRowIndex - minRowIndex + 1;
        var trimmedSlots = new CraftingGridSlot[trimmedWidth * trimmedHeight];

        for (var rowIndex = 0; rowIndex < trimmedHeight; rowIndex++) {
            for (var columnIndex = 0; columnIndex < trimmedWidth; columnIndex++) {
                trimmedSlots[columnIndex + rowIndex * trimmedWidth] = this.slot(minColumnIndex + columnIndex, minRowIndex + rowIndex);
            }
        }

        return new TrimmedGrid(trimmedWidth, trimmedHeight, minColumnIndex, minRowIndex, trimmedSlots, filledSlotCount);
    }

    public record CraftingGridSlot(int slotIndex, ItemStack item) {

    }

    public record TrimmedGrid(int width, int height, int offsetX, int offsetY, CraftingGridSlot[] slots, int ingredientCount) {
        public ItemStack item(int columnIndex, int rowIndex) {
            return this.slots[columnIndex + rowIndex * this.width].item();
        }

        public CraftingGridSlot slot(int columnIndex, int rowIndex) {
            return this.slots[columnIndex + rowIndex * this.width];
        }
    }
}
