package dev.oglass.vibenilla.containers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.function.Function;
import net.goldenstack.loot.LootContext;
import net.goldenstack.loot.LootTable;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Fills containers that still reference a loot table, like vanilla structure chests. */
final class LootFiller {
    private static final String LOOT_TABLE = "LootTable";
    private static final String LOOT_TABLE_SEED = "LootTableSeed";

    private LootFiller() {}

    /**
     * Returns the block with its loot table replaced by generated contents, or the block itself if
     * it has no loot table.
     */
    static Block unpack(
            Function<Key, LootTable> tables,
            Instance instance,
            BlockVec position,
            Block block,
            @Nullable Player player) {
        var nbt = block.nbtOrEmpty();
        var name = nbt.getString(LOOT_TABLE, "");

        if (name.isEmpty()) {
            return block;
        }

        var seed = nbt.getLong(LOOT_TABLE_SEED);
        var cleared = block.withNbt(nbt.remove(LOOT_TABLE).remove(LOOT_TABLE_SEED));
        var table = tables.apply(Key.key(name));

        if (table == null) {
            MinecraftServer.LOGGER.warn("Missing loot table {} at {}", name, position);
            return cleared;
        }

        var random = seed == 0 ? new Random() : new Random(seed);
        var data = new HashMap<LootContext.Key<?>, Object>();
        data.put(LootContext.RANDOM, random);
        data.put(LootContext.WORLD, instance);
        data.put(LootContext.ORIGIN, position.add(0.5, 0.5, 0.5));

        if (player != null) {
            data.put(LootContext.THIS_ENTITY, player);
        }

        var generated = table.generate(LootContext.from(VanillaInterface.defaults(), data));
        var contents = ContainerItems.read(cleared, ContainerKind.SLOTS_PER_BLOCK);
        var emptySlots = new ArrayList<Integer>();

        for (var slot = 0; slot < contents.length; slot++) {
            if (contents[slot].isAir()) {
                emptySlots.add(slot);
            }
        }

        Collections.shuffle(emptySlots, random);

        for (var item : splitAndShuffle(generated, emptySlots.size(), random)) {
            if (emptySlots.isEmpty()) {
                MinecraftServer.LOGGER.warn("Tried to over-fill a container at {}", position);
                break;
            }

            contents[emptySlots.removeLast()] = item;
        }

        return ContainerItems.write(cleared, Arrays.asList(contents));
    }

    private static ArrayList<ItemStack> splitAndShuffle(
            Iterable<ItemStack> generated, int slots, Random random) {
        var items = new ArrayList<ItemStack>();
        var splittable = new ArrayList<ItemStack>();

        for (var item : generated) {
            (item.amount() > 1 ? splittable : items).add(item);
        }

        while (slots - items.size() - splittable.size() > 0 && !splittable.isEmpty()) {
            var stack = splittable.remove(random.nextInt(splittable.size()));
            var splitAmount = 1 + random.nextInt(stack.amount() / 2);
            var split = stack.withAmount(splitAmount);
            var rest = stack.withAmount(stack.amount() - splitAmount);

            (rest.amount() > 1 && random.nextBoolean() ? splittable : items).add(rest);
            (split.amount() > 1 && random.nextBoolean() ? splittable : items).add(split);
        }

        items.addAll(splittable);
        Collections.shuffle(items, random);
        return items;
    }
}
