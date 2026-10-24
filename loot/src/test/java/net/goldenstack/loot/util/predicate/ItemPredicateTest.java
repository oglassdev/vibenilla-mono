package net.goldenstack.loot.util.predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.goldenstack.loot.LootContext;
import net.goldenstack.loot.LootNumber;
import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.instance.block.predicate.ComponentPredicateSet;
import net.minestom.server.instance.block.predicate.DataComponentPredicates;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

class ItemPredicateTest {

    @Test
    void matchesCountAndComponents() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var predicate =
                new ItemPredicate(
                        null,
                        new LootNumberRange(
                                new LootNumber.Constant(1.0), new LootNumber.Constant(1.0)),
                        new DataComponentPredicates(
                                DataComponentMap.builder()
                                        .set(DataComponents.CUSTOM_NAME, Component.text("stone"))
                                        .build(),
                                ComponentPredicateSet.EMPTY));

        var matchingItem =
                ItemStack.of(Material.STONE)
                        .with(DataComponents.CUSTOM_NAME, Component.text("stone"));
        var nonMatchingItem = ItemStack.of(Material.STONE);

        assertTrue(predicate.test(matchingItem, context));
        assertFalse(predicate.test(nonMatchingItem, context));
    }
}
