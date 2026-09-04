package net.goldenstack.loot;

import net.goldenstack.loot.util.VanillaInterface;
import net.goldenstack.loot.util.RelevantEntity;
import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.predicate.DamageSourcePredicate;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.world.clock.WorldClock;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void optionalPredicatesOnlyRequireVanillaContextValuesUsedByVanilla() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of(
                LootContext.ORIGIN, new Pos(0.0, 0.0, 0.0),
                LootContext.DAMAGE_SOURCE, DamageType.create("test", "default", 0.0F, null, null)
        ));

        assertEquals(true, new LootPredicate.EntityProperties(null, RelevantEntity.THIS).test(context));
        assertEquals(true, new LootPredicate.DamageSourceProperties(null).test(context));
    }

    @Test
    void damageSourcePredicateCodecInitializesOnFirstUse() {
        assertNotNull(DamageSourcePredicate.CODEC);
    }

    @Test
    void timeCheckUsesTheConfiguredWorldClock() {
        MinecraftServer.init();

        var world = MinecraftServer.getInstanceManager().createInstanceContainer();
        world.setTime(WorldClock.OVERWORLD, 42L);
        var context = LootContext.from(VanillaInterface.defaults(), Map.of(LootContext.WORLD, world));
        var range = new LootNumberRange(new LootNumber.Constant(42.0), new LootNumber.Constant(42.0));

        assertEquals(true, new LootPredicate.TimeCheck(WorldClock.OVERWORLD, null, range).test(context));
    }
}
