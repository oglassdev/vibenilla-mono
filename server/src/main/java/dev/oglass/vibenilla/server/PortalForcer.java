package dev.oglass.vibenilla.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import rocks.minestom.placement.PortalShape.Axis;

/**
 * Vanilla {@code PortalForcer}: finds the nether portal a traveller arrives through, or builds one.
 * Chunks load asynchronously; every block read and write runs on the executor passed to the entry
 * points, which must be the tick thread. Each continuation first asks whether the traveller still
 * wants the result and otherwise completes with null without touching the world.
 */
final class PortalForcer {
    static final BlockVec END_PLATFORM_CENTER = new BlockVec(100, 48, 0);

    private static final int NETHER_SEARCH_RADIUS = 16;
    private static final int OVERWORLD_SEARCH_RADIUS = 128;
    private static final int CREATE_RADIUS = 16;
    private static final int FRAME_MARGIN = 4;

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

    private static final CompletableFuture<Pos> UNWANTED = CompletableFuture.completedFuture(null);

    private record Request(
            Instance to,
            PortalIndex portals,
            int x,
            int y,
            int z,
            int radius,
            Pos position,
            Axis axis,
            BooleanSupplier wanted,
            Executor tick) {
        List<BlockVec> nearby() {
            return portals.nearby(x, y, z, radius);
        }
    }

    private PortalForcer() {}

    /**
     * Where a traveller at the position arrives in {@code to} through a portal of the axis. Call on
     * the tick thread; the result completes there too.
     */
    static CompletableFuture<Pos> netherExit(
            Instance from,
            Instance to,
            PortalIndex portals,
            Pos position,
            Axis axis,
            BooleanSupplier wanted,
            Executor tick) {
        var scale =
                from.getCachedDimensionType().coordinateScale()
                        / to.getCachedDimensionType().coordinateScale();
        var x = (int) Math.floor(position.x() * scale);
        var z = (int) Math.floor(position.z() * scale);
        var toNether = to.getDimensionType().key().equals(WorldStorage.THE_NETHER);
        var radius = toNether ? NETHER_SEARCH_RADIUS : OVERWORLD_SEARCH_RADIUS;
        var request =
                new Request(
                        to, portals, x, position.blockY(), z, radius, position, axis, wanted, tick);
        return find(request, request.nearby(), 0, new ArrayList<>());
    }

    /** Rebuilds the obsidian platform players arrive on and returns where they stand. */
    static CompletableFuture<Pos> buildEndPlatform(
            Instance end, BooleanSupplier wanted, Executor tick) {
        var center = END_PLATFORM_CENTER;
        return Chunks.loadAround(end, center.blockX(), center.blockZ(), 2)
                .thenApplyAsync(
                        ignored -> {
                            if (!wanted.getAsBoolean()) {
                                return null;
                            }
                            for (var x = center.blockX() - 2; x <= center.blockX() + 2; x++) {
                                for (var z = center.blockZ() - 2; z <= center.blockZ() + 2; z++) {
                                    for (var y = center.blockY() + 1;
                                            y <= center.blockY() + 3;
                                            y++) {
                                        end.setBlock(x, y, z, Block.AIR);
                                    }
                                    end.setBlock(x, center.blockY(), z, Block.OBSIDIAN);
                                }
                            }
                            return new Pos(
                                    center.blockX() + 0.5,
                                    center.blockY() + 1,
                                    center.blockZ() + 0.5);
                        },
                        tick);
    }

    /**
     * Walks the indexed portals closest first, loading one chunk at a time, until one is still
     * there. Those found missing on the way are dropped from the index in one write.
     */
    private static CompletableFuture<Pos> find(
            Request request, List<BlockVec> candidates, int next, List<BlockVec> stale) {
        if (next == candidates.size()) {
            request.portals().removeAll(stale);
            return build(request);
        }
        var to = request.to();
        var portal = candidates.get(next);
        return to.loadChunk(portal.blockX() >> 4, portal.blockZ() >> 4)
                .thenComposeAsync(
                        chunk -> {
                            if (!request.wanted().getAsBoolean()) {
                                return UNWANTED;
                            }
                            if (isPortal(to, portal.blockX(), portal.blockY(), portal.blockZ())) {
                                request.portals().removeAll(stale);
                                return CompletableFuture.completedFuture(
                                        frameAround(to, portal)
                                                .exit(request.position(), request.axis()));
                            }
                            stale.add(portal);
                            return find(request, candidates, next + 1, stale);
                        },
                        request.tick());
    }

    /** Loads the creation area and builds a portal there, unless one appeared meanwhile. */
    private static CompletableFuture<Pos> build(Request request) {
        var to = request.to();
        return Chunks.loadAround(to, request.x(), request.z(), CREATE_RADIUS + FRAME_MARGIN)
                .thenComposeAsync(
                        ignored -> {
                            if (!request.wanted().getAsBoolean()) {
                                return UNWANTED;
                            }
                            var candidates = request.nearby();
                            if (!candidates.isEmpty()) {
                                return find(request, candidates, 0, new ArrayList<>());
                            }
                            var frame =
                                    createPortal(
                                            to,
                                            request.portals(),
                                            request.x(),
                                            request.y(),
                                            request.z(),
                                            request.axis());
                            return CompletableFuture.completedFuture(
                                    frame.exit(request.position(), request.axis()));
                        },
                        request.tick());
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
            Instance instance,
            PortalIndex portals,
            int originX,
            int originY,
            int originZ,
            Axis axis) {
        var dimension = instance.getCachedDimensionType();
        var minY = dimension.minY();
        var maxY = Math.min(dimension.maxY() - 1, minY + dimension.logicalHeight() - 1);
        var dx = axis == Axis.X ? 1 : 0;
        var dz = 1 - dx;

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
        portals.add(site);
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

    private static long distanceSquared(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = x1 - x2;
        long dy = y1 - y2;
        long dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
