package net.goldenstack.loot.util.predicate;

import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Transcoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityPredicateTest {

    @Test
    void decodesModernEntityPredicateSchemas() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("""
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
}
