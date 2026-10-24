package rocks.minestom.worldgen.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.structure.template.StructureShapeUpdater;

/**
 * Bridges structure-phase block writes into the feature phase.
 *
 * <p>Structures place blocks before the chunk's decoration runs, but the decoration reads the
 * memoized terrain buffer which never saw those writes - vanilla ores would then "repair" mineshaft
 * corridors. Writes are recorded here against the chunk's surface-height array (the shared identity
 * handle both phases receive) and replayed into the terrain-backed accessor when the feature phase
 * begins, mirroring vanilla where features read the proto-chunk that already contains the structure
 * blocks.
 */
public final class StructureWrites {
    private static final Map<int[], List<Write>> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    // Leaves queued by structure placement, per generator (its terrain lookup identity) and per
    // chunk of the leaf. A structure's leaves commonly span several chunks, and each one only
    // settles correctly once every chunk touching that canopy (and whatever natural vegetation grew
    // alongside it) has been decorated, so every flush relaxes the pending leaves again with that
    // chunk's live cross-chunk view. Leaves are dropped once their whole 3x3 chunk neighborhood is
    // decorated.
    private static final Map<GenerationUnitAdapter.TerrainLookup, LeavesQueue> PENDING_LEAVES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile GenerationUnitAdapter.TerrainLookup terrainLookup;

    private StructureWrites() {}

    /**
     * Remembers the most recent terrain lookup seen by the feature phase, so structure placement
     * can read the same live chunk buffers (terrain + surface + carvers + margin writes from
     * previously decorated neighbors). Consumers must validate the lookup against their own chunk
     * handle since multiple generators may share this slot.
     */
    public static void captureTerrainLookup(GenerationUnitAdapter.TerrainLookup lookup) {
        if (lookup != null) {
            terrainLookup = lookup;
        }
    }

    public static GenerationUnitAdapter.TerrainLookup terrainLookup() {
        return terrainLookup;
    }

    // Vanilla marks the position above every placed magma_block / soul_sand
    // (Blocks::postProcessAbove) for chunk post-processing; at FULL the
    // water there ticks and BubbleColumnBlock.updateColumn converts the
    // source column. Keyed per generator (its terrain lookup identity) so
    // dimensions sharing chunk coordinates never cross.
    private static final Map<GenerationUnitAdapter.TerrainLookup, Map<Long, List<BlockVec>>>
            PENDING_POST_PROCESS = Collections.synchronizedMap(new WeakHashMap<>());

    public static void markPostProcess(
            GenerationUnitAdapter.TerrainLookup lookup, BlockVec position) {
        if (lookup == null) {
            return;
        }
        var chunkKey =
                (long) (position.blockX() >> 4) << 32 | ((position.blockZ() >> 4) & 0xFFFFFFFFL);
        PENDING_POST_PROCESS
                .computeIfAbsent(lookup, unused -> new java.util.concurrent.ConcurrentHashMap<>())
                .computeIfAbsent(
                        chunkKey, unused -> Collections.synchronizedList(new ArrayList<>()))
                .add(position);
    }

    public static java.util.Set<Long> postProcessChunkKeys(
            GenerationUnitAdapter.TerrainLookup lookup) {
        var byChunk = PENDING_POST_PROCESS.get(lookup);
        return byChunk != null ? java.util.Set.copyOf(byChunk.keySet()) : java.util.Set.of();
    }

    public static List<BlockVec> drainPostProcess(
            GenerationUnitAdapter.TerrainLookup lookup, long chunkKey) {
        var byChunk = PENDING_POST_PROCESS.get(lookup);
        if (byChunk == null) {
            return List.of();
        }
        var positions = byChunk.remove(chunkKey);
        if (positions == null) {
            return List.of();
        }
        synchronized (positions) {
            return List.copyOf(positions);
        }
    }

    public record Write(int x, int y, int z, Block block) {}

    public static void record(int[] chunkHandle, int x, int y, int z, Block block) {
        if (chunkHandle == null) {
            return;
        }
        PENDING.computeIfAbsent(
                        chunkHandle, unused -> Collections.synchronizedList(new ArrayList<>()))
                .add(new Write(x, y, z, block));
    }

    /**
     * Replays the chunk's recorded structure writes into the feature-phase accessor (terrain buffer
     * + generation unit) and clears them.
     */
    public static void replay(int[] chunkHandle, Block.Getter accessor) {
        if (chunkHandle == null || !(accessor instanceof GenerationUnitAdapter adapter)) {
            return;
        }

        var writes = PENDING.remove(chunkHandle);
        if (writes == null) {
            return;
        }

        synchronized (writes) {
            for (var write : writes) {
                adapter.setBlock(write.x(), write.y(), write.z(), write.block());
            }
        }
    }

    /**
     * Queues leaves placed by structure generation for a distance relaxation that has to wait until
     * this chunk's own vegetation decoration has run, mirroring vanilla settling leaves distance
     * through scheduled ticks that only fire once natural trees near the structure already exist.
     */
    public static void queueLeavesUpdate(
            GenerationUnitAdapter.TerrainLookup lookup,
            BlockTagManager blockTags,
            List<BlockVec> positions) {
        if (lookup == null || positions.isEmpty()) {
            return;
        }
        var queue = PENDING_LEAVES.computeIfAbsent(lookup, unused -> new LeavesQueue(blockTags));
        synchronized (queue) {
            for (var position : positions) {
                queue.byChunk
                        .computeIfAbsent(
                                chunkKey(position.blockX() >> 4, position.blockZ() >> 4),
                                unused -> new ArrayList<>())
                        .add(position);
            }
        }
    }

    /**
     * Re-relaxes every pending leaf (from this chunk and any other chunk a structure's canopy has
     * touched) against this chunk's live cross-chunk view, now that its own decoration has finished
     * planting whatever natural trees end up nearby, then drops the leaves that can no longer
     * change.
     */
    public static void flushLeavesUpdates(int[] chunkHandle, GenerationUnitAdapter level) {
        var lookup = level.terrainLookup();
        if (chunkHandle == null || lookup == null) {
            return;
        }
        var queue = PENDING_LEAVES.get(lookup);
        if (queue == null) {
            return;
        }

        List<BlockVec> seeds;
        synchronized (queue) {
            seeds = queue.byChunk.values().stream().flatMap(List::stream).toList();
            queue.byChunk.keySet().removeIf(chunkKey -> isNeighborhoodDecorated(lookup, chunkKey));
        }
        if (seeds.isEmpty()) {
            return;
        }

        var positions = StructureShapeUpdater.expandConnectedLeaves(level, seeds);
        StructureShapeUpdater.updateLeavesDistance(level, queue.blockTags, positions);
    }

    private static boolean isNeighborhoodDecorated(
            GenerationUnitAdapter.TerrainLookup lookup, long chunkKey) {
        var chunkX = (int) (chunkKey >> 32);
        var chunkZ = (int) chunkKey;
        for (var offsetX = -1; offsetX <= 1; offsetX++) {
            for (var offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (!Boolean.TRUE.equals(lookup.decorated(chunkX + offsetX, chunkZ + offsetZ))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
    }

    private static final class LeavesQueue {
        private final BlockTagManager blockTags;
        private final Map<Long, List<BlockVec>> byChunk = new HashMap<>();

        private LeavesQueue(BlockTagManager blockTags) {
            this.blockTags = blockTags;
        }
    }
}
