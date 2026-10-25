package dev.oglass.vibenilla.server;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;

/** Chunk preloading, so that work on the tick thread never has to wait for a chunk. */
final class Chunks {
    private Chunks() {}

    /** Runs tasks on the player's next tick. */
    static Executor tick(Player player) {
        return task -> player.scheduleNextTick(entity -> task.run());
    }

    /** Loads the chunks within the block radius of the position. */
    static CompletableFuture<Void> loadAround(Instance instance, int x, int z, int radius) {
        var loads = new ArrayList<CompletableFuture<Chunk>>();
        for (var chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++) {
            for (var chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++) {
                loads.add(instance.loadChunk(chunkX, chunkZ));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    /** The chunk distance {@code Player.setInstance} waits for when the player arrives. */
    private static int viewRange(Player player, Instance instance) {
        return Math.min(player.getSettings().viewDistance(), instance.viewDistance()) + 1;
    }

    /** Whether {@link #loadView} would have nothing left to load. */
    static boolean isViewLoaded(Player player, Instance instance, Point position) {
        var loaded = new AtomicBoolean(true);
        ChunkRange.chunksInRange(
                position,
                viewRange(player, instance),
                (x, z) -> {
                    if (!instance.isChunkLoaded(x, z)) {
                        loaded.set(false);
                    }
                });
        return loaded.get();
    }

    /**
     * Loads the chunks {@code Player.setInstance} waits for when the player arrives at the
     * position, so that it can move them without loading anything.
     */
    static CompletableFuture<Void> loadView(Player player, Instance instance, Point position) {
        var loads = new ArrayList<CompletableFuture<Chunk>>();
        ChunkRange.chunksInRange(
                position,
                viewRange(player, instance),
                (x, z) -> loads.add(instance.loadChunk(x, z)));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }
}
