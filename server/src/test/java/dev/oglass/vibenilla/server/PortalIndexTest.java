package dev.oglass.vibenilla.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minestom.server.coordinate.BlockVec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortalIndexTest {
    @Test
    void persistsAndListsPortalsClosestFirst(@TempDir Path directory) {
        var file = directory.resolve("portals.txt");
        var index = new PortalIndex(file);
        index.add(new BlockVec(10, 64, 10));
        index.add(new BlockVec(-3, 70, 4));
        index.add(new BlockVec(200, 64, 0));

        var reloaded = new PortalIndex(file);
        assertEquals(
                List.of(new BlockVec(-3, 70, 4), new BlockVec(10, 64, 10)),
                reloaded.nearby(0, 64, 0, 16));
        assertTrue(reloaded.nearby(100, 64, 0, 16).isEmpty());
    }

    @Test
    void removesStaleEntriesTogether(@TempDir Path directory) throws Exception {
        var file = directory.resolve("portals.txt");
        var index = new PortalIndex(file);
        var stale = List.of(new BlockVec(1, 64, 1), new BlockVec(2, 64, 2));
        index.add(new BlockVec(0, 64, 0));
        stale.forEach(index::add);

        Files.delete(file);
        index.removeAll(stale);

        assertEquals(List.of("0 64 0"), Files.readAllLines(file));
        assertEquals(List.of(new BlockVec(0, 64, 0)), new PortalIndex(file).nearby(0, 64, 0, 16));
    }
}
