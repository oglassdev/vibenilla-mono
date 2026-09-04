package net.goldenstack.loot.util;

import net.goldenstack.loot.LootContext;
import net.goldenstack.loot.LootFunction;
import net.goldenstack.loot.LootPredicate;
import net.goldenstack.loot.LootSlotSource;
import net.goldenstack.loot.LootTable;
import net.goldenstack.loot.util.predicate.FluidPredicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.codec.Codec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.EquipmentHandler;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.TagKey;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public interface VanillaInterface {

    static @NotNull VanillaInterface defaults() {
        return new VanillaInterface() {
            @Override
            public @Nullable Integer score(@NotNull Entity entity, @NotNull String objective) {
                return null;
            }

            @Override
            public @Nullable Integer score(@NotNull String name, @NotNull String objective) {
                return null;
            }

            @Override
            public @NotNull BinaryTag serializeEntity(@NotNull Entity entity) {
                return CompoundBinaryTag.empty();
            }

            @Override
            public @NotNull ItemStack enchant(@NotNull Random random, @NotNull ItemStack item, int levels, @Nullable RegistryTag<Enchantment> enchantments) {
                return item;
            }

            @Override
            public @Nullable ItemStack smelt(@NotNull ItemStack input) {
                return null;
            }

            @Override
            public @Nullable LootTable tableRegistry(@NotNull Key key) {
                return null;
            }

            @Override
            public @Nullable LootPredicate predicateRegistry(@NotNull Key key) {
                return null;
            }

            @Override
            public @Nullable LootFunction functionRegistry(@NotNull Key key) {
                return null;
            }

            @Override
            public @Nullable CompoundBinaryTag commandStorage(@NotNull Key key) {
                return null;
            }
        };
    }

    @NotNull Tag<Component> CUSTOM_NAME = Tag.Component("CustomName");

    @NotNull Tag<List<Material>> DECORATED_POT_SHERDS = Tag.String("sherds")
            .map(Key::key, Key::asString)
            .map(Material::fromKey, Material::key)
            .list().defaultValue(List::of);

    @NotNull Tag<List<ItemStack>> CONTAINER_ITEMS = Tag.ItemStack("Items").list().defaultValue(List::of);

    @Nullable Integer score(@NotNull Entity entity, @NotNull String objective);

    @Nullable Integer score(@NotNull String name, @NotNull String objective);

    @NotNull BinaryTag serializeEntity(@NotNull Entity entity);

    default @NotNull Component resolveComponent(@NotNull Component component, @Nullable Entity entity) {
        return component;
    }

    default @Nullable Double entityFallDistance(@NotNull Entity entity) {
        return null;
    }

    default @Nullable Boolean entityBoolean(@NotNull Key attribute, @NotNull Entity entity) {
        return null;
    }

    default @Nullable Integer entityInteger(@NotNull Key attribute, @NotNull Entity entity) {
        return null;
    }

    default @Nullable List<Entity> lightningStruckEntities(@NotNull Entity lightning) {
        return null;
    }

    default @Nullable Entity entityLookedAt(@NotNull Entity entity) {
        return null;
    }

    default @Nullable Boolean locationStructure(@NotNull Key structure,
                                                @NotNull Instance instance,
                                                @NotNull Point point) {
        return null;
    }

    default @Nullable Boolean locationSmokey(@NotNull Instance instance, @NotNull Point point) {
        return null;
    }

    default @Nullable Boolean locationFluid(@NotNull FluidPredicate predicate,
                                             @NotNull Instance instance,
                                             @NotNull Point point) {
        return null;
    }

    default @Nullable Boolean locationCanSeeSky(@NotNull Instance instance, @NotNull Point point) {
        return null;
    }

    default @NotNull ItemStack explorationMap(@NotNull ItemStack input,
                                               @NotNull Instance world,
                                               @NotNull Point origin,
                                               @NotNull TagKey<?> destination,
                                               @NotNull Key decoration,
                                               byte zoom,
                                               int searchRadius,
                                               boolean skipExistingChunks) {
        return input;
    }

    default @NotNull Random randomSequence(@NotNull Key sequence, @NotNull Random fallback) {
        return fallback;
    }

    default @Nullable BinaryTag environmentAttribute(@NotNull Key attribute, @NotNull LootContext context) {
        return null;
    }

    default @Nullable LootSlotSource slotSource(@NotNull Key name, @NotNull LootContext context) {
        return null;
    }

    default @Nullable List<ItemStack> dynamicDrops(@NotNull Key name, @NotNull LootContext context) {
        return null;
    }

    default boolean matchesPlayerStats(@NotNull Player player,
                                       @NotNull List<Codec.RawValue> stats,
                                       @NotNull LootContext context) {
        return stats.isEmpty();
    }

    default boolean matchesPlayerRecipes(@NotNull Player player,
                                         @NotNull Map<String, Boolean> recipes,
                                         @NotNull LootContext context) {
        return recipes.isEmpty();
    }

    default boolean matchesPlayerAdvancements(@NotNull Player player,
                                              @NotNull Map<String, Codec.RawValue> advancements,
                                              @NotNull LootContext context) {
        return advancements.isEmpty();
    }

    default @NotNull List<ItemStack> slotRange(@NotNull String source,
                                               @NotNull List<Integer> slots,
                                               @NotNull LootContext context) {
        if (source.equals("container")) {
            var container = context.get(LootContext.CONTAINER);
            if (container == null) {
                return List.of();
            }

            var items = new ArrayList<ItemStack>();
            for (var slot : slots) {
                if (slot >= 0 && slot < container.size()) {
                    items.add(container.get(slot));
                }
            }

            return items;
        }

        var entity = switch (source) {
            case "this" -> context.get(LootContext.THIS_ENTITY);
            case "attacker" -> context.get(LootContext.ATTACKING_ENTITY);
            case "direct_attacker" -> context.get(LootContext.DIRECT_ATTACKING_ENTITY);
            case "attacking_player" -> context.get(LootContext.LAST_DAMAGE_PLAYER);
            case "target_entity" -> context.get(LootContext.TARGET_ENTITY);
            case "interacting_entity" -> context.get(LootContext.INTERACTING_ENTITY);
            default -> null;
        };

        if (entity instanceof Player player) {
            return playerSlots(player, slots);
        }

        if (entity instanceof EquipmentHandler equipment) {
            return equipmentSlots(equipment, slots);
        }

        if (source.equals("block_entity")) {
            var block = context.get(LootContext.BLOCK_STATE);
            if (block == null) {
                return List.of();
            }

            return indexedSlots(block.getTag(CONTAINER_ITEMS), slots);
        }

        return List.of();
    }

    private static @NotNull List<ItemStack> playerSlots(@NotNull Player player, @NotNull List<Integer> slots) {
        var items = new ArrayList<ItemStack>();
        for (var slot : slots) {
            if (slot >= 0 && slot < 36) {
                items.add(player.getInventory().getItemStack(slot));
                continue;
            }

            if (slot == 499) {
                items.add(player.getInventory().getCursorItem());
                continue;
            }

            if (slot >= 500 && slot < 504) {
                items.add(player.getInventory().getItemStack(37 + slot - 500));
                continue;
            }

            var equipment = equipmentSlot(slot);
            if (equipment != null) {
                items.add(player.getEquipment(equipment));
            }
        }

        return items;
    }

    private static @NotNull List<ItemStack> equipmentSlots(@NotNull EquipmentHandler equipment,
                                                           @NotNull List<Integer> slots) {
        var items = new ArrayList<ItemStack>();
        for (var slot : slots) {
            var equipmentSlot = equipmentSlot(slot);
            if (equipmentSlot != null) {
                items.add(equipment.getEquipment(equipmentSlot));
            }
        }

        return items;
    }

    private static @Nullable EquipmentSlot equipmentSlot(int slot) {
        return switch (slot) {
            case 98 -> EquipmentSlot.MAIN_HAND;
            case 99 -> EquipmentSlot.OFF_HAND;
            case 100 -> EquipmentSlot.BOOTS;
            case 101 -> EquipmentSlot.LEGGINGS;
            case 102 -> EquipmentSlot.CHESTPLATE;
            case 103 -> EquipmentSlot.HELMET;
            case 105 -> EquipmentSlot.BODY;
            case 106 -> EquipmentSlot.SADDLE;
            default -> null;
        };
    }

    private static @NotNull List<ItemStack> indexedSlots(@NotNull List<ItemStack> source,
                                                         @NotNull List<Integer> slots) {
        var items = new ArrayList<ItemStack>();
        for (var slot : slots) {
            if (slot >= 0 && slot < source.size()) {
                items.add(source.get(slot));
            }
        }

        return items;
    }

    @NotNull ItemStack enchant(@NotNull Random random, @NotNull ItemStack item, int levels, @Nullable RegistryTag<Enchantment> enchantments);

    @Nullable ItemStack smelt(@NotNull ItemStack input);

    @Nullable LootTable tableRegistry(@NotNull Key key);

    @Nullable LootPredicate predicateRegistry(@NotNull Key key);

    @Nullable LootFunction functionRegistry(@NotNull Key key);

    @Nullable CompoundBinaryTag commandStorage(@NotNull Key key);

}
