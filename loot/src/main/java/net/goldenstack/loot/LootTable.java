package net.goldenstack.loot;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A loot table.
 *
 * @param pools the pools that generate items in this table
 * @param functions the functions applied to each output item of this table
 * @param randomSequence An ID specifying the name of the random sequence that is used to generate
 *     loot from this loot table.
 */
public record LootTable(
        @Nullable Key type,
        @NotNull List<LootPool> pools,
        @NotNull List<LootFunction> functions,
        @Nullable Key randomSequence)
        implements LootGenerator {

    public static final @NotNull LootTable EMPTY = new LootTable(null, List.of(), List.of(), null);

    public LootTable(
            @NotNull List<LootPool> pools,
            @NotNull List<LootFunction> functions,
            @Nullable Key randomSequence) {
        this(null, pools, functions, randomSequence);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static final @NotNull StructCodec<LootTable> CODEC =
            StructCodec.struct(
                    "type",
                    Codec.KEY.optional(),
                    LootTable::type,
                    "pools",
                    LootPool.CODEC.list().optional(List.of()),
                    LootTable::pools,
                    "modifier",
                    LootFunction.CODEC.listOrSingle().optional(List.of()),
                    LootTable::functions,
                    "random_sequence",
                    Codec.KEY.optional(),
                    LootTable::randomSequence,
                    LootTable::new);

    @Override
    public @NotNull List<ItemStack> generate(@NotNull LootContext context) {
        var generationContext =
                this.randomSequence == null
                        ? context
                        : context.withRandom(
                                context.vanilla()
                                        .randomSequence(
                                                this.randomSequence,
                                                context.require(LootContext.RANDOM)));
        var items = new ArrayList<ItemStack>();

        for (var pool : this.pools) {
            for (var item : pool.generate(generationContext)) {
                items.add(LootFunction.apply(this.functions, item, generationContext));
            }
        }

        return items;
    }
}
