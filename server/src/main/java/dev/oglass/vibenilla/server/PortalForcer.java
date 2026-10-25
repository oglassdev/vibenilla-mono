package dev.oglass.vibenilla.server;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jspecify.annotations.Nullable;
import rocks.minestom.placement.PortalShape.Axis;

/**
 * Vanilla {@code PortalForcer}: finds the nether portal a traveller arrives through, or builds one.
 * Every method blocks on chunk loads and must not run on a tick thread.
 */
final class PortalForcer {
    static final BlockVec END_PLATFORM_CENTER = new BlockVec(100, 48, 0);

    private static final int NETHER_SEARCH_RADIUS = 16;
    private static final int OVERWORLD_SEARCH_RADIUS = 128;
    private static final int CREATE_RADIUS = 16;
    private static final int FRAME_MARGIN = 4;
    private static final int[] PORTAL_STATES = {
        Block.NETHER_PORTAL.stateId(), Block.NETHER_PORTAL.withProperty("axis", "z").stateId()
    };

    /** A portal's lowest row: {@code x} and {@code z} are its smallest coordinates. */
    private record Frame(Axis axis, int x, int y, int z, int width) {
        Pos exit(Pos entry, Axis entryAxis) {
            var alongX = axis == Axis.X;
            var yaw = entry.yaw() + (axis == entryAxis ? 0 : 90);
            return new Pos(
                    alongX ? x + width / 2.0 : x + 0.5,
                    y,
                    alongX ? z + 0.5 : z + width / 2.0,
                    yaw,
                    entry.pitch());
        }
    }

    private PortalForcer() {}

    /** Where a traveller at the position arrives in {@code to} through a portal of the axis. */
    static Pos netherExit(Instance from, Instance to, Pos position, Axis axis) {
        var scale =
                from.getCachedDimensionType().coordinateScale()
                        / to.getCachedDimensionType().coordinateScale();
        var x = (int) Math.floor(position.x() * scale);
        var z = (int) Math.floor(position.z() * scale);
        var toNether = to.getDimensionType().key().equals(WorldStorage.THE_NETHER);
        var radius = toNether ? NETHER_SEARCH_RADIUS : OVERWORLD_SEARCH_RADIUS;

        var portal = findClosestPortal(to, x, position.blockY(), z, radius);
        var frame =
                portal != null
                        ? frameAround(to, portal)
                        : createPortal(to, x, position.blockY(), z, axis);
        return frame.exit(position, axis);
    }

    /** Rebuilds the obsidian platform players arrive on and returns where they stand. */
    static Pos buildEndPlatform(Instance end) {
        var center = END_PLATFORM_CENTER;
        loadAround(end, center.blockX(), center.blockZ(), 2);
        for (var x = center.blockX() - 2; x <= center.blockX() + 2; x++) {
            for (var z = center.blockZ() - 2; z <= center.blockZ() + 2; z++) {
                for (var y = center.blockY() + 1; y <= center.blockY() + 3; y++) {
                    end.setBlock(x, y, z, Block.AIR);
                }
                end.setBlock(x, center.blockY(), z, Block.OBSIDIAN);
            }
        }
        return new Pos(center.blockX() + 0.5, center.blockY() + 1, center.blockZ() + 0.5);
    }

    /**
     * The portal block closest to the target within the square around it, lowest first on ties.
     * Only chunks already generated are read.
     */
    private static @Nullable BlockVec findClosestPortal(
            Instance instance, int x, int y, int z, int radius) {
        var loads = new ArrayList<CompletableFuture<Chunk>>();
        for (var chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++) {
            for (var chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++) {
                loads.add(instance.loadOptionalChunk(chunkX, chunkZ));
            }
        }

        BlockVec best = null;
        var bestDistance = Long.MAX_VALUE;
        for (var load : loads) {
            var chunk = load.join();
            if (chunk == null) {
                continue;
            }
            var sections = chunk.getSections();
            for (var index = 0; index < sections.size(); index++) {
                var palette = sections.get(index).blockPalette();
                if (!palette.any(PORTAL_STATES[0]) && !palette.any(PORTAL_STATES[1])) {
                    continue;
                }
                var baseY = (chunk.getMinSection() + index) << 4;
                for (var localX = 0; localX < 16; localX++) {
                    for (var localZ = 0; localZ < 16; localZ++) {
                        var worldX = (chunk.getChunkX() << 4) + localX;
                        var worldZ = (chunk.getChunkZ() << 4) + localZ;
                        if (Math.abs(worldX - x) > radius || Math.abs(worldZ - z) > radius) {
                            continue;
                        }
                        for (var localY = 0; localY < 16; localY++) {
                            var state = palette.get(localX, localY, localZ);
                            if (state != PORTAL_STATES[0] && state != PORTAL_STATES[1]) {
                                continue;
                            }
                            var worldY = baseY + localY;
                            var distance = distanceSquared(x, y, z, worldX, worldY, worldZ);
                            if (distance < bestDistance
                                    || distance == bestDistance && worldY < best.blockY()) {
                                best = new BlockVec(worldX, worldY, worldZ);
                                bestDistance = distance;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static Frame frameAround(Instance instance, BlockVec portal) {
        var axis = Axis.of(instance.getBlock(portal));
        var dx = axis == Axis.X ? 1 : 0;
        var dz = 1 - dx;
        var x = portal.blockX();
        var y = portal.blockY();
        var z = portal.blockZ();
        while (isPortal(instance, x, y - 1, z)) {
            y--;
        }
        while (isPortal(instance, x - dx, y, z - dz)) {
            x -= dx;
            z -= dz;
        }
        var width = 1;
        while (isPortal(instance, x + dx * width, y, z + dz * width)) {
            width++;
        }
        return new Frame(axis, x, y, z, width);
    }

    /** Vanilla {@code PortalForcer.createPortal}: a 2x3 portal on the best spot near the origin. */
    private static Frame createPortal(
            Instance instance, int originX, int originY, int originZ, Axis axis) {
        var dimension = instance.getCachedDimensionType();
        var minY = dimension.minY();
        var maxY = Math.min(dimension.maxY() - 1, minY + dimension.logicalHeight() - 1);
        var dx = axis == Axis.X ? 1 : 0;
        var dz = 1 - dx;
        loadAround(instance, originX, originZ, CREATE_RADIUS + FRAME_MARGIN);

        BlockVec best = null;
        var bestDistance = -1L;
        BlockVec fallback = null;
        var fallbackDistance = -1L;
        for (var radius = 0; radius <= CREATE_RADIUS; radius++) {
            for (var offsetX = -radius; offsetX <= radius; offsetX++) {
                for (var offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                        continue;
                    }
                    var x = originX + offsetX;
                    var z = originZ + offsetZ;
                    var chunk = instance.getChunk(x >> 4, z >> 4);
                    var top =
                            Math.min(
                                    maxY,
                                    chunk.motionBlockingHeightmap().getHeight(x & 15, z & 15) + 1);
                    for (var y = top; y >= minY; y--) {
                        if (!canReplace(instance, x, y, z)) {
                            continue;
                        }
                        var startY = y;
                        while (y > minY && canReplace(instance, x, y - 1, z)) {
                            y--;
                        }
                        var height = startY - y;
                        if (y + 4 > maxY || height > 0 && height < 3) {
                            continue;
                        }
                        if (!canHostFrame(instance, x, y, z, dx, dz, 0)) {
                            continue;
                        }
                        var distance = distanceSquared(originX, originY, originZ, x, y, z);
                        if (canHostFrame(instance, x, y, z, dx, dz, -1)
                                && canHostFrame(instance, x, y, z, dx, dz, 1)
                                && (bestDistance == -1 || distance < bestDistance)) {
                            best = new BlockVec(x, y, z);
                            bestDistance = distance;
                        }
                        if (bestDistance == -1
                                && (fallbackDistance == -1 || distance < fallbackDistance)) {
                            fallback = new BlockVec(x, y, z);
                            fallbackDistance = distance;
                        }
                    }
                }
            }
        }

        var site = best != null ? best : fallback;
        if (site == null) {
            site = clearSite(instance, originX, originY, originZ, dx, dz, minY, maxY);
        }
        buildFrame(instance, site, axis, dx, dz);
        return new Frame(axis, site.blockX(), site.blockY(), site.blockZ(), 2);
    }

    /** Last resort: a platform and a cleared pocket next to the origin, clear of the surface. */
    private static BlockVec clearSite(
            Instance instance,
            int originX,
            int originY,
            int originZ,
            int dx,
            int dz,
            int minY,
            int maxY) {
        var lowest = Math.max(minY + 1, 70);
        var highest = maxY - 9;
        var site =
                new BlockVec(
                        originX - dx,
                        Math.clamp(originY, lowest, Math.max(lowest, highest)),
                        originZ - dz);
        var clockwiseX = -dz;
        var clockwiseZ = dx;
        for (var side = -1; side < 2; side++) {
            for (var width = 0; width < 2; width++) {
                for (var h = -1; h < 3; h++) {
                    instance.setBlock(
                            site.blockX() + width * dx + side * clockwiseX,
                            site.blockY() + h,
                            site.blockZ() + width * dz + side * clockwiseZ,
                            h < 0 ? Block.OBSIDIAN : Block.AIR);
                }
            }
        }
        return site;
    }

    private static void buildFrame(Instance instance, BlockVec site, Axis axis, int dx, int dz) {
        for (var width = -1; width < 3; width++) {
            for (var h = -1; h < 4; h++) {
                if (width == -1 || width == 2 || h == -1 || h == 3) {
                    instance.setBlock(
                            site.blockX() + width * dx,
                            site.blockY() + h,
                            site.blockZ() + width * dz,
                            Block.OBSIDIAN);
                }
            }
        }
        var portal = Block.NETHER_PORTAL.withProperty("axis", axis.property());
        for (var width = 0; width < 2; width++) {
            for (var h = 0; h < 3; h++) {
                instance.setBlock(
                        new BlockVec(
                                site.blockX() + width * dx,
                                site.blockY() + h,
                                site.blockZ() + width * dz),
                        portal,
                        false);
            }
        }
    }

    private static boolean canHostFrame(
            Instance instance, int x, int y, int z, int dx, int dz, int side) {
        var clockwiseX = -dz;
        var clockwiseZ = dx;
        for (var width = -1; width < 3; width++) {
            for (var h = -1; h < 4; h++) {
                var px = x + dx * width + clockwiseX * side;
                var pz = z + dz * width + clockwiseZ * side;
                if (h < 0
                        ? !block(instance, px, y + h, pz).solid()
                        : !canReplace(instance, px, y + h, pz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canReplace(Instance instance, int x, int y, int z) {
        var block = block(instance, x, y, z);
        return (block.air() || block.replaceable())
                && !block.liquid()
                && !"true".equals(block.getProperty("waterlogged"));
    }

    private static boolean isPortal(Instance instance, int x, int y, int z) {
        return block(instance, x, y, z).compare(Block.NETHER_PORTAL);
    }

    /** Unloaded and out of world positions read as barriers. */
    private static Block block(Instance instance, int x, int y, int z) {
        var dimension = instance.getCachedDimensionType();
        if (y < dimension.minY()
                || y >= dimension.maxY()
                || !instance.isChunkLoaded(x >> 4, z >> 4)) {
            return Block.BARRIER;
        }
        return instance.getBlock(x, y, z);
    }

    private static void loadAround(Instance instance, int x, int z, int radius) {
        var loads = new ArrayList<CompletableFuture<Chunk>>();
        for (var chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++) {
            for (var chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++) {
                loads.add(instance.loadChunk(chunkX, chunkZ));
            }
        }
        loads.forEach(CompletableFuture::join);
    }

    private static long distanceSquared(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = x1 - x2;
        long dy = y1 - y2;
        long dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
