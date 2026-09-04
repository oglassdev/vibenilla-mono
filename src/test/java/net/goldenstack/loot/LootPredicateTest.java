package net.goldenstack.loot;

import net.goldenstack.loot.util.VanillaInterface;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootPredicateTest {

    @Test
    void randomChanceUsesVanillaFloatRandomness() {
        MinecraftServer.init();

        var random = new Random(1L);
        var expectedRandom = new Random(1L);
        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.RANDOM, random)
        );
        var predicate = new LootPredicate.RandomChance(new LootNumber.Constant(1.0));

        predicate.test(context);
        expectedRandom.nextFloat();

        assertEquals(expectedRandom.nextFloat(), random.nextFloat());
    }
}
