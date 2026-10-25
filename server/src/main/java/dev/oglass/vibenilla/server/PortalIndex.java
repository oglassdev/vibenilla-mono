package dev.oglass.vibenilla.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import org.jspecify.annotations.Nullable;

/**
 * Stand-in for vanilla's point of interest storage: one representative block per nether portal in a
 * dimension, persisted so a portal can be found without loading the chunks around it. Entries are
 * not removed when a portal breaks; whoever finds a stale one removes it. Not thread safe.
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

    void remove(BlockVec portal) {
        if (portals.remove(portal)) {
            write();
        }
    }

    /** The portal closest to the target within the square around it, lowest first on ties. */
    @Nullable BlockVec nearest(int x, int y, int z, int radius) {
        BlockVec best = null;
        var bestDistance = Long.MAX_VALUE;
        for (var portal : portals) {
            if (Math.abs(portal.blockX() - x) > radius || Math.abs(portal.blockZ() - z) > radius) {
                continue;
            }
            long dx = portal.blockX() - x;
            long dy = portal.blockY() - y;
            long dz = portal.blockZ() - z;
            var distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance
                    || distance == bestDistance && portal.blockY() < best.blockY()) {
                best = portal;
                bestDistance = distance;
            }
        }
        return best;
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
