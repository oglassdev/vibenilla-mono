package rocks.minestom.crafting.listener;

import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.jetbrains.annotations.NotNull;
import rocks.minestom.crafting.CraftingManager;

public final class CraftingPlayerSpawnListener implements EventListener<@NotNull PlayerSpawnEvent> {
    private final CraftingManager craftingManager;

    public CraftingPlayerSpawnListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @Override
    public @NotNull Class<PlayerSpawnEvent> eventType() {
        return PlayerSpawnEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull PlayerSpawnEvent event) {
        if (event.isFirstSpawn()) {
            this.craftingManager.registerPlayer(event.getPlayer());
        }

        return Result.SUCCESS;
    }
}
