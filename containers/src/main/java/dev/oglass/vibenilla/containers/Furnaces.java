package dev.oglass.vibenilla.containers;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceChunkUnloadEvent;
import net.minestom.server.event.instance.InstanceUnregisterEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerGameModeChangeEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.BlockManager;
import rocks.minestom.crafting.CookingRecipes;

/** Vanilla furnaces, smokers and blast furnaces. */
public final class Furnaces {
    private Furnaces() {}

    /** Spawns experience orbs worth {@code points} in total. */
    @FunctionalInterface
    public interface ExperienceSpawner {
        void spawn(Instance instance, Point position, int points);
    }

    /**
     * Registers handlers for furnaces, smokers and blast furnaces, and wraps the placement rule
     * registered for each of them so that placed blocks use the new handler. Call this after the
     * placement rules are registered. Chunks loaded from disk get the handler through the {@code
     * minecraft:furnace}, {@code minecraft:smoker} and {@code minecraft:blast_furnace} block entity
     * ids, and furnaces that were burning resume when their chunk loads.
     *
     * @param experience spawns the orbs for the experience a furnace stored while smelting
     * @return the events that must be added to a running event handler, so viewers of a furnace are
     *     released when they disconnect or become spectators, furnaces in unloaded chunks or
     *     instances are closed, and clicks follow the furnace slot rules
     */
    public static EventNode<Event> register(
            BlockManager blockManager,
            CookingRecipes recipes,
            CookingFuels fuels,
            ExperienceSpawner experience) {
        var sessions = new FurnaceSessions(new Furnace.Cooking(recipes, fuels, experience));

        for (var kind : FurnaceKind.values()) {
            var handler = new FurnaceHandler(kind, sessions);
            blockManager.registerHandler(kind.key(), () -> handler);
            blockManager.registerBlockPlacementRule(
                    new ContainerPlacementRule(
                            kind.block(),
                            blockManager.getBlockPlacementRule(kind.block()),
                            handler));
        }

        return EventNode.all("furnaces")
                .addListener(
                        PlayerDisconnectEvent.class, event -> sessions.release(event.getPlayer()))
                .addListener(
                        PlayerGameModeChangeEvent.class,
                        event -> {
                            if (event.getNewGameMode() == GameMode.SPECTATOR) {
                                sessions.closeView(event.getPlayer());
                            }
                        })
                .addListener(InventoryPreClickEvent.class, sessions::preClick)
                .addListener(
                        InstanceChunkUnloadEvent.class,
                        event ->
                                sessions.closeChunk(
                                        event.getInstance(), event.getChunkX(), event.getChunkZ()))
                .addListener(
                        InstanceUnregisterEvent.class,
                        event -> sessions.closeInstance(event.getInstance()));
    }
}
