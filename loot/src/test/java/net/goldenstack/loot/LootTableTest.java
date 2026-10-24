package net.goldenstack.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.Either;
import org.junit.jupiter.api.Test;

class LootTableTest {

    private static final Key FIRST = Key.key("test:first");
    private static final Key SECOND = Key.key("test:second");

    @Test
    void cyclicPredicateReferencesFail() {
        MinecraftServer.init();

        var context =
                context(
                        Map.of(
                                FIRST,
                                new LootPredicate.Reference(SECOND),
                                SECOND,
                                new LootPredicate.Reference(FIRST)),
                        Map.of());

        assertFalse(new LootPredicate.Reference(FIRST).test(context));
    }

    @Test
    void cyclicTableReferencesGenerateNothing() {
        MinecraftServer.init();

        var table = singleEntryTable(reference(SECOND));
        var other = singleEntryTable(reference(FIRST));
        var context = context(Map.of(), Map.of(FIRST, table, SECOND, other));

        assertEquals(List.of(), table.generate(context));
    }

    @Test
    void generationSplitsOversizedStacksAndDropsEmptyOnes() {
        MinecraftServer.init();

        var setCount = new LootFunction.SetCount(List.of(), new LootNumber.Constant(100.0), false);
        var table =
                new LootTable(
                        List.of(
                                new LootPool(
                                        new LootNumber.Constant(1.0),
                                        new LootNumber.Constant(0.0),
                                        List.of(
                                                new LootEntry.Item(
                                                        List.of(),
                                                        List.of(setCount),
                                                        1L,
                                                        0L,
                                                        Material.STONE)),
                                        List.of(),
                                        List.of()),
                                new LootPool(
                                        new LootNumber.Constant(1.0),
                                        new LootNumber.Constant(0.0),
                                        List.of(
                                                new LootEntry.Item(
                                                        List.of(),
                                                        List.of(),
                                                        1L,
                                                        0L,
                                                        Material.DIRT)),
                                        List.of(),
                                        List.of(new LootFunction.Discard(List.of())))),
                        List.of(),
                        null);

        assertEquals(
                List.of(ItemStack.of(Material.STONE, 64), ItemStack.of(Material.STONE, 36)),
                table.generate(context(Map.of(), Map.of())));
    }

    private static LootTable singleEntryTable(LootEntry entry) {
        return new LootTable(
                List.of(
                        new LootPool(
                                new LootNumber.Constant(1.0),
                                new LootNumber.Constant(0.0),
                                List.of(entry),
                                List.of(),
                                List.of())),
                List.of(),
                null);
    }

    private static LootEntry reference(Key table) {
        return new LootEntry.LootTable(List.of(), List.of(), 1L, 0L, new Either.Left<>(table));
    }

    private static LootContext context(
            Map<Key, LootPredicate> predicates, Map<Key, LootTable> tables) {
        var defaults = VanillaInterface.defaults();
        var vanilla =
                (VanillaInterface)
                        Proxy.newProxyInstance(
                                VanillaInterface.class.getClassLoader(),
                                new Class<?>[] {VanillaInterface.class},
                                (proxy, method, arguments) ->
                                        switch (method.getName()) {
                                            case "predicateRegistry" ->
                                                    predicates.get(arguments[0]);
                                            case "tableRegistry" -> tables.get(arguments[0]);
                                            default -> method.invoke(defaults, arguments);
                                        });
        return LootContext.from(vanilla, Map.of(LootContext.RANDOM, new Random(1L)));
    }
}
