package rocks.minestom.crafting.listener;

import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import rocks.minestom.crafting.CraftingManager;

public final class CraftingTableListener implements EventListener<@NotNull PlayerBlockInteractEvent> {
    private final CraftingManager craftingManager;

    public CraftingTableListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @Override
    public @NotNull Class<PlayerBlockInteractEvent> eventType() {
        return PlayerBlockInteractEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull PlayerBlockInteractEvent event) {
        if (event.getHand() != PlayerHand.MAIN) {
            return Result.SUCCESS;
        }

        if (event.getPlayer().isSneaking()) {
            return Result.SUCCESS;
        }

        if (event.getBlock() != Block.CRAFTING_TABLE) {
            return Result.SUCCESS;
        }

        event.setBlockingItemUse(true);
        var craftingInventory = this.craftingManager.openCraftingTable(event.getPlayer());
        event.getPlayer().openInventory(craftingInventory);
        return Result.SUCCESS;
    }
}
