package rocks.minestom.crafting.listener;

import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import org.jetbrains.annotations.NotNull;
import rocks.minestom.crafting.CraftingManager;

public final class CraftingPlayerDisconnectListener implements EventListener<@NotNull PlayerDisconnectEvent> {
    private final CraftingManager craftingManager;

    public CraftingPlayerDisconnectListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @Override
    public @NotNull Class<PlayerDisconnectEvent> eventType() {
        return PlayerDisconnectEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull PlayerDisconnectEvent event) {
        this.craftingManager.unregisterPlayer(event.getPlayer());
        return Result.SUCCESS;
    }
}
