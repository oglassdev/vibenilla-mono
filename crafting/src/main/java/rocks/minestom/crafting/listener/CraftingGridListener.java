package rocks.minestom.crafting.listener;

import net.minestom.server.event.EventListener;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
import org.jetbrains.annotations.NotNull;
import rocks.minestom.crafting.CraftingManager;

public final class CraftingGridListener implements EventListener<InventoryItemChangeEvent> {
    private final CraftingManager craftingManager;

    public CraftingGridListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @Override
    public @NotNull Class<InventoryItemChangeEvent> eventType() {
        return InventoryItemChangeEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull InventoryItemChangeEvent event) {
        var workspace = this.craftingManager.workspace(event.getInventory());

        if (workspace == null) {
            return Result.SUCCESS;
        }

        var slotIndex = event.getSlot();

        if (slotIndex == workspace.resultSlot()) {
            if (!workspace.isUpdatingResult() && event.getNewItem().isAir() && !event.getPreviousItem().isAir()) {
                workspace.craft(workspace.owner());
            }

            return Result.SUCCESS;
        }

        if (workspace.isGridSlot(slotIndex) && !workspace.isUpdatingGrid()) {
            workspace.refresh();
        }

        return Result.SUCCESS;
    }
}
