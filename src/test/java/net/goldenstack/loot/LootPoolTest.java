package net.goldenstack.loot;

import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LootPoolTest {

    @Test
    void excludesEntriesWithNonPositiveEffectiveWeights() {
        MinecraftServer.init();

        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.LUCK, 1.0, LootContext.RANDOM, new Random(1L))
        );
        var entry = new LootEntry.Item(List.of(), List.of(), 1L, -1L, Material.STONE);

        assertNull(LootPool.pickChoice(List.of(entry), context));
    }

    @Test
    void doesNotDrawRandomnessForOnePositiveChoice() {
        MinecraftServer.init();

        var random = new Random(1L);
        var expectedRandom = new Random(1L);
        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.LUCK, 0.0, LootContext.RANDOM, random)
        );
        var entry = new LootEntry.Item(List.of(), List.of(), 1L, 0L, Material.STONE);

        assertNotNull(LootPool.pickChoice(List.of(entry), context));
        assertEquals(expectedRandom.nextInt(), random.nextInt());
    }

    @Test
    void bonusRollsUseVanillaFloatPrecision() {
        MinecraftServer.init();

        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.LUCK, 1.0, LootContext.RANDOM, new Random(1L))
        );
        var entry = new LootEntry.Item(List.of(), List.of(), 1L, 0L, Material.STONE);
        var pool = new LootPool(
                new LootNumber.Constant(0.0),
                new LootNumber.Constant(0.99999999),
                List.of(entry), List.of(), List.of()
        );

        assertEquals(1, pool.generate(context).size());
    }

    @Test
    void multipleChoicesUseVanillaIntegerRandomness() {
        MinecraftServer.init();

        var random = new Random(1L);
        var expectedRandom = new Random(1L);
        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.LUCK, 0.0, LootContext.RANDOM, random)
        );
        var first = new LootEntry.Item(List.of(), List.of(), 1L, 0L, Material.STONE);
        var second = new LootEntry.Item(List.of(), List.of(), 1L, 0L, Material.DIRT);
        var expectedChoice = expectedRandom.nextInt(2) == 0 ? first : second;

        var choice = LootPool.pickChoice(List.of(first, second), context);

        assertSame(expectedChoice, choice);
        assertEquals(expectedRandom.nextInt(), random.nextInt());
    }

    @Test
    void effectiveWeightUsesVanillaFloatPrecision() {
        MinecraftServer.init();

        var context = LootContext.from(
                VanillaInterface.defaults(),
                Map.of(LootContext.LUCK, 0.99999999, LootContext.RANDOM, new Random(1L))
        );
        var entry = new LootEntry.Item(List.of(), List.of(), 1L, 1L, Material.STONE);

        assertEquals(2L, entry.getWeight(context));
    }

    @Test
    void dynamicEntriesUseHostProvidedDropsWithoutBlockState() {
        var vanilla = (VanillaInterface) Proxy.newProxyInstance(
                VanillaInterface.class.getClassLoader(),
                new Class<?>[]{VanillaInterface.class},
                (proxy, method, arguments) -> method.getName().equals("dynamicDrops")
                        ? List.of(ItemStack.of(Material.DIAMOND))
                        : null
        );
        var context = LootContext.from(vanilla, Map.of());
        var entry = new LootEntry.Dynamic(List.of(), List.of(), 1L, 0L, Key.key("minecraft:test"));

        assertEquals(List.of(Material.DIAMOND), entry.generate(context).stream().map(ItemStack::material).toList());
    }

    @Test
    void expandedTagEntriesDoNotApplyTagFunctions() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var entry = new LootEntry.Tag(
                List.of(),
                List.of(new LootFunction.SetCount(List.of(), new LootNumber.Constant(4.0), false)),
                1L,
                0L,
                RegistryTag.direct(RegistryKey.of("minecraft:stone")),
                true
        );

        assertEquals(1, entry.requestChoices(context).getFirst().generate(context).getFirst().amount());
    }
}
