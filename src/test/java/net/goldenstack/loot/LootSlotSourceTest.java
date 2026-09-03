package net.goldenstack.loot;

import net.goldenstack.loot.util.VanillaInterface;
import net.minestom.server.MinecraftServer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootSlotSourceTest {

    @Test
    void readsIndexedContainerSlots() {
        MinecraftServer.init();

        var container = List.of(ItemStack.of(Material.STONE), ItemStack.of(Material.DIRT), ItemStack.AIR);
        var context = LootContext.from(VanillaInterface.defaults(), Map.of(LootContext.CONTAINER, container));
        var source = new LootSlotSource.Range("container", List.of(0, 2, 4));

        assertEquals(List.of(container.getFirst(), container.getLast()), source.provide(context));
    }

    @Test
    void slotsEntryAppliesFunctionsToNonEmptySlots() {
        MinecraftServer.init();

        var container = List.of(ItemStack.of(Material.STONE));
        var context = LootContext.from(VanillaInterface.defaults(), Map.of(LootContext.CONTAINER, container));
        var source = new LootSlotSource.Range("container", List.of(0));
        var entry = new LootEntry.Slots(
                List.of(),
                List.of(new LootFunction.SetCount(List.of(), new LootNumber.Constant(4.0), false)),
                1L,
                0L,
                source
        );

        assertEquals(4, entry.generate(context).getFirst().amount());
    }
}
