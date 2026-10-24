package rocks.minestom.crafting.listener;

import net.minestom.server.event.EventListener;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import rocks.minestom.crafting.CraftingManager;
import rocks.minestom.crafting.CraftingWorkspace;

public final class CraftingShiftClickListener implements EventListener<@NotNull InventoryPreClickEvent> {
    private final CraftingManager craftingManager;

    public CraftingShiftClickListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @Override
    public @NotNull Class<InventoryPreClickEvent> eventType() {
        return InventoryPreClickEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull InventoryPreClickEvent event) {
        var click = event.getClick();
        var workspace = this.craftingManager.workspace(event.getInventory());
        int slot = event.getSlot();

        if (workspace != null && slot == workspace.resultSlot()) {
            this.handleResultSlotClick(event, workspace, click);
            return Result.SUCCESS;
        }

        if (click instanceof Click.LeftShift || click instanceof Click.RightShift) {
            this.handlePlayerInventoryShift(event, (Click) click);
        }

        return Result.SUCCESS;
    }

    private void handleResultSlotClick(InventoryPreClickEvent event, CraftingWorkspace workspace, Click click) {
        if (click instanceof Click.LeftShift || click instanceof Click.RightShift) {
            this.handleShiftCraft(event, workspace);
            return;
        }

        if (click instanceof Click.Left) {
            this.handleResultPickup(event, workspace, false);
            return;
        }

        if (click instanceof Click.Right) {
            this.handleResultPickup(event, workspace, true);
            return;
        }

        event.setCancelled(true);
    }

    private void handleShiftCraft(InventoryPreClickEvent event, CraftingWorkspace workspace) {
        event.setCancelled(true);

        while (true) {
            var recipeMatch = workspace.currentMatch();

            if (recipeMatch == null) {
                break;
            }

            if (!event.getPlayer().getInventory().addItemStack(recipeMatch.result())) {
                break;
            }

            workspace.craft(event.getPlayer());
        }
    }

    private void handleResultPickup(InventoryPreClickEvent event, CraftingWorkspace workspace, boolean isRightClick) {
        event.setCancelled(true);
        var recipeMatch = workspace.currentMatch();

        if (recipeMatch == null) {
            return;
        }

        var resultStack = recipeMatch.result();

        if (resultStack.isAir()) {
            return;
        }

        var playerInventory = event.getPlayer().getInventory();
        var cursorItem = playerInventory.getCursorItem();
        int pickupAmount = isRightClick ? 1 : resultStack.amount();

        if (!this.canPickup(cursorItem, resultStack, pickupAmount)) {
            return;
        }

        if (cursorItem.isAir()) {
            playerInventory.setCursorItem(resultStack.withAmount(pickupAmount));
        } else {
            playerInventory.setCursorItem(cursorItem.withAmount(cursorItem.amount() + pickupAmount));
        }

        workspace.craft(event.getPlayer());
    }

    private boolean canPickup(ItemStack cursorItem, ItemStack resultStack, int pickupAmount) {
        if (pickupAmount <= 0) {
            return false;
        }

        if (cursorItem.isAir()) {
            return true;
        }

        if (!cursorItem.isSimilar(resultStack)) {
            return false;
        }

        return cursorItem.amount() + pickupAmount <= cursorItem.maxStackSize();
    }

    private void handlePlayerInventoryShift(InventoryPreClickEvent event, Click click) {
        var openInventory = event.getPlayer().getOpenInventory();

        if (openInventory == null) {
            return;
        }

        var openWorkspace = this.craftingManager.workspace(openInventory);

        if (openWorkspace == null) {
            return;
        }

        if (event.getInventory() != event.getPlayer().getInventory()) {
            return;
        }

        event.setCancelled(true);
        var button = click instanceof Click.RightShift ? 1 : 0;
        event.getPlayer().getInventory().shiftClick(event.getPlayer(), event.getSlot(), button);
    }
}
