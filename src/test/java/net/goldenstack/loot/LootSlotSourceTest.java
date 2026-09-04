package net.goldenstack.loot;

import net.goldenstack.loot.util.VanillaInterface;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.GameProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Test
    void decodesVanillaSlotRangeNames() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("""
                {
                  "source": "container",
                  "slots": "hotbar.*"
                }
                """);
        var source = LootSlotSource.Range.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), source.slots());
    }

    @Test
    void readsVanillaPlayerInventoryAndEquipmentSlots() {
        MinecraftServer.init();

        var player = new Player(null, new GameProfile(UUID.randomUUID(), "test"));
        player.getInventory().setItemStack(0, ItemStack.of(Material.STONE));
        player.setHelmet(ItemStack.of(Material.DIAMOND));
        player.getInventory().setCursorItem(ItemStack.of(Material.DIRT));
        var context = LootContext.from(VanillaInterface.defaults(), Map.of(LootContext.THIS_ENTITY, player));
        var source = new LootSlotSource.Range("this", List.of(0, 103, 499));

        assertEquals(
                List.of(Material.STONE, Material.DIAMOND, Material.DIRT),
                source.provide(context).stream().map(ItemStack::material).toList()
        );
    }
}
