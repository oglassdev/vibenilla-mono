package net.goldenstack.loot;

import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.VanillaInterface;
import net.goldenstack.loot.util.ListOperation;
import net.goldenstack.loot.util.predicate.ItemPredicate;
import net.minestom.server.MinecraftServer;
import net.minestom.server.color.Color;
import net.minestom.server.component.DataComponents;
import net.minestom.server.instance.block.predicate.DataComponentPredicates;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LootFunctionTest {

    @Test
    void filteredRunsTheMatchingBranch() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var predicate = new ItemPredicate(
                null,
                new LootNumberRange(new LootNumber.Constant(1.0), new LootNumber.Constant(1.0)),
                DataComponentPredicates.EMPTY
        );
        var onPass = new LootFunction.SetCount(List.of(), new LootNumber.Constant(3.0), false);
        var onFail = new LootFunction.SetCount(List.of(), new LootNumber.Constant(4.0), false);
        var function = new LootFunction.Filtered(List.of(), predicate, onPass, onFail);

        assertEquals(3, function.apply(ItemStack.of(Material.STONE), context).amount());
        assertEquals(4, function.apply(ItemStack.of(Material.STONE, 2), context).amount());
    }

    @Test
    void discardReturnsAirOnlyWhenItsConditionsPass() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var function = new LootFunction.Discard(List.of());
        var item = ItemStack.of(Material.STONE);

        assertSame(ItemStack.AIR, function.apply(item, context));
    }

    @Test
    void customModelDataAppliesListOperations() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var floats = new ListOperation.WithValues<LootNumber>(
                new ListOperation.ReplaceAll(), List.of(new LootNumber.Constant(1.5))
        );
        var function = new LootFunction.SetCustomModelData(List.of(), floats, null, null, null);
        var result = function.apply(ItemStack.of(Material.STONE), context);

        assertEquals(List.of(1.5F), result.get(DataComponents.CUSTOM_MODEL_DATA).floats());
    }

    @Test
    void randomFunctionsWriteTheirComponents() {
        MinecraftServer.init();

        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.RANDOM, new Random(1L))
        );
        var dyed = new LootFunction.SetRandomDyes(List.of(), new LootNumber.Constant(1.0));
        var potion = new LootFunction.SetRandomPotion(List.of(), null);

        var dyedItem = dyed.apply(ItemStack.of(Material.LEATHER_HELMET), context);
        var potionItem = potion.apply(ItemStack.of(Material.POTION), context);

        assertEquals(Color.class, dyedItem.get(DataComponents.DYED_COLOR).getClass());
        assertNotNull(potionItem.get(DataComponents.POTION_CONTENTS).potion());
    }
}
