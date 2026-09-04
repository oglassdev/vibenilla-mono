package net.goldenstack.loot;

import net.goldenstack.loot.util.VanillaInterface;
import net.minestom.server.MinecraftServer;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LootPoolTest {

    @Test
    void excludesEntriesWithNonPositiveEffectiveWeights() {
        MinecraftServer.init();

        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.LUCK, 1.0, LootContext.RANDOM, new Random(1L))
        );
        var entry = new LootEntry.Item(List.of(), List.of(), 1L, -1L, Material.STONE);

        assertNull(LootPool.pickChoice(List.of(entry), context));
    }

    @Test
    void doesNotDrawRandomnessForOnePositiveChoice() {
        MinecraftServer.init();

        var random = new Random(1L);
        var expectedRandom = new Random(1L);
        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.LUCK, 0.0, LootContext.RANDOM, random)
        );
        var entry = new LootEntry.Item(List.of(), List.of(), 1L, 0L, Material.STONE);

        assertNotNull(LootPool.pickChoice(List.of(entry), context));
        assertEquals(expectedRandom.nextInt(), random.nextInt());
    }
}
