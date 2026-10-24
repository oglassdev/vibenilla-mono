package net.goldenstack.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Random;
import net.goldenstack.loot.util.ListOperation;
import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.RelevantTarget;
import net.goldenstack.loot.util.VanillaInterface;
import net.goldenstack.loot.util.nbt.NBTPath;
import net.goldenstack.loot.util.nbt.NBTReference;
import net.goldenstack.loot.util.predicate.ItemPredicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.color.Color;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.predicate.DataComponentPredicates;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.potion.PotionType;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.RegistryTranscoder;
import net.minestom.server.registry.TagKey;
import org.junit.jupiter.api.Test;

class LootFunctionTest {

    @Test
    void filteredRunsTheMatchingBranch() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var predicate =
                new ItemPredicate(
                        null,
                        new LootNumberRange(
                                new LootNumber.Constant(1.0), new LootNumber.Constant(1.0)),
                        DataComponentPredicates.EMPTY);
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
    void enchantRandomlySkipsFailedConditions() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var item = ItemStack.of(Material.STONE);
        var predicate = new LootPredicate.AnyOf(List.of());
        var function = new LootFunction.EnchantRandomly(List.of(predicate), null, true, false);

        assertSame(item, function.apply(item, context));
    }

    @Test
    void enchantRandomlyChecksTheAdditionalCostFlagValue() {
        MinecraftServer.init();

        var function = new LootFunction.EnchantRandomly(List.of(), null, false, true);
        var item = ItemStack.of(Material.BOOK);

        var allowed =
                LootContext.from(
                        VanillaInterface.defaults(),
                        Map.of(
                                LootContext.RANDOM,
                                new Random(1L),
                                LootContext.ADDITIONAL_COST_COMPONENT_ALLOWED,
                                true));
        var denied =
                LootContext.from(
                        VanillaInterface.defaults(),
                        Map.of(
                                LootContext.RANDOM,
                                new Random(1L),
                                LootContext.ADDITIONAL_COST_COMPONENT_ALLOWED,
                                false));

        assertNotNull(function.apply(item, allowed).get(DataComponents.ADDITIONAL_TRADE_COST));
        assertNull(function.apply(item, denied).get(DataComponents.ADDITIONAL_TRADE_COST));
    }

    @Test
    void applyBonusSkipsFailedConditions() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var item = ItemStack.of(Material.STONE);
        var enchantment = RegistryKey.<Enchantment>of(Key.key("minecraft:fortune"));
        var formula =
                new LootFunction.ApplyBonus.Formula.Wrapper(
                        new LootFunction.ApplyBonus.Formula.OreDrops());
        var function =
                new LootFunction.ApplyBonus(
                        List.of(new LootPredicate.AnyOf(List.of())), enchantment, formula);

        assertSame(item, function.apply(item, context));
    }

    @Test
    void customModelDataAppliesListOperations() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var floats =
                new ListOperation.WithValues<LootNumber>(
                        new ListOperation.ReplaceAll(), List.of(new LootNumber.Constant(1.5)));
        var function = new LootFunction.SetCustomModelData(List.of(), floats, null, null, null);
        var result = function.apply(ItemStack.of(Material.STONE), context);

        assertEquals(List.of(1.5F), result.get(DataComponents.CUSTOM_MODEL_DATA).floats());
    }

    @Test
    void customModelDataColorsAcceptVanillaRgbVectors() throws Exception {
        MinecraftServer.init();

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  colors: {
                    mode: "replace_all",
                    values: [[1.0F, 0.5F, 0.0F]]
                  }
                }
                """);
        var transcoder = new RegistryTranscoder<>(Transcoder.NBT, MinecraftServer.process());
        var function =
                LootFunction.SetCustomModelData.CODEC.decode(transcoder, input).orElseThrow();
        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var result = function.apply(ItemStack.of(Material.STONE), context);
        var color = result.get(DataComponents.CUSTOM_MODEL_DATA).colors().getFirst();

        assertEquals(255, color.red());
        assertEquals(127, color.green());
        assertEquals(0, color.blue());
    }

    @Test
    void randomFunctionsWriteTheirComponents() {
        MinecraftServer.init();

        var context =
                LootContext.from(
                        VanillaInterface.defaults(), Map.of(LootContext.RANDOM, new Random(1L)));
        var dyed = new LootFunction.SetRandomDyes(List.of(), new LootNumber.Constant(1.0));
        var potion = new LootFunction.SetRandomPotion(List.of(), null);

        var dyedItem = dyed.apply(ItemStack.of(Material.LEATHER_HELMET, 3), context);
        var potionItem = potion.apply(ItemStack.of(Material.POTION), context);

        assertEquals(Color.class, dyedItem.get(DataComponents.DYED_COLOR).getClass());
        assertEquals(1, dyedItem.amount());
        assertNotNull(potionItem.get(DataComponents.POTION_CONTENTS).potion());
    }

    @Test
    void setNameUsesVanillaTargetNames() throws Exception {
        MinecraftServer.init();

        var serialized = MinestomAdventure.tagStringIO().asTag("\"item_name\"");
        var target =
                LootFunction.SetName.Target.CODEC.decode(Transcoder.NBT, serialized).orElseThrow();

        assertEquals(LootFunction.SetName.Target.ITEM_NAME, target);
        var encoded =
                LootFunction.SetName.Target.CODEC.encode(Transcoder.NBT, target).orElseThrow();
        assertEquals("item_name", ((StringBinaryTag) encoded).value());
    }

    @Test
    void containerFunctionsMatchVanillaStackHandling() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var setCount = new LootFunction.SetCount(List.of(), new LootNumber.Constant(70.0), false);
        var entry = new LootEntry.Item(List.of(), List.of(setCount), 1L, 0L, Material.STONE);
        var setContents =
                new LootFunction.SetContents(List.of(), List.of(entry), DataComponents.CONTAINER);
        var container = setContents.apply(ItemStack.of(Material.BUNDLE), context);
        var contents = container.get(DataComponents.CONTAINER);

        assertEquals(2, contents.size());
        assertEquals(Material.STONE, contents.get(0).material());
        assertEquals(64, contents.get(0).amount());
        assertEquals(Material.STONE, contents.get(1).material());
        assertEquals(6, contents.get(1).amount());

        var modifyContents =
                new LootFunction.ModifyContents(
                        List.of(),
                        List.of(
                                new LootFunction.SetCount(
                                        List.of(), new LootNumber.Constant(70.0), false)),
                        DataComponents.CONTAINER);
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

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  condition: [],
                  effects: [
                    {type: "minecraft:speed", duration: 1},
                    {type: "minecraft:speed", duration: 2}
                  ]
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> LootFunction.SetStewEffect.CODEC.decode(Transcoder.NBT, input).orElseThrow());
    }

    @Test
    void randomChanceWithEnchantedBonusRejectsChanceOutsideVanillaRange() throws Exception {
        MinecraftServer.init();

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  enchantment: "minecraft:looting",
                  unenchanted_chance: 1.1F,
                  enchanted_chance: 0.5F
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LootPredicate.RandomChanceWithEnchantedBonus.CODEC
                                .decode(Transcoder.NBT, input)
                                .orElseThrow());
    }

    @Test
    void setEnchantmentsConvertsBooksAndClampsLevels() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var enchantment = RegistryKey.<Enchantment>of(Key.key("minecraft:sharpness"));
        var function =
                new LootFunction.SetEnchantments(
                        List.of(), Map.of(enchantment, new LootNumber.Constant(300.0)), false);
        var result = function.apply(ItemStack.of(Material.BOOK), context);
        var enchantments = result.get(DataComponents.STORED_ENCHANTMENTS, EnchantmentList.EMPTY);

        assertEquals(Material.ENCHANTED_BOOK, result.material());
        assertEquals(255, enchantments.level(enchantment));
    }

    @Test
    void setAttributesRequiresVanillaSlotGroups() {
        MinecraftServer.init();

        var attribute = Attribute.values().iterator().next();
        var directive =
                new LootFunction.SetAttributes.AttributeDirective(
                        Key.key("minecraft:test"),
                        attribute,
                        AttributeOperation.ADD_VALUE,
                        new LootNumber.Constant(1.0),
                        List.of(EquipmentSlotGroup.HAND));

        assertEquals(List.of(EquipmentSlotGroup.HAND), directive.slots());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LootFunction.SetAttributes.AttributeDirective(
                                Key.key("minecraft:test"),
                                attribute,
                                AttributeOperation.ADD_VALUE,
                                new LootNumber.Constant(1.0),
                                List.of()));
    }

    @Test
    void setPotionReplacesAllPotionContents() {
        MinecraftServer.init();

        var potion = PotionType.values().iterator().next();
        var input =
                ItemStack.of(Material.POTION)
                        .with(
                                DataComponents.POTION_CONTENTS,
                                new PotionContents(
                                        potion, new Color(1, 2, 3), List.of(), "custom"));
        var function = new LootFunction.SetPotion(List.of(), potion.key());
        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var result = function.apply(input, context).get(DataComponents.POTION_CONTENTS);

        assertEquals(potion, result.potion());
        assertNull(result.customColor());
        assertEquals(List.of(), result.customEffects());
        assertNull(result.customName());
    }

    @Test
    void furnaceSmeltUsesConfiguredInputCount() {
        MinecraftServer.init();

        var defaults = VanillaInterface.defaults();
        var vanilla =
                new VanillaInterface() {
                    @Override
                    public Integer score(Entity entity, String objective) {
                        return defaults.score(entity, objective);
                    }

                    @Override
                    public Integer score(String name, String objective) {
                        return defaults.score(name, objective);
                    }

                    @Override
                    public BinaryTag serializeEntity(Entity entity) {
                        return CompoundBinaryTag.empty();
                    }

                    @Override
                    public ItemStack enchant(
                            Random random,
                            ItemStack item,
                            int levels,
                            RegistryTag<Enchantment> enchantments) {
                        return item;
                    }

                    @Override
                    public ItemStack smelt(ItemStack input) {
                        return ItemStack.of(Material.IRON_INGOT, 2);
                    }

                    @Override
                    public LootTable tableRegistry(Key key) {
                        return null;
                    }

                    @Override
                    public LootPredicate predicateRegistry(Key key) {
                        return null;
                    }

                    @Override
                    public LootFunction functionRegistry(Key key) {
                        return null;
                    }

                    @Override
                    public CompoundBinaryTag commandStorage(Key key) {
                        return null;
                    }
                };
        var context = LootContext.from(vanilla, Map.of());
        var input = ItemStack.of(Material.IRON_ORE, 3);

        var multiplied = new LootFunction.FurnaceSmelt(List.of(), true).apply(input, context);
        var single = new LootFunction.FurnaceSmelt(List.of(), false).apply(input, context);

        assertEquals(6, multiplied.amount());
        assertEquals(2, single.amount());
    }

    @Test
    void setCustomDataMergesNestedCompounds() {
        MinecraftServer.init();

        var current =
                CompoundBinaryTag.builder()
                        .put("keep", IntBinaryTag.intBinaryTag(1))
                        .put(
                                "nested",
                                CompoundBinaryTag.builder()
                                        .put("old", IntBinaryTag.intBinaryTag(2))
                                        .build())
                        .build();
        var changes =
                CompoundBinaryTag.builder()
                        .put(
                                "nested",
                                CompoundBinaryTag.builder()
                                        .put("new", IntBinaryTag.intBinaryTag(3))
                                        .build())
                        .build();
        var input =
                ItemStack.of(Material.STONE)
                        .with(DataComponents.CUSTOM_DATA, new CustomData(current));
        var function = new LootFunction.SetCustomData(List.of(), changes);
        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var result = function.apply(input, context).get(DataComponents.CUSTOM_DATA).nbt();

        assertEquals(current.get("keep"), result.get("keep"));
        assertEquals(
                IntBinaryTag.intBinaryTag(2),
                ((CompoundBinaryTag) result.get("nested")).get("old"));
        assertEquals(
                IntBinaryTag.intBinaryTag(3),
                ((CompoundBinaryTag) result.get("nested")).get("new"));
    }

    @Test
    void copyCustomDataMergeAccumulatesAllSourceCompounds() {
        MinecraftServer.init();

        var source =
                ListBinaryTag.builder()
                        .add(
                                CompoundBinaryTag.builder()
                                        .put("first", IntBinaryTag.intBinaryTag(1))
                                        .build())
                        .add(
                                CompoundBinaryTag.builder()
                                        .put("second", IntBinaryTag.intBinaryTag(2))
                                        .build())
                        .build();
        var operation =
                new LootFunction.CopyCustomData.Operation(
                        new NBTPath(List.of(new NBTPath.Selector.EntireList())),
                        new NBTPath(List.of(new NBTPath.Selector.RootKey("merged"))),
                        LootFunction.CopyCustomData.Operator.MERGE);
        var target = NBTReference.of(CompoundBinaryTag.empty());

        operation.execute(target, source);

        var merged = (CompoundBinaryTag) ((CompoundBinaryTag) target.get()).get("merged");
        assertEquals(IntBinaryTag.intBinaryTag(1), merged.get("first"));
        assertEquals(IntBinaryTag.intBinaryTag(2), merged.get("second"));
    }

    @Test
    void setComponentsAppliesAndRemovesComponents() {
        MinecraftServer.init();

        var changes =
                DataComponentMap.patchBuilder()
                        .set(DataComponents.MAX_STACK_SIZE, 1)
                        .remove(DataComponents.CUSTOM_NAME)
                        .build();
        var input =
                ItemStack.of(Material.STONE)
                        .with(
                                builder ->
                                        builder.set(DataComponents.MAX_STACK_SIZE, 64)
                                                .set(
                                                        DataComponents.CUSTOM_NAME,
                                                        Component.text("stone")));
        var function = new LootFunction.SetComponents(List.of(), changes);
        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var result = function.apply(input, context);

        assertEquals(1, result.get(DataComponents.MAX_STACK_SIZE));
        assertNull(result.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void copyNameClearsAnExistingNameWhenTheSourceHasNoName() {
        MinecraftServer.init();

        var entity = new Entity(EntityType.ZOMBIE);
        var input =
                ItemStack.of(Material.STONE)
                        .with(DataComponents.CUSTOM_NAME, Component.text("stone"));
        var function = new LootFunction.CopyName(List.of(), RelevantTarget.THIS);
        var context =
                LootContext.from(
                        VanillaInterface.defaults(), Map.of(LootContext.THIS_ENTITY, entity));

        var result = function.apply(input, context);

        assertNull(result.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void setFireworksPreservesUnsignedFlightDuration() {
        MinecraftServer.init();

        var function = new LootFunction.SetFireworks(List.of(), 255, null);
        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var result = function.apply(ItemStack.of(Material.FIREWORK_ROCKET), context);

        assertEquals(255, result.get(DataComponents.FIREWORKS).flightDuration());
    }

    @Test
    void containerFunctionCodecsUseVanillaFields() throws Exception {
        MinecraftServer.init();

        var setContentsInput =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  type: "set_contents",
                  condition: [],
                  component: "minecraft:container",
                  entries: []
                }
                """);
        var modifyContentsInput =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  type: "modify_contents",
                  condition: [],
                  component: "minecraft:container",
                  modifier: {
                    type: "set_count",
                    condition: [],
                    count: 1
                  }
                }
                """);
        var transcoder = new RegistryTranscoder<>(Transcoder.NBT, MinecraftServer.process());

        assertInstanceOf(
                LootFunction.SetContents.class,
                LootFunction.CODEC.decode(transcoder, setContentsInput).orElseThrow());
        assertInstanceOf(
                LootFunction.ModifyContents.class,
                LootFunction.CODEC.decode(transcoder, modifyContentsInput).orElseThrow());
    }

    @Test
    void explorationMapTargetsEmptyMaps() {
        MinecraftServer.init();

        var vanilla =
                new VanillaInterface() {
                    @Override
                    public Integer score(Entity entity, String objective) {
                        return null;
                    }

                    @Override
                    public Integer score(String name, String objective) {
                        return null;
                    }

                    @Override
                    public BinaryTag serializeEntity(Entity entity) {
                        return CompoundBinaryTag.empty();
                    }

                    @Override
                    public ItemStack enchant(
                            Random random,
                            ItemStack item,
                            int levels,
                            RegistryTag<Enchantment> enchantments) {
                        return item;
                    }

                    @Override
                    public ItemStack smelt(ItemStack input) {
                        return null;
                    }

                    @Override
                    public LootTable tableRegistry(Key key) {
                        return null;
                    }

                    @Override
                    public LootPredicate predicateRegistry(Key key) {
                        return null;
                    }

                    @Override
                    public LootFunction functionRegistry(Key key) {
                        return null;
                    }

                    @Override
                    public CompoundBinaryTag commandStorage(Key key) {
                        return null;
                    }

                    @Override
                    public ItemStack explorationMap(
                            ItemStack input,
                            Instance world,
                            Point origin,
                            TagKey<?> destination,
                            Key decoration,
                            byte zoom,
                            int searchRadius,
                            boolean skipExistingChunks) {
                        return ItemStack.of(Material.DIAMOND);
                    }
                };
        var world = MinecraftServer.getInstanceManager().createInstanceContainer();
        var context =
                LootContext.from(
                        vanilla,
                        Map.of(
                                LootContext.WORLD,
                                world,
                                LootContext.ORIGIN,
                                new Pos(0.0, 0.0, 0.0)));
        var function =
                new LootFunction.ExplorationMap(
                        List.of(),
                        TagKey.ofHash("#minecraft:on_treasure_maps"),
                        Key.key("minecraft:woodland_mansion"),
                        (byte) 2,
                        50,
                        true);

        assertEquals(
                Material.DIAMOND, function.apply(ItemStack.of(Material.MAP), context).material());
        assertEquals(
                Material.FILLED_MAP,
                function.apply(ItemStack.of(Material.FILLED_MAP), context).material());
    }

    @Test
    void setBookCoverEnforcesVanillaTitleLength() throws Exception {
        MinecraftServer.init();

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  condition: [],
                  title: {raw: "123456789012345678901234567890123"},
                  author: "author"
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> LootFunction.SetBookCover.CODEC.decode(Transcoder.NBT, input).orElseThrow());
    }

    @Test
    void setLootTableDecodesTheLootTableId() throws Exception {
        MinecraftServer.init();

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  loot_table_id: "minecraft:chests/simple_dungeon"
                }
                """);
        var function = LootFunction.SetLootTable.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertEquals(Key.key("minecraft:chests/simple_dungeon"), function.name());
    }

    @Test
    void setFireworkExplosionDecodesIntegerRgbColors() throws Exception {
        MinecraftServer.init();

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  colors: [1193046]
                }
                """);
        var function =
                LootFunction.SetFireworkExplosion.CODEC.decode(Transcoder.NBT, input).orElseThrow();
        var color = function.colors().getFirst();

        assertEquals(18, color.red());
        assertEquals(52, color.green());
        assertEquals(86, color.blue());
    }

    @Test
    void setDamageUsesVanillaFloatPrecision() {
        MinecraftServer.init();

        var input = ItemStack.of(Material.DIAMOND_SWORD);
        var function =
                new LootFunction.SetDamage(
                        List.of(), new LootNumber.Constant(0.2639333760409993), false);
        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var result = function.apply(input, context);

        assertEquals(1148, result.get(DataComponents.DAMAGE));
    }
}
