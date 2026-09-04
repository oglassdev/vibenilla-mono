package net.goldenstack.loot;

import net.goldenstack.loot.util.Template;
import net.goldenstack.loot.util.predicate.ItemPredicate;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.component.DataComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.registry.DynamicRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public interface LootSlotSource {

    @NotNull Codec<LootSlotSource> CODEC = Codec.RegistryTaggedUnion(registries -> {
        class Holder {
            static final @NotNull DynamicRegistry<StructCodec<? extends LootSlotSource>> CODEC = createDefaultRegistry();
        }
        return Holder.CODEC;
    }, LootSlotSource::codec, "type").orElse(Codec.ForwardRef(() -> Group.INLINE_CODEC), value -> value);

    static @NotNull DynamicRegistry<StructCodec<? extends LootSlotSource>> createDefaultRegistry() {
        var registry = DynamicRegistry.<StructCodec<? extends LootSlotSource>>create(Key.key("loot_slot_sources"));
        registry.register("contents", Contents.CODEC);
        registry.register("empty", Empty.CODEC);
        registry.register("filtered", Filtered.CODEC);
        registry.register("group", Group.CODEC);
        registry.register("limit_slots", Limit.CODEC);
        registry.register("reference", Reference.CODEC);
        registry.register("slot_range", Range.CODEC);
        return registry;
    }

    @NotNull List<ItemStack> provide(@NotNull LootContext context);

    @NotNull StructCodec<? extends LootSlotSource> codec();

    record Empty() implements LootSlotSource {
        public static final @NotNull StructCodec<Empty> CODEC = StructCodec.struct(Empty::new);

        @Override
        public @NotNull List<ItemStack> provide(@NotNull LootContext context) {
            return List.of();
        }

        @Override
        public @NotNull StructCodec<? extends LootSlotSource> codec() {
            return CODEC;
        }
    }

    record Group(@NotNull List<LootSlotSource> terms) implements LootSlotSource {
        public static final @NotNull StructCodec<Group> CODEC = StructCodec.struct(
                "terms", LootSlotSource.CODEC.list(), Group::terms,
                Group::new
        );
        public static final @NotNull Codec<Group> INLINE_CODEC = LootSlotSource.CODEC.list()
                .transform(Group::new, Group::terms);

        @Override
        public @NotNull List<ItemStack> provide(@NotNull LootContext context) {
            var items = new ArrayList<ItemStack>();
            for (var term : this.terms) {
                items.addAll(term.provide(context));
            }
            return items;
        }

        @Override
        public @NotNull StructCodec<? extends LootSlotSource> codec() {
            return CODEC;
        }
    }

    record Filtered(@NotNull LootSlotSource slotSource,
                    @NotNull ItemPredicate itemFilter) implements LootSlotSource {
        public static final @NotNull StructCodec<Filtered> CODEC = StructCodec.struct(
                "slot_source", LootSlotSource.CODEC, Filtered::slotSource,
                "item_filter", ItemPredicate.CODEC, Filtered::itemFilter,
                Filtered::new
        );

        @Override
        public @NotNull List<ItemStack> provide(@NotNull LootContext context) {
            return this.slotSource.provide(context).stream()
                    .filter(item -> this.itemFilter.test(item, context))
                    .toList();
        }

        @Override
        public @NotNull StructCodec<? extends LootSlotSource> codec() {
            return CODEC;
        }
    }

    record Limit(@NotNull LootSlotSource slotSource, int limit) implements LootSlotSource {
        private static final @NotNull Codec<Integer> POSITIVE_CODEC = Codec.INT.transform(value -> {

            if (value <= 0) {
                throw new IllegalArgumentException("Slot source limit must be positive");
            }

            return value;
        }, value -> value);

        public Limit {

            if (limit <= 0) {
                throw new IllegalArgumentException("Slot source limit must be positive");
            }
        }

        public static final @NotNull StructCodec<Limit> CODEC = StructCodec.struct(
                "slot_source", LootSlotSource.CODEC, Limit::slotSource,
                "limit", POSITIVE_CODEC, Limit::limit,
                Limit::new
        );

        @Override
        public @NotNull List<ItemStack> provide(@NotNull LootContext context) {
            var items = this.slotSource.provide(context);
            return items.subList(0, Math.min(this.limit, items.size()));
        }

        @Override
        public @NotNull StructCodec<? extends LootSlotSource> codec() {
            return CODEC;
        }
    }

    record Contents(@NotNull LootSlotSource slotSource,
                    @NotNull DataComponent<List<ItemStack>> component) implements LootSlotSource {
        public static final @NotNull StructCodec<Contents> CODEC = StructCodec.struct(
                "slot_source", LootSlotSource.CODEC, Contents::slotSource,
                "component", Template.CONTAINER, Contents::component,
                Contents::new
        );

        @Override
        public @NotNull List<ItemStack> provide(@NotNull LootContext context) {
            var items = new ArrayList<ItemStack>();
            for (var item : this.slotSource.provide(context)) {
                var contents = item.get(this.component);
                if (contents != null) {
                    items.addAll(contents);
                }
            }
            return items;
        }

        @Override
        public @NotNull StructCodec<? extends LootSlotSource> codec() {
            return CODEC;
        }
    }

    record Range(@NotNull String source, @NotNull List<Integer> slots) implements LootSlotSource {
        public static final @NotNull StructCodec<Range> CODEC = StructCodec.struct(
                "source", Codec.STRING.optional("container"), Range::source,
                "slots", Codec.INT.list(), Range::slots,
                Range::new
        );

        @Override
        public @NotNull List<ItemStack> provide(@NotNull LootContext context) {
            return context.vanilla().slotRange(this.source, this.slots, context);
        }

        @Override
        public @NotNull StructCodec<? extends LootSlotSource> codec() {
            return CODEC;
        }
    }

    record Reference(@NotNull Key name) implements LootSlotSource {
        public static final @NotNull StructCodec<Reference> CODEC = StructCodec.struct(
                "name", Codec.KEY, Reference::name,
                Reference::new
        );

        @Override
        public @NotNull List<ItemStack> provide(@NotNull LootContext context) {
            var source = context.vanilla().slotSource(this.name, context);
            return source == null ? List.of() : source.provide(context);
        }

        @Override
        public @NotNull StructCodec<? extends LootSlotSource> codec() {
            return CODEC;
        }
    }
}
