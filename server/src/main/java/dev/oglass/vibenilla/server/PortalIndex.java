package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;

/**
 * Stand-in for vanilla's point of interest storage: one representative block per nether portal in a
 * dimension, persisted so a portal can be found without loading the chunks around it. Entries are
 * not removed when a portal breaks; whoever finds stale ones removes them together. Not thread
 * safe.
 */
final class PortalIndex {
    private final Path file;
    private final Set<BlockVec> portals = new LinkedHashSet<>();

    PortalIndex(Path file) {
        this.file = file;
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (var line : Files.readAllLines(file)) {
                var parts = line.split(" ");
                portals.add(
                        new BlockVec(
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2])));
            }
        } catch (IOException | RuntimeException e) {
            MinecraftServer.LOGGER.error("Failed to read portal index {}", file, e);
            portals.clear();
        }
    }

    void add(BlockVec portal) {
        if (portals.add(portal)) {
            write();
        }
    }

    void removeAll(Collection<BlockVec> stale) {
        if (portals.removeAll(stale)) {
            write();
        }
    }

    /** The portals within the square around the target, closest first and lowest first on ties. */
    List<BlockVec> nearby(int x, int y, int z, int radius) {
        return portals.stream()
                .filter(
                        portal ->
                                Math.abs(portal.blockX() - x) <= radius
                                        && Math.abs(portal.blockZ() - z) <= radius)
                .sorted(
                        Comparator.comparingLong(
                                        (BlockVec portal) -> distanceSquared(portal, x, y, z))
                                .thenComparingInt(BlockVec::blockY))
                .toList();
    }

    private static long distanceSquared(BlockVec portal, int x, int y, int z) {
        long dx = portal.blockX() - x;
        long dy = portal.blockY() - y;
        long dz = portal.blockZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void write() {
        var lines =
                portals.stream()
                        .map(p -> p.blockX() + " " + p.blockY() + " " + p.blockZ())
                        .toList();
        var temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(temp, lines);
            AtomicFiles.replace(temp, file);
        } catch (IOException e) {
            MinecraftServer.LOGGER.error("Failed to write portal index {}", file, e);
        }
    }
}
