package dev.oglass.vibenilla.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

class PlayerStorageTest {
    @Test
    void respawnRoundTripsWithItsDimensionInVanillaFormat() {
        var spawn = new Respawns.Spawn(WorldStorage.THE_NETHER, new Pos(10.5, 64 + 0.1, -3.5));
        var tag = PlayerStorage.respawnTag(spawn);

        assertEquals("minecraft:the_nether", tag.getString("dimension"));
        assertEquals(3, tag.getIntArray("pos").length);
        var data = CompoundBinaryTag.builder().put("respawn", tag).build();
        assertEquals(spawn, PlayerStorage.respawn(data));
        assertEquals(Key.key("minecraft:the_nether"), PlayerStorage.respawn(data).dimension());
    }

    @Test
    void missingRespawnReadsAsNone() {
        assertNull(PlayerStorage.respawn(CompoundBinaryTag.empty()));
    }
}
