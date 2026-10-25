package dev.oglass.vibenilla.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import net.minestom.server.coordinate.BlockVec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortalIndexTest {
    @Test
    void persistsAndFindsTheNearestPortal(@TempDir Path directory) {
        var file = directory.resolve("portals.txt");
        var index = new PortalIndex(file);
        index.add(new BlockVec(10, 64, 10));
        index.add(new BlockVec(-3, 70, 4));
        index.add(new BlockVec(200, 64, 0));

        var reloaded = new PortalIndex(file);
        assertEquals(new BlockVec(-3, 70, 4), reloaded.nearest(0, 64, 0, 16));
        assertNull(reloaded.nearest(100, 64, 0, 16));

        reloaded.remove(new BlockVec(-3, 70, 4));
        assertEquals(new BlockVec(10, 64, 10), new PortalIndex(file).nearest(0, 64, 0, 16));
    }
}
