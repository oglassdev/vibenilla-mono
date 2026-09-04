package net.goldenstack.loot;

import net.goldenstack.loot.util.*;
import net.goldenstack.loot.util.nbt.NBTPath;
import net.goldenstack.loot.util.nbt.NBTReference;
import net.goldenstack.loot.util.nbt.NBTUtils;
import net.goldenstack.loot.util.predicate.ItemPredicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.color.Color;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.book.FilteredText;
import net.minestom.server.item.component.*;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.item.instrument.Instrument;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.RegistryTranscoder;
import net.minestom.server.registry.TagKey;
import net.minestom.server.utils.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * A function that allows loot to pass through it, potentially making modifications.
 */
@SuppressWarnings("UnstableApiUsage")
public interface LootFunction {

    @NotNull Codec<LootFunction> CODEC = makeCodec().orElse(makeCodec().list().transform(Sequence::new, seq -> ((Sequence) seq).functions()));

    private static StructCodec<LootFunction> makeCodec() {
        return Codec.RegistryTaggedUnion(registries -> {
            class Holder {
                static final @NotNull DynamicRegistry<StructCodec<? extends LootFunction>> CODEC = createDefaultRegistry();
            }
            return Holder.CODEC;
        }, LootFunction::codec, "function");
    }

    static @NotNull DynamicRegistry<StructCodec<? extends LootFunction>> createDefaultRegistry() {
        final DynamicRegistry<StructCodec<? extends LootFunction>> registry = DynamicRegistry.create(Key.key("loot_functions"));
        registry.register("apply_bonus", ApplyBonus.CODEC);
        registry.register("copy_components", CopyComponents.CODEC);
        registry.register("copy_custom_data", CopyCustomData.CODEC);
        registry.register("copy_name", CopyName.CODEC);
        registry.register("copy_state", CopyState.CODEC);
        registry.register("discard", Discard.CODEC);
        registry.register("enchanted_count_increase", EnchantedCountIncrease.CODEC);
        registry.register("enchant_randomly", EnchantRandomly.CODEC);
        registry.register("enchant_with_levels", EnchantWithLevels.CODEC);
        registry.register("exploration_map", ExplorationMap.CODEC);
        registry.register("explosion_decay", ExplosionDecay.CODEC);
        registry.register("fill_player_head", FillPlayerHead.CODEC);
        registry.register("filtered", Filtered.CODEC);
        registry.register("furnace_smelt", FurnaceSmelt.CODEC);
        registry.register("limit_count", LimitCount.CODEC);
        registry.register("modify_contents", ModifyContents.CODEC);
        registry.register("reference", Reference.CODEC);
        registry.register("set_attributes", SetAttributes.CODEC);
        registry.register("set_banner_pattern", SetBannerPattern.CODEC);
        registry.register("set_book_cover", SetBookCover.CODEC);
        registry.register("set_components", SetComponents.CODEC);
        registry.register("set_contents", SetContents.CODEC);
        registry.register("set_count", SetCount.CODEC);
        registry.register("set_custom_data", SetCustomData.CODEC);
        registry.register("set_custom_model_data", SetCustomModelData.CODEC);
        registry.register("set_damage", SetDamage.CODEC);
        registry.register("set_enchantments", SetEnchantments.CODEC);
        registry.register("set_firework_explosion", SetFireworkExplosion.CODEC);
        registry.register("set_fireworks", SetFireworks.CODEC);
        registry.register("set_instrument", SetInstrument.CODEC);
        registry.register("set_item", SetItem.CODEC);
        registry.register("set_loot_table", SetLootTable.CODEC);
        registry.register("set_lore", SetLore.CODEC);
        registry.register("set_name", SetName.CODEC);
        registry.register("set_ominous_bottle_amplifier", SetOminousBottleAmplifier.CODEC);
        registry.register("set_potion", SetPotion.CODEC);
        registry.register("set_random_dyes", SetRandomDyes.CODEC);
        registry.register("set_random_potion", SetRandomPotion.CODEC);
        registry.register("set_stew_effect", SetStewEffect.CODEC);
        registry.register("set_writable_book_pages", SetWritableBookPages.CODEC);
        registry.register("set_written_book_pages", SetWrittenBookPages.CODEC);
        registry.register("sequence", Sequence.CODEC);
        registry.register("toggle_tooltips", ToggleTooltips.CODEC);
        return registry;
    }

    /**
     * Performs any mutations on the provided object and returns the result.
     * @param input the input item to this function
     * @param context the context object, to use if required
     * @return the modified form of the input
     */
    @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context);

    /**
     * @return the codec that can encode this function
     */
    @NotNull StructCodec<? extends LootFunction> codec();

    /**
     * Applies each function to the given item consecutively.
     * @param functions the functions to apply
     * @param item the item to modify
     * @param context the context to use
     * @return the modified item
     */
    static @NotNull ItemStack apply(@NotNull Collection<LootFunction> functions, @NotNull ItemStack item, @NotNull LootContext context) {
        for (LootFunction function : functions) {
            item = function.apply(item, context);
        }
        return item;
    }

    /**
     * Applies each function to each of the given items consecutively.
     * @param functions the functions to apply
     * @param items the items to modify
     * @param context the context to use
     * @return the modified items
     */
    static @NotNull List<ItemStack> apply(@NotNull Collection<LootFunction> functions, @NotNull List<ItemStack> items, @NotNull LootContext context) {
        List<ItemStack> newItems = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            newItems.add(LootFunction.apply(functions, item, context));
        }
        return newItems;
    }

    private static void addStackSplits(@NotNull List<ItemStack> items, @NotNull ItemStack item) {
        var maximumStackSize = item.maxStackSize();

        if (item.amount() < maximumStackSize) {
            items.add(item);
            return;
        }

        var remainingAmount = item.amount();

        while (remainingAmount > 0) {
            var splitAmount = Math.min(maximumStackSize, remainingAmount);
            items.add(item.withAmount(splitAmount));
            remainingAmount -= splitAmount;
        }
    }

    record ApplyBonus(@NotNull List<LootPredicate> predicates, @NotNull RegistryKey<Enchantment> enchantment, @NotNull Formula.Wrapper formula) implements LootFunction {
        public static final @NotNull StructCodec<ApplyBonus> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), ApplyBonus::predicates,
                "enchantment", RegistryKey.codec(Registries::enchantment), ApplyBonus::enchantment,
                StructCodec.INLINE, Formula.CODEC, ApplyBonus::formula,
                ApplyBonus::new
        );

        public sealed interface Formula {

            enum FormulaType {
                BINOMIAL_WITH_BONUS_COUNT,
                ORE_DROPS,
                UNIFORM_BONUS_COUNT,
            }

            record Wrapper(Formula parameters) {
                private static <T extends Formula> StructCodec<Wrapper> wrap(@NotNull StructCodec<T> codec) {
                    return StructCodec.struct(
                            "parameters", codec.transform(a->a,a->(T)a), Wrapper::parameters,
                            Wrapper::new
                    );
                }

                private static final StructCodec<Wrapper> BINOMIAL_WITH_BONUS_COUNT = wrap(BinomialWithBonusCount.CODEC);
                private static final StructCodec<Wrapper> ORE_DROPS = new StructCodec<>() {
                    @Override
                    public @NotNull <D> Result<Wrapper> decodeFromMap(@NotNull Transcoder<D> coder, Transcoder.@NotNull MapLike<D> map) {
                        return new Result.Ok<>(new Wrapper(new OreDrops()));
                    }

                    @Override
                    public @NotNull <D> Result<D> encodeToMap(@NotNull Transcoder<D> coder, @NotNull Wrapper value, Transcoder.@NotNull MapBuilder<D> map) {
                        return new Result.Ok<>(map.build());
                    }
                };
                private static final StructCodec<Wrapper> UNIFORM_BONUS_COUNT = wrap(UniformBonusCount.CODEC);

                public static @NotNull StructCodec<Wrapper> codec(@NotNull FormulaType type) {
                    return switch (type) {
                        case BINOMIAL_WITH_BONUS_COUNT -> BINOMIAL_WITH_BONUS_COUNT;
                        case ORE_DROPS -> ORE_DROPS;
                        case UNIFORM_BONUS_COUNT -> UNIFORM_BONUS_COUNT;
                    };
                }
            }

            @NotNull Codec<Wrapper> CODEC = Codec.KEY.transform(key -> switch (key.asString()) {
                case "minecraft:binomial_with_bonus_count" -> FormulaType.BINOMIAL_WITH_BONUS_COUNT;
                case "minecraft:ore_drops" -> FormulaType.ORE_DROPS;
                case "minecraft:uniform_bonus_count" -> FormulaType.UNIFORM_BONUS_COUNT;
                default -> throw new IllegalArgumentException();
            }, type -> Key.key(type.toString().toLowerCase())).unionType("formula", Wrapper::codec, (Wrapper formula) -> switch (formula.parameters()) {
                case BinomialWithBonusCount ignored -> FormulaType.BINOMIAL_WITH_BONUS_COUNT;
                case OreDrops ignored -> FormulaType.ORE_DROPS;
                case UniformBonusCount ignored -> FormulaType.UNIFORM_BONUS_COUNT;
            });

            int calculate(@NotNull Random random, int count, int level);

            record UniformBonusCount(int bonusMultiplier) implements Formula {
                public static final @NotNull StructCodec<UniformBonusCount> CODEC = StructCodec.struct(
                        "bonusMultiplier", StructCodec.INT, UniformBonusCount::bonusMultiplier,
                        UniformBonusCount::new
                );

                @Override
                public int calculate(@NotNull Random random, int count, int level) {
                    return count + random.nextInt(bonusMultiplier * level + 1);
                }
            }

            record OreDrops() implements Formula {
                public static final @NotNull StructCodec<OreDrops> CODEC = StructCodec.struct(OreDrops::new);

                @Override
                public int calculate(@NotNull Random random, int count, int level) {
                    if (level <= 0) return count;

                    return count * Math.max(1, random.nextInt(level + 2));
                }
            }

            record BinomialWithBonusCount(float probability, int extra) implements Formula {
                public static final @NotNull StructCodec<BinomialWithBonusCount> CODEC = StructCodec.struct(
                        "probability", Codec.FLOAT, BinomialWithBonusCount::probability,
                        "extra", Codec.INT, BinomialWithBonusCount::extra,
                        BinomialWithBonusCount::new
                );

                @Override
                public int calculate(@NotNull Random random, int count, int level) {
                    for (int i = 0; i < extra + level; i++) {
                        if (random.nextFloat() < probability) {
                            count++;
                        }
                    }

                    return count;
                }
            }

        }

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            ItemStack tool = context.get(LootContext.TOOL);
            if (tool == null) return input;

            int level = EnchantmentUtils.level(tool, enchantment);
            int newCount = formula.parameters().calculate(context.require(LootContext.RANDOM), input.amount(), level);

            return input.withAmount(newCount);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record CopyComponents(@NotNull List<LootPredicate> predicates, @NotNull RelevantTarget source,
                          @Nullable List<DataComponent<?>> include, @Nullable List<DataComponent<?>> exclude) implements LootFunction {
        public static final @NotNull StructCodec<CopyComponents> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), CopyComponents::predicates,
                "source", RelevantTarget.CODEC, CopyComponents::source,
                "include", Codec.KEY.<DataComponent<?>>transform(DataComponent::fromKey, DataComponent::key).list().optional(), CopyComponents::include,
                "exclude", Codec.KEY.<DataComponent<?>>transform(DataComponent::fromKey, DataComponent::key).list().optional(), CopyComponents::exclude,
                CopyComponents::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            DataComponentMap sourceComponents = switch (source) {
                case BLOCK_ENTITY -> blockComponents(context.get(LootContext.BLOCK_STATE));
                case THIS, ATTACKER, DIRECT_ATTACKER, ATTACKING_PLAYER, TOOL, TARGET_ENTITY, INTERACTING_ENTITY -> holderComponents(context.get(source.key()));
            };

            if (sourceComponents == null) return input;

            ItemStack.Builder builder = input.builder();
            for (DataComponent.Value entry : sourceComponents.entrySet()) {
                DataComponent<?> component = entry.component();

                if (include != null && !include.contains(component)) continue;

                if (exclude != null && exclude.contains(component)) continue;

                setComponent(builder, component, entry.value());
            }

            return builder.build();
        }

        private static @Nullable DataComponentMap holderComponents(@Nullable Object source) {
            if (!(source instanceof DataComponent.Holder holder)) return null;

            DataComponentMap.Builder builder = DataComponentMap.builder();
            for (DataComponent<?> component : DataComponent.values()) {
                copyComponent(builder, holder, component);
            }

            return builder.build();
        }

        private static @Nullable DataComponentMap blockComponents(@Nullable Block block) {
            if (block == null || !block.hasNbt()) return null;

            BinaryTag components = block.nbt().get("components");
            if (!(components instanceof CompoundBinaryTag compound)) return DataComponentMap.EMPTY;

            Transcoder<BinaryTag> coder = new RegistryTranscoder<>(Transcoder.NBT, MinecraftServer.process());
            Result<DataComponentMap> result = DataComponent.MAP_NBT_TYPE.decode(coder, compound);

            return result instanceof Result.Ok(DataComponentMap componentMap) ? componentMap : DataComponentMap.EMPTY;
        }

        private static <T> void copyComponent(@NotNull DataComponentMap.Builder builder,
                                               @NotNull DataComponent.Holder holder,
                                               @NotNull DataComponent<T> component) {
            T value = holder.get(component);

            if (value != null) builder.set(component, value);
        }

        @SuppressWarnings("unchecked")
        private static <T> void setComponent(@NotNull ItemStack.Builder builder,
                                             @NotNull DataComponent<?> component,
                                             @Nullable Object value) {
            builder.set((DataComponent<T>) component, (T) value);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }
    
    record CopyCustomData(@NotNull List<LootPredicate> predicates, @NotNull LootNBT source, @NotNull List<Operation> ops) implements LootFunction {
        public static final @NotNull StructCodec<CopyCustomData> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), CopyCustomData::predicates,
                "source", LootNBT.CODEC, CopyCustomData::source,
                "ops", Operation.CODEC.list(), CopyCustomData::ops,
                CopyCustomData::new
        );

        public record Operation(@NotNull NBTPath source, @NotNull NBTPath target, @NotNull Operator op) {
            public static final @NotNull StructCodec<Operation> CODEC = StructCodec.struct(
                    "source", NBTPath.CODEC, Operation::source,
                    "target", NBTPath.CODEC, Operation::target,
                    "op", Operator.SERIALIZER, Operation::op,
                    Operation::new
            );

            public void execute(@NotNull NBTReference nbt, @NotNull BinaryTag sourceTag) {
                List<BinaryTag> nbts = new ArrayList<>();
                source.get(sourceTag).forEach(ref -> nbts.add(ref.get()));

                if (nbts.isEmpty()) return;
                op.merge(nbt, target, nbts);
            }
        }

        public enum Operator {
            REPLACE() {
                @Override
                public void merge(@NotNull NBTReference nbt, @NotNull NBTPath target, @NotNull List<BinaryTag> source) {
                    target.set(nbt, source.getLast());
                }
            },
            APPEND() {
                @Override
                public void merge(@NotNull NBTReference nbt, @NotNull NBTPath target, @NotNull List<BinaryTag> source) {
                    List<NBTReference> nbts = target.getWithDefaults(nbt, ListBinaryTag::empty);

                    for (var ref : nbts) {
                        source.forEach(ref::listAdd);
                    }
                }
            },
            MERGE() {
                @Override
                public void merge(@NotNull NBTReference nbt, @NotNull NBTPath target, @NotNull List<BinaryTag> source) {
                    List<NBTReference> nbts = target.getWithDefaults(nbt, CompoundBinaryTag::empty);

                    for (var ref : nbts) {
                        if (ref.get() instanceof CompoundBinaryTag compound) {
                            for (var nbt2 : source) {
                                if (nbt2 instanceof CompoundBinaryTag compound2) {
                                    ref.set(NBTUtils.merge(compound, compound2));
                                }
                            }
                        }
                    }
                }
            };

            public static final @NotNull Codec<Operator> SERIALIZER = Codec.Enum(Operator.class);

            public abstract void merge(@NotNull NBTReference nbt, @NotNull NBTPath target, @NotNull List<BinaryTag> source);
        }

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            BinaryTag sourceNBT = source.getNBT(context);
            if (sourceNBT == null) return input;

            NBTReference targetNBT = NBTReference.of(input.get(DataComponents.CUSTOM_DATA, CustomData.EMPTY).nbt());

            for (Operation operation : ops) {
                operation.execute(targetNBT, sourceNBT);
            }

            if (targetNBT.get() instanceof CompoundBinaryTag compound) {
                return input.with(DataComponents.CUSTOM_DATA, new CustomData(compound));
            } else {
                return input;
            }
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }
    
    record CopyName(@NotNull List<LootPredicate> predicates, @NotNull RelevantTarget source) implements LootFunction {
        public static final @NotNull StructCodec<CopyName> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), CopyName::predicates,
                "source", RelevantTarget.ENTITY_OR_BLOCK_CODEC, CopyName::source,
                CopyName::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            var key = context.get(this.source.key());

            if (key instanceof Entity entity) {
                var customName = entity.getCustomName();
                return customName == null ? input.without(DataComponents.CUSTOM_NAME) : input.with(DataComponents.CUSTOM_NAME, customName);
            }

            if (key instanceof Block block && block.hasTag(VanillaInterface.CUSTOM_NAME)) {
                var customName = block.getTag(VanillaInterface.CUSTOM_NAME);
                return customName == null ? input.without(DataComponents.CUSTOM_NAME) : input.with(DataComponents.CUSTOM_NAME, customName);
            }

            return input;
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record CopyState(@NotNull List<LootPredicate> predicates, @NotNull Block block, @NotNull List<String> properties) implements LootFunction {
        public static final @NotNull StructCodec<CopyState> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), CopyState::predicates,
                "block", Codec.KEY.transform(Block::fromKey, Block::key), CopyState::block,
                "properties", Codec.STRING.list(), CopyState::properties,
                CopyState::new
        );

        public CopyState {
            List<String> props = new ArrayList<>(properties);
            props.removeIf(name -> !block.properties().containsKey(name));
            properties = List.copyOf(props);
        }

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            Block block = context.get(LootContext.BLOCK_STATE);
            if (block == null) return input;

            ItemBlockState irritableBowelSyndrome = input.get(DataComponents.BLOCK_STATE, ItemBlockState.EMPTY);

            if (!block.key().equals(this.block.key())) return input;

            for (var prop : properties) {
                @Nullable String value = block.getProperty(prop);
                if (value == null) continue;

                irritableBowelSyndrome = irritableBowelSyndrome.with(prop, value);
            }

            return input.with(DataComponents.BLOCK_STATE, irritableBowelSyndrome);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record EnchantedCountIncrease(@NotNull List<LootPredicate> predicates, @NotNull RegistryKey<Enchantment> enchantment,
                                  @NotNull LootNumber count, @Nullable Integer limit) implements LootFunction {
        public static final @NotNull StructCodec<EnchantedCountIncrease> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), EnchantedCountIncrease::predicates,
                "enchantment", RegistryKey.codec(Registries::enchantment), EnchantedCountIncrease::enchantment,
                "count", LootNumber.CODEC, EnchantedCountIncrease::count,
                "limit", Codec.INT.optional(), EnchantedCountIncrease::limit,
                EnchantedCountIncrease::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            Entity attacker = context.get(LootContext.ATTACKING_ENTITY);
            int level = EnchantmentUtils.level(attacker, enchantment);

            if (level == 0) return input;

            var addition = Math.round(level * (float) this.count.getDouble(context));
            var newAmount = input.amount() + addition;

            return input.withAmount(this.limit != null && this.limit > 0 ? Math.min(this.limit, newAmount) : newAmount);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record EnchantRandomly(@NotNull List<LootPredicate> predicates, @Nullable RegistryTag<Enchantment> options,
                           boolean onlyCompatible, boolean includeAdditionalCostComponent) implements LootFunction {
        public EnchantRandomly(@NotNull List<LootPredicate> predicates,
                               @Nullable RegistryTag<Enchantment> options,
                               boolean onlyCompatible) {
            this(predicates, options, onlyCompatible, false);
        }

        public static final @NotNull StructCodec<EnchantRandomly> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), EnchantRandomly::predicates,
                "options", RegistryTag.codec(Registries::enchantment).optional(), EnchantRandomly::options,
                "only_compatible", Codec.BOOLEAN.optional(true), EnchantRandomly::onlyCompatible,
                "include_additional_cost_component", Codec.BOOLEAN.optional(false), EnchantRandomly::includeAdditionalCostComponent,
                EnchantRandomly::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            var enchantmentRegistry = MinecraftServer.getEnchantmentRegistry();

            var values = new ArrayList<RegistryKey<Enchantment>>();
            (this.options == null ? enchantmentRegistry.keys() : this.options).forEach(values::add);

            if (this.onlyCompatible && !input.material().equals(Material.BOOK)) {
                values.removeIf(enchantment -> !enchantmentRegistry.get(enchantment).supportedItems().contains(input.material()));
            }

            if (values.isEmpty()) {
                return input;
            }

            var random = context.require(LootContext.RANDOM);

            var chosen = values.get(random.nextInt(values.size()));

            var level = random.nextInt(enchantmentRegistry.get(chosen).maxLevel()) + 1;
            var result = EnchantmentUtils.modifyItem(input, map -> map.put(chosen, level));

            if (this.includeAdditionalCostComponent && context.has(LootContext.ADDITIONAL_COST_COMPONENT_ALLOWED)) {
                return result.with(DataComponents.ADDITIONAL_TRADE_COST, 2 + random.nextInt(5 + level * 10) + 3 * level);
            }

            return result;
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record EnchantWithLevels(@NotNull List<LootPredicate> predicates, @NotNull LootNumber levels,
                             @Nullable RegistryTag<Enchantment> options, boolean includeAdditionalCostComponent) implements LootFunction {
        public EnchantWithLevels(@NotNull List<LootPredicate> predicates,
                                 @NotNull LootNumber levels,
                                 @Nullable RegistryTag<Enchantment> options) {
            this(predicates, levels, options, false);
        }

        public static final @NotNull StructCodec<EnchantWithLevels> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), EnchantWithLevels::predicates,
                "levels", LootNumber.CODEC, EnchantWithLevels::levels,
                "options", RegistryTag.codec(Registries::enchantment).optional(), EnchantWithLevels::options,
                "include_additional_cost_component", Codec.BOOLEAN.optional(false), EnchantWithLevels::includeAdditionalCostComponent,
                EnchantWithLevels::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            var enchantmentCost = this.levels.getInt(context);
            var result = context.vanilla().enchant(context.require(LootContext.RANDOM), input, enchantmentCost, this.options);

            if (this.includeAdditionalCostComponent
                    && context.has(LootContext.ADDITIONAL_COST_COMPONENT_ALLOWED)
                    && !result.isAir()
                    && enchantmentCost > 0) {
                return result.with(DataComponents.ADDITIONAL_TRADE_COST, enchantmentCost);
            }

            return result;
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record ExplorationMap(@NotNull List<LootPredicate> predicates,
                           @NotNull TagKey<Object> destination,
                           @NotNull Key decoration,
                           byte zoom,
                           int searchRadius,
                           boolean skipExistingChunks) implements LootFunction {
        public static final @NotNull StructCodec<ExplorationMap> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), ExplorationMap::predicates,
                "destination", Codec.STRING.transform(TagKey::ofHash, TagKey::hashedKey).optional(TagKey.ofHash("#minecraft:on_treasure_maps")), ExplorationMap::destination,
                "decoration", Codec.KEY.optional(Key.key("minecraft:woodland_mansion")), ExplorationMap::decoration,
                "zoom", Codec.BYTE.optional((byte) 2), ExplorationMap::zoom,
                "search_radius", Codec.INT.optional(50), ExplorationMap::searchRadius,
                "skip_existing_chunks", Codec.BOOLEAN.optional(true), ExplorationMap::skipExistingChunks,
                ExplorationMap::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            if (!input.material().equals(Material.FILLED_MAP)) return input;

            var world = context.get(LootContext.WORLD);
            var origin = context.get(LootContext.ORIGIN);
            if (world == null || origin == null) return input;

            return context.vanilla().explorationMap(
                    input,
                    world,
                    origin,
                    this.destination,
                    this.decoration,
                    this.zoom,
                    this.searchRadius,
                    this.skipExistingChunks
            );
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record ExplosionDecay(@NotNull List<LootPredicate> predicates) implements LootFunction {
        public static final @NotNull StructCodec<ExplosionDecay> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), ExplosionDecay::predicates,
                ExplosionDecay::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            Float radius = context.get(LootContext.EXPLOSION_RADIUS);
            if (radius == null) return input;

            Random random = context.require(LootContext.RANDOM);

            float chance = 1 / radius;
            int trials = input.amount();

            int newAmount = 0;

            for (int i = 0; i < trials; i++) {
                if (random.nextFloat() <= chance) {
                    newAmount++;
                }
            }

            return input.withAmount(newAmount);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record FillPlayerHead(@NotNull List<LootPredicate> predicates, @NotNull RelevantEntity entity) implements LootFunction {
        public static final @NotNull StructCodec<FillPlayerHead> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), FillPlayerHead::predicates,
                "entity", RelevantEntity.CODEC, FillPlayerHead::entity,
                FillPlayerHead::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            if (!input.material().equals(Material.PLAYER_HEAD)) return input;

            if (!(context.get(entity.key()) instanceof Player player)) return input;

            PlayerSkin skin = player.getSkin();
            if (skin == null) return input;

            return input.with(DataComponents.PROFILE, new ResolvableProfile(skin));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }
    
    record Filtered(@NotNull List<LootPredicate> predicates, @NotNull ItemPredicate predicate,
                    @Nullable LootFunction onPass, @Nullable LootFunction onFail) implements LootFunction {
        public static final @NotNull StructCodec<Filtered> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), Filtered::predicates,
                "item_filter", ItemPredicate.CODEC, Filtered::predicate,
                "on_pass", LootFunction.CODEC.optional(), Filtered::onPass,
                "on_fail", LootFunction.CODEC.optional(), Filtered::onFail,
                Filtered::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {

                return input;
            }

            var function = this.predicate.test(input, context) ? this.onPass : this.onFail;

            return function == null ? input : function.apply(input, context);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record Discard(@NotNull List<LootPredicate> predicates) implements LootFunction {
        public static final @NotNull StructCodec<Discard> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), Discard::predicates,
                Discard::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {

                return input;
            }

            return ItemStack.AIR;
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }
    
    record FurnaceSmelt(@NotNull List<LootPredicate> predicates, boolean useInputCount) implements LootFunction {
        public FurnaceSmelt(@NotNull List<LootPredicate> predicates) {
            this(predicates, true);
        }

        public static final @NotNull StructCodec<FurnaceSmelt> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), FurnaceSmelt::predicates,
                "use_input_count", Codec.BOOLEAN.optional(true), FurnaceSmelt::useInputCount,
                FurnaceSmelt::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            var smelted = context.vanilla().smelt(input);

            if (smelted == null || smelted.isAir()) {
                return input;
            }

            var amount = this.useInputCount ? input.amount() * smelted.amount() : smelted.amount();
            return smelted.withAmount(Math.min(amount, smelted.maxStackSize()));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record LimitCount(@NotNull List<LootPredicate> predicates, @NotNull LootNumberRange limit) implements LootFunction {
        public static final @NotNull StructCodec<LimitCount> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), LimitCount::predicates,
                "limit", LootNumberRange.CODEC, LimitCount::limit,
                LimitCount::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;
            return input.withAmount(i -> (int) limit.limit(context, i));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record ModifyContents(@NotNull List<LootPredicate> predicates, @NotNull List<LootFunction> modifier,
                          @NotNull DataComponent<List<ItemStack>> component) implements LootFunction {
        public static final @NotNull StructCodec<ModifyContents> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), ModifyContents::predicates,
                "modifier", LootFunction.CODEC.list(), ModifyContents::modifier,
                "component", Template.CONTAINER, ModifyContents::component,
                ModifyContents::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            if (input.isAir()) {
                return input;
            }

            var items = input.get(this.component);

            if (items == null) {
                return input;
            }

            var updated = new ArrayList<ItemStack>();

            for (var item : items) {
                if (item.isAir()) {
                    updated.add(item);
                    continue;
                }

                var modifiedItem = LootFunction.apply(modifier, item, context);
                updated.add(modifiedItem.withAmount(Math.min(modifiedItem.amount(), modifiedItem.maxStackSize())));
            }

            return input.with(this.component, updated);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record Reference(@NotNull List<LootPredicate> predicates, @NotNull Key name) implements LootFunction {
        public static final @NotNull StructCodec<Reference> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), Reference::predicates,
                "name", Codec.KEY, Reference::name,
                Reference::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            LootFunction function = context.vanilla().functionRegistry(name);

            return function != null ? function.apply(input, context) : input;
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetAttributes(@NotNull List<LootPredicate> predicates, @NotNull List<AttributeDirective> modifiers, boolean replace) implements LootFunction {
        public static final @NotNull StructCodec<SetAttributes> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetAttributes::predicates,
                "modifiers", AttributeDirective.CODEC.list(), SetAttributes::modifiers,
                "replace", Codec.BOOLEAN.optional(true), SetAttributes::replace,
                SetAttributes::new
        );

        public record AttributeDirective(@NotNull Key id, @NotNull Attribute attribute, @NotNull AttributeOperation operation,
                                         @NotNull LootNumber amount, @NotNull List<EquipmentSlotGroup> slots) {

            private static final @NotNull Codec<List<EquipmentSlotGroup>> SLOTS_CODEC = EquipmentSlotGroup.CODEC.listOrSingle(Integer.MAX_VALUE).transform(slots -> {

                if (slots.isEmpty()) {
                    throw new IllegalArgumentException("Attribute modifier slots must not be empty");
                }

                return slots;
            }, slots -> slots);

            public static final @NotNull Codec<AttributeDirective> CODEC = StructCodec.struct(
                    "id", Codec.KEY, AttributeDirective::id,
                    "attribute", Attribute.CODEC, AttributeDirective::attribute,
                    "operation", AttributeOperation.CODEC, AttributeDirective::operation,
                    "amount", LootNumber.CODEC, AttributeDirective::amount,
                    "slot", SLOTS_CODEC, AttributeDirective::slots,
                    AttributeDirective::new
            );

            public AttributeDirective {

                if (slots.isEmpty()) {
                    throw new IllegalArgumentException("Attribute modifier slots must not be empty");
                }
            }

        }

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            var component = input.get(DataComponents.ATTRIBUTE_MODIFIERS, AttributeList.EMPTY);

            var list = this.replace ? new ArrayList<AttributeList.Modifier>() : new ArrayList<>(component.modifiers());

            for (var modifier : this.modifiers) {
                var attributeModifier = new AttributeModifier(
                        modifier.id(),
                        (float) modifier.amount().getDouble(context),
                        modifier.operation()
                );

                var slot = modifier.slots().get(context.require(LootContext.RANDOM).nextInt(modifier.slots().size()));

                list.add(new AttributeList.Modifier(modifier.attribute(), attributeModifier, slot));
            }

            return input.with(DataComponents.ATTRIBUTE_MODIFIERS, new AttributeList(list));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetBannerPattern(@NotNull List<LootPredicate> predicates, @NotNull BannerPatterns patterns, boolean append) implements LootFunction {
        public static final @NotNull StructCodec<SetBannerPattern> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetBannerPattern::predicates,
                "patterns", BannerPatterns.CODEC, SetBannerPattern::patterns,
                "append", Codec.BOOLEAN, SetBannerPattern::append,
                SetBannerPattern::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            if (append) {
                BannerPatterns patterns = input.get(DataComponents.BANNER_PATTERNS);
                if (patterns != null) {
                    List<BannerPatterns.Layer> layers = new ArrayList<>(patterns.layers());
                    layers.addAll(this.patterns().layers());
                    return input.with(DataComponents.BANNER_PATTERNS, new BannerPatterns(layers));
                }
            }

            return input.with(DataComponents.BANNER_PATTERNS, patterns);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetBookCover(@NotNull List<LootPredicate> predicates, @Nullable FilteredText<String> title,
                        @Nullable String author, @Nullable Integer generation) implements LootFunction {
        public SetBookCover {

            if (generation != null && (generation < 0 || generation > 3)) {
                throw new IllegalArgumentException("Book generation must be between 0 and 3");
            }
        }

        public static final @NotNull StructCodec<SetBookCover> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetBookCover::predicates,
                "title", FilteredText.STRING_CODEC.optional(), SetBookCover::title,
                "author", Codec.STRING.optional(), SetBookCover::author,
                "generation", Codec.INT.optional(), SetBookCover::generation,
                SetBookCover::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            WrittenBookContent content = input.get(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY);

            WrittenBookContent updated = new WrittenBookContent(
                    title != null ? title : content.title(),
                    author != null ? author : content.author(),
                    generation != null ? generation : content.generation(),
                    content.pages(),
                    content.resolved()
            );

            return input.with(DataComponents.WRITTEN_BOOK_CONTENT, updated);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetComponents(@NotNull List<LootPredicate> predicates, @NotNull DataComponentMap changes) implements LootFunction {
        public static final @NotNull StructCodec<SetComponents> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetComponents::predicates,
                "components", DataComponent.PATCH_CODEC, SetComponents::changes,
                SetComponents::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            return input.with(builder -> {
                for (var entry : this.changes.entrySet()) {
                    if (entry.value() == null) {
                        builder.remove(entry.component());
                    } else {
                        this.setComponent(builder, entry.component(), entry.value());
                    }
                }
            });
        }

        @SuppressWarnings("unchecked")
        private <T> void setComponent(@NotNull ItemStack.Builder builder, @NotNull DataComponent<T> component, Object value) {
            builder.set(component, (T) value);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetContents(@NotNull List<LootPredicate> predicates, @NotNull List<LootEntry> entries,
                       @NotNull DataComponent<List<ItemStack>> type) implements LootFunction {
        public static final @NotNull StructCodec<SetContents> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetContents::predicates,
                "modifier", LootEntry.CODEC.list(), SetContents::entries,
                "type", Template.CONTAINER, SetContents::type,
                SetContents::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            if (input.isAir()) {
                return input;
            }

            var contents = new ArrayList<ItemStack>();

            for (var entry : this.entries) {
                for (var choice : entry.requestChoices(context)) {
                    for (var item : choice.generate(context)) {
                        LootFunction.addStackSplits(contents, item);
                    }
                }
            }

            return input.with(this.type, contents);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetCount(@NotNull List<LootPredicate> predicates, @NotNull LootNumber count, boolean add) implements LootFunction {
        public static final @NotNull StructCodec<SetCount> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetCount::predicates,
                "count", LootNumber.CODEC, SetCount::count,
                "add", Codec.BOOLEAN.optional(false), SetCount::add,
                SetCount::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;
            return input.withAmount(amount -> (this.add ? amount : 0) + this.count.getInt(context));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetCustomData(@NotNull List<LootPredicate> predicates, @NotNull CompoundBinaryTag tag) implements LootFunction {
        public static final @NotNull StructCodec<SetCustomData> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetCustomData::predicates,
                "tag", Codec.STRING.transform(s -> {
                    try {
                        return TagStringIO.tagStringIO().asCompound(s);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, TagStringIO.tagStringIO()::asString), SetCustomData::tag,
                SetCustomData::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            var current = input.get(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            return input.with(DataComponents.CUSTOM_DATA, new CustomData(NBTUtils.merge(current.nbt(), this.tag)));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetCustomModelData(@NotNull List<LootPredicate> predicates,
                              @Nullable ListOperation.WithValues<LootNumber> floats,
                              @Nullable ListOperation.WithValues<Boolean> flags,
                              @Nullable ListOperation.WithValues<String> strings,
                              @Nullable ListOperation.WithValues<LootNumber> colors) implements LootFunction {
        public static final @NotNull StructCodec<SetCustomModelData> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetCustomModelData::predicates,
                "floats", ListOperation.WithValues.codec(LootNumber.CODEC).optional(), SetCustomModelData::floats,
                "flags", ListOperation.WithValues.codec(Codec.BOOLEAN).optional(), SetCustomModelData::flags,
                "strings", ListOperation.WithValues.codec(Codec.STRING).optional(), SetCustomModelData::strings,
                "colors", ListOperation.WithValues.codec(LootNumber.CODEC).optional(), SetCustomModelData::colors,
                SetCustomModelData::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            var current = input.get(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of()));
            var updatedFloats = applyFloats(this.floats, current.floats(), context);
            var updatedFlags = applyValues(this.flags, current.flags());
            var updatedStrings = applyValues(this.strings, current.strings());
            var updatedColors = applyColors(this.colors, current.colors(), context);

            return input.with(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(updatedFloats, updatedFlags, updatedStrings, updatedColors));
        }

        private static @NotNull List<Float> applyFloats(@Nullable ListOperation.WithValues<LootNumber> operation,
                                                        @NotNull List<Float> current,
                                                        @NotNull LootContext context) {
            if (operation == null) {

                return current;
            }

            return operation.operation().apply(
                    operation.values().stream().map(value -> (float) value.getDouble(context)).toList(), current
            );
        }

        private static @NotNull List<RGBLike> applyColors(@Nullable ListOperation.WithValues<LootNumber> operation,
                                                          @NotNull List<RGBLike> current,
                                                          @NotNull LootContext context) {
            if (operation == null) {

                return current;
            }

            return operation.operation().apply(
                    operation.values().stream().map(value -> (RGBLike) new Color(value.getInt(context))).toList(), current
            );
        }

        private static <T> @NotNull List<T> applyValues(@Nullable ListOperation.WithValues<T> operation,
                                                        @NotNull List<T> current) {
            return operation == null ? current : operation.operation().apply(operation.values(), current);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetDamage(@NotNull List<LootPredicate> predicates, @NotNull LootNumber damage, boolean add) implements LootFunction {
        public static final @NotNull StructCodec<SetDamage> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetDamage::predicates,
                "damage", LootNumber.CODEC, SetDamage::damage,
                "add", Codec.BOOLEAN.optional(false), SetDamage::add,
                SetDamage::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            int maxDamage = input.get(DataComponents.MAX_DAMAGE, -1);
            if (maxDamage == -1) return input;

            double damage = input.get(DataComponents.DAMAGE, 0) / (double) maxDamage;

            double currentDura = add ? 1 - damage : 0;
            double newDura = Math.max(0, Math.min(1, currentDura + this.damage.getDouble(context)));

            double newDamage = 1 - newDura;

            return input.with(DataComponents.DAMAGE, (int) Math.floor(newDamage * maxDamage));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetEnchantments(@NotNull List<LootPredicate> predicates, @NotNull Map<RegistryKey<Enchantment>, LootNumber> enchantments, boolean add) implements LootFunction {
        public static final @NotNull StructCodec<SetEnchantments> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetEnchantments::predicates,
                "enchantments", RegistryKey.codec(Registries::enchantment).mapValue(LootNumber.CODEC).optional(Map.of()), SetEnchantments::enchantments,
                "add", Codec.BOOLEAN.optional(false), SetEnchantments::add,
                SetEnchantments::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            var item = input.material().equals(Material.BOOK)
                    ? input.builder().material(Material.ENCHANTED_BOOK).build()
                    : input;

            return EnchantmentUtils.modifyItem(item, enchantmentMap -> {
                this.enchantments.forEach((enchantment, number) -> {
                    var level = number.getInt(context);

                    if (this.add) {
                        level += enchantmentMap.getOrDefault(enchantment, 0);
                    }

                    enchantmentMap.put(enchantment, Math.max(0, Math.min(255, level)));
                });
            });
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetFireworkExplosion(@NotNull List<LootPredicate> predicates, @Nullable FireworkExplosion.Shape shape,
                                @Nullable List<RGBLike> colors, @Nullable List<RGBLike> fadeColors,
                                @Nullable Boolean trail, @Nullable Boolean twinkle) implements LootFunction {
        public static final @NotNull StructCodec<SetFireworkExplosion> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetFireworkExplosion::predicates,
                "shape", Codec.Enum(FireworkExplosion.Shape.class).optional(), SetFireworkExplosion::shape,
                "colors", Color.CODEC.list().optional(), SetFireworkExplosion::colors,
                "fade_colors", Color.CODEC.list().optional(), SetFireworkExplosion::fadeColors,
                "trail", Codec.BOOLEAN.optional(), SetFireworkExplosion::trail,
                "twinkle", Codec.BOOLEAN.optional(), SetFireworkExplosion::twinkle,
                SetFireworkExplosion::new
        );

        private static final @NotNull FireworkExplosion DEFAULT = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, List.of(), List.of(), false, false);

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            FireworkExplosion firework = input.get(DataComponents.FIREWORK_EXPLOSION, DEFAULT);

            FireworkExplosion updated = new FireworkExplosion(
                    this.shape != null ? this.shape : firework.shape(),
                    this.colors != null ? this.colors : firework.colors(),
                    this.fadeColors != null ? this.fadeColors : firework.fadeColors(),
                    this.trail != null ? this.trail : firework.hasTrail(),
                    this.twinkle != null ? this.twinkle : firework.hasTwinkle()
            );

            return input.with(DataComponents.FIREWORK_EXPLOSION, updated);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetFireworks(@NotNull List<LootPredicate> predicates, @Nullable Integer flightDuration, @Nullable ListOperation.WithValues<FireworkExplosion> explosions) implements LootFunction {
        private static final @NotNull Codec<Integer> UNSIGNED_BYTE_CODEC = Codec.INT.transform(value -> {
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Flight duration must be an unsigned byte");
            }

            return value;
        }, value -> value);

        public static final @NotNull StructCodec<SetFireworks> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetFireworks::predicates,
                "flight_duration", UNSIGNED_BYTE_CODEC.optional(), SetFireworks::flightDuration,
                "explosions", ListOperation.WithValues.codec(FireworkExplosion.CODEC, 256).optional(), SetFireworks::explosions,
                SetFireworks::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            FireworkList list = input.get(DataComponents.FIREWORKS, FireworkList.EMPTY);

            FireworkList updated = new FireworkList(
                    this.flightDuration != null ? this.flightDuration : list.flightDuration(),
                    this.explosions == null ? list.explosions() : this.explosions.operation().apply(this.explosions.values(), list.explosions())
            );

            return input.with(DataComponents.FIREWORKS, updated);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetInstrument(@NotNull List<LootPredicate> predicates, @NotNull RegistryTag<Instrument> options) implements LootFunction {
        public static final @NotNull StructCodec<SetInstrument> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetInstrument::predicates,
                "options", RegistryTag.codec(Registries::instrument), SetInstrument::options,
                SetInstrument::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;
            if (options.size() == 0) return input;

            int count = context.require(LootContext.RANDOM).nextInt(options.size());

            var it = options.iterator();
            for (int i = 0; i < count; i++) {
                it.next();
            }

            return input.with(DataComponents.INSTRUMENT, it.next());
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetItem(@NotNull List<LootPredicate> predicates, @NotNull Material item) implements LootFunction {
        public static final @NotNull StructCodec<SetItem> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetItem::predicates,
                "item", Material.CODEC, SetItem::item,
                SetItem::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            return input.builder().material(item).build();
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetLootTable(@NotNull List<LootPredicate> predicates, @NotNull Key name, long seed) implements LootFunction {
        public static final @NotNull StructCodec<SetLootTable> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetLootTable::predicates,
                "name", Codec.KEY, SetLootTable::name,
                "seed", Codec.LONG.optional(0L), SetLootTable::seed,
                SetLootTable::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;
            if (input.isAir()) return input;

            return input.with(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(name.asString(), seed));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetLore(@NotNull List<LootPredicate> predicates, @NotNull List<Component> lore,
                   @NotNull ListOperation operation, @Nullable RelevantEntity entity) implements LootFunction {
        public static final @NotNull StructCodec<SetLore> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetLore::predicates,
                "lore", Codec.COMPONENT.list(256), SetLore::lore,
                StructCodec.INLINE, ListOperation.codec(256), SetLore::operation,
                "entity", RelevantEntity.CODEC.optional(), SetLore::entity,
                SetLore::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            var components = input.get(DataComponents.LORE, List.of());
            var resolutionEntity = this.entity == null ? null : context.get(this.entity.key());
            var resolvedLore = this.lore.stream()
                    .map(line -> context.vanilla().resolveComponent(line, resolutionEntity))
                    .toList();

            return input.with(DataComponents.LORE, this.operation.apply(resolvedLore, components, 256));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetName(@NotNull List<LootPredicate> predicates, @Nullable Component name,
                   @Nullable RelevantEntity entity, @NotNull Target target) implements LootFunction {
        public static final @NotNull StructCodec<SetName> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetName::predicates,
                "name", Codec.COMPONENT.optional(), SetName::name,
                "entity", RelevantEntity.CODEC.optional(), SetName::entity,
                "target", Target.CODEC.optional(Target.CUSTOM_NAME), SetName::target,
                SetName::new
        );

        public enum Target {
            ITEM_NAME("item_name", DataComponents.ITEM_NAME),
            CUSTOM_NAME("custom_name", DataComponents.CUSTOM_NAME);

            public static final @NotNull Codec<Target> CODEC = Codec.STRING.transform(value -> switch (value) {
                case "item_name" -> ITEM_NAME;
                case "custom_name" -> CUSTOM_NAME;
                default -> throw new IllegalArgumentException("Unknown set name target: " + value);
            }, Target::id);

            private final String id;
            private final DataComponent<Component> component;

            Target(String id, DataComponent<Component> component) {
                this.id = id;
                this.component = component;
            }

            public String id() {
                return this.id;
            }

            public DataComponent<Component> component() {
                return this.component;
            }
        }

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) return input;

            if (this.name == null) return input;

            var resolutionEntity = this.entity == null ? null : context.get(this.entity.key());
            var component = context.vanilla().resolveComponent(this.name, resolutionEntity);

            return input.with(this.target.component(), component);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetOminousBottleAmplifier(@NotNull List<LootPredicate> predicates, @NotNull LootNumber amplifier) implements LootFunction {
        public static final @NotNull StructCodec<SetOminousBottleAmplifier> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetOminousBottleAmplifier::predicates,
                "amplifier", LootNumber.CODEC, SetOminousBottleAmplifier::amplifier,
                SetOminousBottleAmplifier::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            int amplifier = Math.max(0, Math.min(this.amplifier.getInt(context), 4));

            return input.with(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, amplifier);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetPotion(@NotNull List<LootPredicate> predicates, @NotNull Key id) implements LootFunction {
        private static final @NotNull Codec<Key> POTION_CODEC = Codec.KEY.transform(key -> {

            if (PotionType.fromKey(key) == null) {
                throw new IllegalArgumentException("Unknown potion: " + key);
            }

            return key;
        }, key -> key);

        public static final @NotNull StructCodec<SetPotion> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetPotion::predicates,
                "id", POTION_CODEC, SetPotion::id,
                SetPotion::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            var potion = PotionType.fromKey(this.id);

            if (potion == null) {
                return input;
            }

            return input.with(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetRandomDyes(@NotNull List<LootPredicate> predicates, @NotNull LootNumber numberOfDyes) implements LootFunction {
        public static final @NotNull StructCodec<SetRandomDyes> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetRandomDyes::predicates,
                "number_of_dyes", LootNumber.CODEC, SetRandomDyes::numberOfDyes,
                SetRandomDyes::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) return input;

            var count = this.numberOfDyes.getInt(context);
            if (count <= 0) return input;

            var random = context.require(LootContext.RANDOM);
            var currentColor = input.get(DataComponents.DYED_COLOR);
            var redTotal = currentColor == null ? 0 : currentColor.red();
            var greenTotal = currentColor == null ? 0 : currentColor.green();
            var blueTotal = currentColor == null ? 0 : currentColor.blue();
            var intensityTotal = currentColor == null ? 0 : Math.max(currentColor.red(), Math.max(currentColor.green(), currentColor.blue()));
            var colorCount = currentColor == null ? 0 : 1;

            for (var dyeCount = 0; dyeCount < count; dyeCount++) {
                var dye = DyeColor.values()[random.nextInt(DyeColor.values().length)];
                redTotal += dye.red();
                greenTotal += dye.green();
                blueTotal += dye.blue();
                intensityTotal += Math.max(dye.red(), Math.max(dye.green(), dye.blue()));
                colorCount++;
            }

            var red = redTotal / colorCount;
            var green = greenTotal / colorCount;
            var blue = blueTotal / colorCount;
            var averageIntensity = (float) intensityTotal / colorCount;
            var resultIntensity = Math.max(red, Math.max(green, blue));
            red = (int) (red * averageIntensity / resultIntensity);
            green = (int) (green * averageIntensity / resultIntensity);
            blue = (int) (blue * averageIntensity / resultIntensity);

            return input.withAmount(1).with(DataComponents.DYED_COLOR, new Color(red, green, blue));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetRandomPotion(@NotNull List<LootPredicate> predicates, @Nullable RegistryTag<PotionType> options) implements LootFunction {
        public static final @NotNull StructCodec<SetRandomPotion> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetRandomPotion::predicates,
                "options", RegistryTag.codec(Registries::potionType).optional(), SetRandomPotion::options,
                SetRandomPotion::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) return input;

            var potions = new ArrayList<PotionType>();
            if (this.options == null) {
                potions.addAll(PotionType.values());
            } else {
                for (var key : this.options) {
                    var potion = PotionType.fromKey(key.key());
                    if (potion != null) potions.add(potion);
                }
            }

            if (potions.isEmpty()) return input;

            var potion = potions.get(context.require(LootContext.RANDOM).nextInt(potions.size()));
            return input.with(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetStewEffect(@NotNull List<LootPredicate> predicates, @NotNull List<AddedEffect> effects) implements LootFunction {
        private static final @NotNull Codec<List<AddedEffect>> EFFECTS_CODEC = AddedEffect.CODEC.list().transform(
                SetStewEffect::validateEffects,
                effects -> effects
        );

        public static final @NotNull StructCodec<SetStewEffect> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetStewEffect::predicates,
                "effects", EFFECTS_CODEC.optional(List.of()), SetStewEffect::effects,
                SetStewEffect::new
        );

        private static @NotNull List<AddedEffect> validateEffects(@NotNull List<AddedEffect> effects) {
            var seenEffects = new HashSet<PotionEffect>();

            for (var effect : effects) {
                if (!seenEffects.add(effect.effect())) {
                    throw new IllegalArgumentException("Encountered duplicate mob effect: '" + effect.effect() + "'");
                }
            }

            return effects;
        }

        public record AddedEffect(@NotNull PotionEffect effect, @NotNull LootNumber duration) {
            public static final @NotNull StructCodec<AddedEffect> CODEC = StructCodec.struct(
                    "type", PotionEffect.CODEC, AddedEffect::effect,
                    "duration", LootNumber.CODEC, AddedEffect::duration,
                    AddedEffect::new
            );
        }

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(this.predicates, context)) {
                return input;
            }

            if (!Material.SUSPICIOUS_STEW.equals(input.material()) || this.effects.isEmpty()) {
                return input;
            }

            var effect = this.effects.get(context.require(LootContext.RANDOM).nextInt(this.effects.size()));

            var duration = effect.duration().getInt(context);
            if (!effect.effect().instantaneous()) {
                duration *= ServerFlag.SERVER_TICKS_PER_SECOND;
            }

            var added = new SuspiciousStewEffects.Effect(effect.effect(), duration);

            var current = input.get(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
            return input.with(DataComponents.SUSPICIOUS_STEW_EFFECTS, current.with(added));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetWritableBookPages(@NotNull List<LootPredicate> predicates, @NotNull List<FilteredText<String>> pages, @NotNull ListOperation operation) implements LootFunction {
        public static final @NotNull StructCodec<SetWritableBookPages> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetWritableBookPages::predicates,
                "pages", FilteredText.STRING_CODEC.list(100), SetWritableBookPages::pages,
                StructCodec.INLINE, ListOperation.codec(100), SetWritableBookPages::operation,
                SetWritableBookPages::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            WritableBookContent content = input.get(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);

            return input.with(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(operation.apply(pages, content.pages(), 100)));
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

    record SetWrittenBookPages(@NotNull List<LootPredicate> predicates, @NotNull List<FilteredText<Component>> pages, @NotNull ListOperation operation) implements LootFunction {
        public static final @NotNull StructCodec<SetWrittenBookPages> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), SetWrittenBookPages::predicates,
                "pages", FilteredText.COMPONENT_CODEC.list(), SetWrittenBookPages::pages,
                StructCodec.INLINE, ListOperation.CODEC, SetWrittenBookPages::operation,
                SetWrittenBookPages::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            WrittenBookContent content = input.get(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY);
            WrittenBookContent updated = new WrittenBookContent(content.title(), content.author(), content.generation(), operation.apply(pages, content.pages()), content.resolved());

            return input.with(DataComponents.WRITTEN_BOOK_CONTENT, updated);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }
    
    record Sequence(@NotNull List<LootFunction> functions) implements LootFunction {
        public static final @NotNull StructCodec<Sequence> CODEC = StructCodec.struct(
                "functions", LootFunction.CODEC.list(), Sequence::functions,
                Sequence::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            return LootFunction.apply(functions, input, context);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }
    
    record ToggleTooltips(@NotNull List<LootPredicate> predicates, @NotNull Map<DataComponent<?>, Boolean> toggles) implements LootFunction {
        public static final @NotNull StructCodec<ToggleTooltips> CODEC = StructCodec.struct(
                "conditions", LootPredicate.CODEC.list().optional(List.of()), ToggleTooltips::predicates,
                "toggles", DataComponent.CODEC.mapValue(StructCodec.BOOLEAN), ToggleTooltips::toggles,
                ToggleTooltips::new
        );

        @Override
        public @NotNull ItemStack apply(@NotNull ItemStack input, @NotNull LootContext context) {
            if (!LootPredicate.all(predicates, context)) return input;

            TooltipDisplay display = input.get(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.EMPTY);

            for (var entry : toggles.entrySet()) {
                display = entry.getValue() ? display.with(entry.getKey()) : display.without(entry.getKey());
            }

            return input.with(DataComponents.TOOLTIP_DISPLAY, display);
        }

        @Override
        public @NotNull StructCodec<? extends LootFunction> codec() {
            return CODEC;
        }
    }

}
