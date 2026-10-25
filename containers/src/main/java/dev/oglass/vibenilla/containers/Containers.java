package dev.oglass.vibenilla.containers;

import java.util.function.Function;
import net.goldenstack.loot.LootTable;
import net.goldenstack.loot.Trove;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceChunkUnloadEvent;
import net.minestom.server.event.instance.InstanceUnregisterEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerGameModeChangeEvent;
import net.minestom.server.instance.block.BlockManager;

/** Vanilla chests, copper chests, trapped chests and barrels. */
public final class Containers {
    private Containers() {}

    /**
     * Registers the container handlers, see {@link #register(BlockManager, Function)}. Loot tables
     * are looked up in {@link Trove}.
     */
    public static EventNode<Event> register(BlockManager blockManager) {
        return register(blockManager, Trove::table);
    }

    /**
     * Registers handlers for chests, trapped chests and barrels, and wraps the placement rule
     * registered for each of them and for copper chests, which share the chest handler, so that
     * placed blocks use the new handler instead of any placeholder. Call this after the placement
     * rules are registered. Chunks loaded from disk get the handler through the {@code
     * minecraft:chest}, {@code minecraft:trapped_chest} and {@code minecraft:barrel} block entity
     * ids.
     *
     * @param lootTables resolves the {@code LootTable} of containers that have not been opened yet
     * @return the events that must be added to a running event handler, so viewers of a container
     *     are released when they disconnect or become spectators and containers in unloaded chunks
     *     or instances are closed
     */
    public static EventNode<Event> register(
            BlockManager blockManager, Function<Key, LootTable> lootTables) {
        var sessions = new Sessions(lootTables);

        for (var kind : ContainerKind.values()) {
            var handler = new ContainerHandler(kind, sessions);
            blockManager.registerHandler(kind.key(), () -> handler);

            for (var block : kind.blocks()) {
                blockManager.registerBlockPlacementRule(
                        new ContainerPlacementRule(
                                block, blockManager.getBlockPlacementRule(block), handler));
            }
        }

        return EventNode.all("containers")
                .addListener(
                        PlayerDisconnectEvent.class, event -> sessions.release(event.getPlayer()))
                .addListener(
                        PlayerGameModeChangeEvent.class,
                        event -> {
                            if (event.getNewGameMode() == GameMode.SPECTATOR) {
                                sessions.closeView(event.getPlayer());
                            }
                        })
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
