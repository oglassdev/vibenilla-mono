package rocks.minestom.crafting.listener;

import net.minestom.server.event.EventListener;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import org.jetbrains.annotations.NotNull;
import rocks.minestom.crafting.CraftingManager;

public final class CraftingCloseListener implements EventListener<@NotNull InventoryCloseEvent> {
    private final CraftingManager craftingManager;

    public CraftingCloseListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @Override
    public @NotNull Class<InventoryCloseEvent> eventType() {
        return InventoryCloseEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull InventoryCloseEvent event) {
        var closedInventory = event.getInventory();

        if (!(closedInventory instanceof Inventory craftingInventory) || craftingInventory.getInventoryType() != InventoryType.CRAFTING) {
            return Result.SUCCESS;
        }

        var workspace = this.craftingManager.workspace(closedInventory);

        if (workspace != null) {
            workspace.returnGridItems(event.getPlayer());
            this.craftingManager.unregisterInventory(closedInventory);
        }

        return Result.SUCCESS;
    }
}
