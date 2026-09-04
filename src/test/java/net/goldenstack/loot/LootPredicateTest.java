package net.goldenstack.loot;

import net.goldenstack.loot.util.VanillaInterface;
import net.goldenstack.loot.util.RelevantEntity;
import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.predicate.DamageSourcePredicate;
import net.goldenstack.loot.util.predicate.EntityPredicate;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Instance;
import net.minestom.server.world.clock.WorldClock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void entityPropertiesPredicateIsOptionalInTheVanillaSchema() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("""
                {
                  "condition": "minecraft:entity_properties",
                  "entity": "this"
                }
                """);
        var predicate = LootPredicate.EntityProperties.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertEquals(new LootPredicate.EntityProperties(null, RelevantEntity.THIS), predicate);
    }

    @Test
    void matchToolPredicateIsOptionalInTheVanillaSchema() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("""
                {
                  "condition": "minecraft:match_tool"
                }
                """);
        var predicate = LootPredicate.MatchTool.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertEquals(new LootPredicate.MatchTool(null), predicate);
    }

    @Test
    void locationCheckPredicateIsOptionalInTheVanillaSchema() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("{}");
        var predicate = LootPredicate.LocationCheck.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertEquals(new LootPredicate.LocationCheck(null, 0, 0, 0), predicate);
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

    @Test
    void damageSourceEntityPredicatesReceiveTheVanillaInterface() {
        MinecraftServer.init();

        var world = MinecraftServer.getInstanceManager().createInstanceContainer();
        var entity = new Entity(EntityType.ITEM);
        var vanilla = VanillaInterface.defaults();
        var entityPredicate = new EntityPredicate() {
            @Override
            public boolean test(Instance instance, Point position, Entity entity) {
                return false;
            }

            @Override
            public boolean test(Instance instance, Point position, Entity entity, VanillaInterface activeVanilla) {
                return activeVanilla == vanilla;
            }
        };
        var predicate = new DamageSourcePredicate.Properties(List.of(), entityPredicate, null, null);
        var damageType = DamageType.create("test", "default", 0.0F, null, null);

        assertTrue(predicate.test(world, new Pos(0.0, 0.0, 0.0), damageType, entity, null, vanilla));
    }
}
