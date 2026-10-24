package net.goldenstack.loot;

import java.util.ArrayList;
import java.util.List;
import net.goldenstack.loot.util.Template;
import net.goldenstack.loot.util.predicate.ItemPredicate;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.component.DataComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.registry.DynamicRegistry;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public interface LootSlotSource {

    @NotNull
    Codec<LootSlotSource> CODEC =
            Codec.RegistryTaggedUnion(
                            registries -> {
                                class Holder {
                                    static final @NotNull DynamicRegistry<
                                                    StructCodec<? extends LootSlotSource>>
                                            CODEC = createDefaultRegistry();
                                }
                                return Holder.CODEC;
                            },
                            LootSlotSource::codec,
                            "type")
                    .orElse(Codec.ForwardRef(() -> Group.INLINE_CODEC), value -> value);

    static @NotNull DynamicRegistry<StructCodec<? extends LootSlotSource>> createDefaultRegistry() {
        var registry =
                DynamicRegistry.<StructCodec<? extends LootSlotSource>>create(
                        Key.key("loot_slot_sources"));
        registry.register("contents", Contents.CODEC);
        registry.register("empty", Empty.CODEC);
        registry.register("filtered", Filtered.CODEC);
        registry.register("group", Group.CODEC);
        registry.register("limit_slots", Limit.CODEC);
        registry.register("reference", Reference.CODEC);
        registry.register("slot_range", Range.CODEC);
        return registry;
    }

    @NotNull
    List<ItemStack> provide(@NotNull LootContext context);

    @NotNull
    StructCodec<? extends LootSlotSource> codec();

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
        public static final @NotNull StructCodec<Group> CODEC =
                StructCodec.struct("terms", LootSlotSource.CODEC.list(), Group::terms, Group::new);
        public static final @NotNull Codec<Group> INLINE_CODEC =
                LootSlotSource.CODEC.list().transform(Group::new, Group::terms);

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

    record Filtered(@NotNull LootSlotSource slotSource, @NotNull ItemPredicate itemFilter)
            implements LootSlotSource {
        public static final @NotNull StructCodec<Filtered> CODEC =
                StructCodec.struct(
                        "slot_source",
                        LootSlotSource.CODEC,
                        Filtered::slotSource,
                        "item_filter",
                        ItemPredicate.CODEC,
                        Filtered::itemFilter,
                        Filtered::new);

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
        private static final @NotNull Codec<Integer> POSITIVE_CODEC =
                Codec.INT.transform(
                        value -> {
                            if (value <= 0) {
                                throw new IllegalArgumentException(
                                        "Slot source limit must be positive");
                            }

                            return value;
                        },
                        value -> value);

        public Limit {
            if (limit <= 0) {
                throw new IllegalArgumentException("Slot source limit must be positive");
            }
        }

        public static final @NotNull StructCodec<Limit> CODEC =
                StructCodec.struct(
                        "slot_source",
                        LootSlotSource.CODEC,
                        Limit::slotSource,
                        "limit",
                        POSITIVE_CODEC,
                        Limit::limit,
                        Limit::new);

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

    record Contents(
            @NotNull LootSlotSource slotSource, @NotNull DataComponent<List<ItemStack>> component)
            implements LootSlotSource {
        public static final @NotNull StructCodec<Contents> CODEC =
                StructCodec.struct(
                        "slot_source",
                        LootSlotSource.CODEC,
                        Contents::slotSource,
                        "component",
                        Template.CONTAINER,
                        Contents::component,
                        Contents::new);

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
        public static final @NotNull StructCodec<Range> CODEC =
                StructCodec.struct(
                        "source",
                        SlotRanges.SOURCE_CODEC.optional("container"),
                        Range::source,
                        "slots",
                        SlotRanges.CODEC,
                        Range::slots,
                        Range::new);

        @Override
        public @NotNull List<ItemStack> provide(@NotNull LootContext context) {
            return context.vanilla().slotRange(this.source, this.slots, context);
        }

        @Override
        public @NotNull StructCodec<? extends LootSlotSource> codec() {
            return CODEC;
        }
    }

    final class SlotRanges {
        private static final @NotNull List<String> SOURCES =
                List.of(
                        "this",
                        "attacker",
                        "direct_attacker",
                        "attacking_player",
                        "target_entity",
                        "interacting_entity",
                        "block_entity",
                        "container");

        private static final @NotNull List<String> RANGE_NAMES = createRangeNames();

        private static final @NotNull Codec<String> SOURCE_CODEC =
                Codec.STRING.transform(
                        value -> {
                            if (!SOURCES.contains(value)) {
                                throw new IllegalArgumentException("Unknown slot source: " + value);
                            }

                            return value;
                        },
                        value -> value);

        private static final @NotNull Codec<List<Integer>> CODEC =
                Codec.STRING.transform(SlotRanges::decode, SlotRanges::encode);

        private SlotRanges() {}

        private static @NotNull List<String> createRangeNames() {
            var names = new ArrayList<String>();
            names.add("contents");
            addRangeNames(names, "container.", 54);
            addRangeNames(names, "hotbar.", 9);
            addRangeNames(names, "inventory.", 27);
            addRangeNames(names, "enderchest.", 27);
            addRangeNames(names, "mob.inventory.", 8);
            addRangeNames(names, "horse.", 15);
            names.addAll(
                    List.of(
                            "weapon",
                            "weapon.mainhand",
                            "weapon.offhand",
                            "weapon.*",
                            "armor.head",
                            "armor.chest",
                            "armor.legs",
                            "armor.feet",
                            "armor.body",
                            "armor.*",
                            "saddle",
                            "horse.chest",
                            "player.cursor"));
            addRangeNames(names, "player.crafting.", 4);
            return List.copyOf(names);
        }

        private static void addRangeNames(
                @NotNull List<String> names, @NotNull String prefix, int size) {
            for (var slot = 0; slot < size; slot++) {
                names.add(prefix + slot);
            }

            names.add(prefix + "*");
        }

        private static @NotNull List<Integer> decode(@NotNull String name) {
            if (!RANGE_NAMES.contains(name)) {
                throw new IllegalArgumentException("Unknown slot range: " + name);
            }

            return switch (name) {
                case "contents" -> List.of(0);
                case "container.*" -> range(0, 54);
                case "hotbar.*" -> range(0, 9);
                case "inventory.*" -> range(9, 27);
                case "enderchest.*" -> range(200, 27);
                case "mob.inventory.*" -> range(300, 8);
                case "horse.*" -> range(500, 15);
                case "weapon", "weapon.mainhand" -> List.of(98);
                case "weapon.offhand" -> List.of(99);
                case "weapon.*" -> List.of(98, 99);
                case "armor.head" -> List.of(103);
                case "armor.chest" -> List.of(102);
                case "armor.legs" -> List.of(101);
                case "armor.feet" -> List.of(100);
                case "armor.body" -> List.of(105);
                case "armor.*" -> List.of(103, 102, 101, 100, 105);
                case "saddle" -> List.of(106);
                case "horse.chest", "player.cursor" -> List.of(499);
                case "player.crafting.*" -> range(500, 4);
                default -> decodeNumbered(name);
            };
        }

        private static @NotNull List<Integer> range(int offset, int size) {
            var slots = new ArrayList<Integer>(size);
            for (var slot = offset; slot < offset + size; slot++) {
                slots.add(slot);
            }

            return List.copyOf(slots);
        }

        private static @NotNull List<Integer> decodeNumbered(@NotNull String name) {
            var prefix =
                    switch (name) {
                        case String value when value.startsWith("container.") -> "container.";
                        case String value when value.startsWith("hotbar.") -> "hotbar.";
                        case String value when value.startsWith("inventory.") -> "inventory.";
                        case String value when value.startsWith("enderchest.") -> "enderchest.";
                        case String value when value.startsWith("mob.inventory.") ->
                                "mob.inventory.";
                        case String value when value.startsWith("horse.") -> "horse.";
                        case String value when value.startsWith("player.crafting.") ->
                                "player.crafting.";
                        default ->
                                throw new IllegalArgumentException("Unknown slot range: " + name);
                    };
            var slot = Integer.parseInt(name.substring(prefix.length()));
            var offset =
                    switch (prefix) {
                        case "container." -> 0;
                        case "hotbar." -> 0;
                        case "inventory." -> 9;
                        case "enderchest." -> 200;
                        case "mob.inventory." -> 300;
                        case "horse." -> 500;
                        case "player.crafting." -> 500;
                        default ->
                                throw new IllegalArgumentException("Unknown slot range: " + name);
                    };
            var size =
                    switch (prefix) {
                        case "container." -> 54;
                        case "hotbar." -> 9;
                        case "inventory." -> 27;
                        case "enderchest." -> 27;
                        case "mob.inventory." -> 8;
                        case "horse." -> 15;
                        case "player.crafting." -> 4;
                        default ->
                                throw new IllegalArgumentException("Unknown slot range: " + name);
                    };

            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("Unknown slot range: " + name);
            }

            return List.of(offset + slot);
        }

        private static @NotNull String encode(@NotNull List<Integer> slots) {
            for (var rangeName : RANGE_NAMES) {
                if (decode(rangeName).equals(slots)) {
                    return rangeName;
                }
            }

            throw new IllegalArgumentException("Cannot encode slot range: " + slots);
        }
    }

    record Reference(@NotNull Key name) implements LootSlotSource {
        public static final @NotNull StructCodec<Reference> CODEC =
                StructCodec.struct("name", Codec.KEY, Reference::name, Reference::new);

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
