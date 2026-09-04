package net.goldenstack.loot;

import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.VanillaInterface;
import net.goldenstack.loot.util.ListOperation;
import net.goldenstack.loot.util.predicate.ItemPredicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.color.Color;
import net.minestom.server.component.DataComponents;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.predicate.DataComponentPredicates;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void setNameUsesVanillaTargetNames() throws Exception {
        MinecraftServer.init();

        var serialized = MinestomAdventure.tagStringIO().asTag("\"item_name\"");
        var target = LootFunction.SetName.Target.CODEC.decode(Transcoder.NBT, serialized).orElseThrow();

        assertEquals(LootFunction.SetName.Target.ITEM_NAME, target);
        var encoded = LootFunction.SetName.Target.CODEC.encode(Transcoder.NBT, target).orElseThrow();
        assertEquals("item_name", ((StringBinaryTag) encoded).value());
    }

    @Test
    void containerFunctionsMatchVanillaStackHandling() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var setCount = new LootFunction.SetCount(List.of(), new LootNumber.Constant(70.0), false);
        var entry = new LootEntry.Item(List.of(), List.of(setCount), 1L, 0L, Material.STONE);
        var setContents = new LootFunction.SetContents(List.of(), List.of(entry), DataComponents.CONTAINER);
        var container = setContents.apply(ItemStack.of(Material.BUNDLE), context);
        var contents = container.get(DataComponents.CONTAINER);

        assertEquals(2, contents.size());
        assertEquals(Material.STONE, contents.get(0).material());
        assertEquals(64, contents.get(0).amount());
        assertEquals(Material.STONE, contents.get(1).material());
        assertEquals(6, contents.get(1).amount());

        var modifyContents = new LootFunction.ModifyContents(
                List.of(), List.of(new LootFunction.SetCount(List.of(), new LootNumber.Constant(70.0), false)), DataComponents.CONTAINER
        );
        var modified = modifyContents.apply(container, context).get(DataComponents.CONTAINER);

        assertEquals(2, modified.size());
        assertEquals(Material.STONE, modified.get(0).material());
        assertEquals(64, modified.get(0).amount());
        assertEquals(Material.STONE, modified.get(1).material());
        assertEquals(64, modified.get(1).amount());
    }

    @Test
    void setStewEffectRejectsDuplicateEffects() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("""
                {
                  conditions: [],
                  effects: [
                    {type: "minecraft:speed", duration: 1},
                    {type: "minecraft:speed", duration: 2}
                  ]
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> LootFunction.SetStewEffect.CODEC.decode(Transcoder.NBT, input).orElseThrow());
    }

    @Test
    void randomChanceWithEnchantedBonusRejectsChanceOutsideVanillaRange() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("""
                {
                  enchantment: "minecraft:looting",
                  unenchanted_chance: 1.1F,
                  enchanted_chance: 0.5F
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> LootPredicate.RandomChanceWithEnchantedBonus.CODEC.decode(Transcoder.NBT, input).orElseThrow());
    }

    @Test
    void setEnchantmentsConvertsBooksAndClampsLevels() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var enchantment = RegistryKey.<Enchantment>unsafeOf(Key.key("minecraft:sharpness"));
        var function = new LootFunction.SetEnchantments(
                List.of(), Map.of(enchantment, new LootNumber.Constant(300.0)), false
        );
        var result = function.apply(ItemStack.of(Material.BOOK), context);
        var enchantments = result.get(DataComponents.STORED_ENCHANTMENTS, EnchantmentList.EMPTY);

        assertEquals(Material.ENCHANTED_BOOK, result.material());
        assertEquals(255, enchantments.level(enchantment));
    }
}
