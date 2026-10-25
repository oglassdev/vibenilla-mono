package rocks.minestom.crafting.listener;

import net.minestom.server.event.EventListener;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
import org.jetbrains.annotations.NotNull;
import rocks.minestom.crafting.CraftingManager;

/** Unlocks recipes when an item enters a player's inventory. */
public final class RecipeBookItemListener implements EventListener<InventoryItemChangeEvent> {
    private final CraftingManager craftingManager;

    public RecipeBookItemListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @Override
    public @NotNull Class<InventoryItemChangeEvent> eventType() {
        return InventoryItemChangeEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull InventoryItemChangeEvent event) {
        var workspace = this.craftingManager.workspace(event.getInventory());

        if (workspace != null && event.getSlot() != workspace.resultSlot()) {
            this.craftingManager.itemObtained(workspace.owner(), event.getNewItem());
        }

        return Result.SUCCESS;
    }
}
