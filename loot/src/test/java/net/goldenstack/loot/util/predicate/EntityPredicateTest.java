package net.goldenstack.loot.util.predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.GameProfile;
import org.junit.jupiter.api.Test;

class EntityPredicateTest {

    @Test
    void decodesModernEntityPredicateSchemas() throws Exception {
        MinecraftServer.init();

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  entity_type: "minecraft:zombie",
                  stepping_on: {},
                  movement_affected_by: {},
                  distance: {absolute: 4.0},
                  movement: {speed: 1.0},
                  effects: {},
                  nbt: {},
                  flags: {is_on_ground: true},
                  equipment: {},
                  periodic_tick: 20,
                  entity_tags: {},
                  location: {},
                  vehicle: {},
                  passenger: {},
                  targeted_entity: {},
                  team: "red",
                  slots: {},
                  components: {},
                  predicates: {},
                  "type_specific/sheep": {},
                  "type_specific/cube_mob": {},
                  "type_specific/fishing_hook": {},
                  "type_specific/player": {},
                  "type_specific/raider": {},
                  "type_specific/lightning": {}
                }
                """);

        var predicate = EntityPredicate.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertInstanceOf(EntityPredicate.Composite.class, predicate);
        assertTrue(((EntityPredicate.Composite) predicate).parts().size() >= 20);
    }

    @Test
    void decodesEntityTypeListsThroughTheVanillaHolderSetSchema() throws Exception {
        MinecraftServer.init();

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  entity_type: ["minecraft:zombie", "minecraft:skeleton"]
                }
                """);

        var predicate = EntityPredicate.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertTrue(predicate.test(null, null, new Entity(EntityType.ZOMBIE)));
    }

    @Test
    void playerInputChecksEverySpecifiedField() {
        MinecraftServer.init();

        var player = new Player(null, new GameProfile(UUID.randomUUID(), "test"));
        player.inputs().refresh(false, true, false, false, false, false, false);
        var predicate =
                new EntityPredicate.FishingHookInput(null, false, null, null, null, null, null);

        assertFalse(predicate.test(player));
    }

    @Test
    void fishingHookWithoutOpenWaterRequirementMatchesUnconditionally() {
        var predicate = new EntityPredicate.FishingHookPredicate(null);

        assertTrue(predicate.test(null, null, null));
    }

    @Test
    void entityFlagsIgnoreLivingOnlyFieldsForNonLivingEntities() {
        var predicate =
                new EntityPredicate.EntityFlagsPredicate(
                        null, null, null, null, null, null, true, null, true);
        var entity = new Entity(EntityType.ITEM);

        assertTrue(predicate.test(null, null, entity));
    }

    @Test
    void nestedEntityPredicatesPreserveVanillaInterface() {
        var vanilla = VanillaInterface.defaults();
        var nestedPredicate =
                new EntityPredicate() {
                    @Override
                    public boolean test(Instance instance, Point position, Entity entity) {
                        return false;
                    }

                    @Override
                    public boolean test(
                            Instance instance,
                            Point position,
                            Entity entity,
                            VanillaInterface activeVanilla) {
                        return activeVanilla == vanilla;
                    }
                };
        MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        var vehicle = new Entity(EntityType.ITEM);
        var passenger = new Entity(EntityType.ITEM);
        vehicle.setInstance(instance, new Pos(0.0, 0.0, 0.0)).join();
        vehicle.addPassenger(passenger);

        assertTrue(
                new EntityPredicate.EntityVehicle(nestedPredicate)
                        .test(instance, null, passenger, vanilla));
    }

    @Test
    void entityFlagsUseHostProvidedWaterState() {
        var vanilla =
                (VanillaInterface)
                        Proxy.newProxyInstance(
                                VanillaInterface.class.getClassLoader(),
                                new Class<?>[] {VanillaInterface.class},
                                (proxy, method, arguments) ->
                                        method.getName().equals("entityBoolean") ? true : null);
        var predicate =
                new EntityPredicate.EntityFlagsPredicate(
                        null, null, null, null, null, null, null, true, null);

        assertTrue(predicate.test(null, null, new Entity(EntityType.ITEM), vanilla));
    }

    @Test
    void playerPredicateUsesHostProvidedProgressionState() {
        MinecraftServer.init();

        var vanilla =
                (VanillaInterface)
                        Proxy.newProxyInstance(
                                VanillaInterface.class.getClassLoader(),
                                new Class<?>[] {VanillaInterface.class},
                                (proxy, method, arguments) ->
                                        method.getName().startsWith("matchesPlayer") ? true : null);
        var player = new Player(null, new GameProfile(UUID.randomUUID(), "test"));
        var range = new LootNumberRange(null, null);
        var rawValue = Codec.RawValue.of(Transcoder.NBT, CompoundBinaryTag.empty());
        var predicate =
                new EntityPredicate.PlayerPredicate(
                        range,
                        new EntityPredicate.FoodPredicate(range, range),
                        List.of("survival"),
                        null,
                        null,
                        List.of(rawValue),
                        Map.of("minecraft:test", true),
                        Map.of("minecraft:test", rawValue));

        assertTrue(predicate.test(null, null, player, vanilla));
    }
}
