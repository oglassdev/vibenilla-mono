package rocks.minestom.crafting.listener;

import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.play.ClientPlaceRecipePacket;
import net.minestom.server.network.packet.client.play.ClientSetRecipeBookStatePacket;
import org.jetbrains.annotations.NotNull;
import rocks.minestom.crafting.CraftingManager;

public final class RecipeBookPacketListener implements EventListener<PlayerPacketEvent> {
    private final CraftingManager craftingManager;

    public RecipeBookPacketListener(CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
    }

    @Override
    public @NotNull Class<PlayerPacketEvent> eventType() {
        return PlayerPacketEvent.class;
    }

    @Override
    public @NotNull Result run(@NotNull PlayerPacketEvent event) {
        var player = event.getPlayer();

        switch (event.getPacket()) {
            case ClientSetRecipeBookStatePacket packet -> {
                this.craftingManager.recipeBookSettings(player, packet);
                event.setCancelled(true);
            }
            case ClientPlaceRecipePacket packet -> {
                if (this.craftingManager.placeRecipe(player, packet)) {
                    event.setCancelled(true);
                }
            }
            default -> {}
        }

        return Result.SUCCESS;
    }
}
