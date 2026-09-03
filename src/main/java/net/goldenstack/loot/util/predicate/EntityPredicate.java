package net.goldenstack.loot.util.predicate;

import net.goldenstack.loot.LootContext;
import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.AgeableMobMeta;
import net.minestom.server.entity.metadata.animal.SheepMeta;
import net.minestom.server.entity.metadata.cube.AbstractCubeMeta;
import net.minestom.server.entity.metadata.monster.raider.RaiderMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.predicate.DataComponentPredicates;
import net.minestom.server.instance.block.predicate.NbtPredicate;
import net.minestom.server.item.ItemStack;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public interface EntityPredicate {

    @NotNull Tag<List<String>> ENTITY_TAGS = Tag.String("entity_tags").list().defaultValue(List::of);

    @NotNull Codec<EntityPredicate> CODEC = Codec.Recursive(EntityPredicate::createCodec);

    private static @NotNull Codec<EntityPredicate> createCodec(@NotNull Codec<EntityPredicate> recursiveCodec) {
        return Codec.STRING.mapValueTyped(key -> codecFor(key, recursiveCodec))
                .transform(Composite::new, predicate -> ((Composite) predicate).parts());
    }

    private static @NotNull Codec<EntityPredicate> codecFor(@NotNull String key, @NotNull Codec<EntityPredicate> recursiveCodec) {
        return switch (key) {
            case "entity_type", "minecraft:entity_type" -> asPredicate(EntityTypePredicate.CODEC);
            case "stepping_on", "minecraft:stepping_on" -> asPredicate(LocationPredicate.CODEC.transform(EntitySteppingOn::new, EntitySteppingOn::predicate));
            case "movement_affected_by", "minecraft:movement_affected_by" -> asPredicate(LocationPredicate.CODEC.transform(EntityMovementAffectedBy::new, EntityMovementAffectedBy::predicate));
            case "distance", "minecraft:distance" -> asPredicate(EntityDistancePredicate.CODEC);
            case "movement", "minecraft:movement" -> asPredicate(EntityMovementPredicate.CODEC);
            case "effects", "minecraft:effects" -> asPredicate(EntityEffectsPredicate.CODEC);
            case "nbt", "minecraft:nbt" -> asPredicate(EntityNbtPredicate.CODEC);
            case "flags", "minecraft:flags" -> asPredicate(EntityFlagsPredicate.CODEC);
            case "equipment", "minecraft:equipment" -> asPredicate(EntityEquipmentPredicate.CODEC);
            case "periodic_tick", "minecraft:periodic_tick" -> asPredicate(PeriodicTickPredicate.CODEC);
            case "entity_tags", "minecraft:entity_tags" -> asPredicate(EntityTagPredicate.CODEC);
            case "location", "minecraft:location" -> asPredicate(LocationPredicate.CODEC.transform(EntityLocation::new, EntityLocation::predicate));
            case "vehicle", "minecraft:vehicle" -> asPredicate(recursiveCodec.transform(EntityVehicle::new, EntityVehicle::predicate));
            case "passenger", "minecraft:passenger" -> asPredicate(recursiveCodec.transform(EntityPassenger::new, EntityPassenger::predicate));
            case "targeted_entity", "minecraft:targeted_entity" -> asPredicate(recursiveCodec.transform(EntityTargeted::new, EntityTargeted::predicate));
            case "team", "minecraft:team" -> asPredicate(TeamPredicate.CODEC);
            case "slots", "minecraft:slots" -> asPredicate(EntitySlotsPredicate.CODEC);
            case "components", "minecraft:components" -> asPredicate(EntityComponentsPredicate.CODEC);
            case "predicates", "minecraft:predicates" -> asPredicate(EntityComponentsPredicate.CODEC);
            case "type_specific/lightning", "minecraft:type_specific/lightning" -> asPredicate(LightningPredicate.codec(recursiveCodec));
            case "type_specific/fishing_hook", "minecraft:type_specific/fishing_hook" -> asPredicate(FishingHookPredicate.CODEC);
            case "type_specific/player", "minecraft:type_specific/player" -> asPredicate(PlayerPredicate.codec(recursiveCodec));
            case "type_specific/raider", "minecraft:type_specific/raider" -> asPredicate(RaiderPredicate.CODEC);
            case "type_specific/sheep", "minecraft:type_specific/sheep" -> asPredicate(SheepPredicate.CODEC);
            case "type_specific/cube_mob", "minecraft:type_specific/cube_mob" -> asPredicate(CubeMobPredicate.CODEC);
            default -> throw new IllegalArgumentException("Unknown entity predicate: " + key);
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends EntityPredicate> @NotNull Codec<EntityPredicate> asPredicate(@NotNull Codec<T> codec) {
        return codec.transform(value -> value, value -> (T) value);
    }

    boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity);

    default boolean test(@NotNull Instance instance,
                         @Nullable Point position,
                         @Nullable Entity entity,
                         @NotNull VanillaInterface vanilla) {
        return this.test(instance, position, entity);
    }

    record Composite(@NotNull Map<String, EntityPredicate> parts) implements EntityPredicate {

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            if (entity == null) {

                return false;
            }

            for (var predicate : this.parts.values()) {
                if (!predicate.test(instance, position, entity)) {

                    return false;
                }
            }

            return true;
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            if (entity == null) {

                return false;
            }

            for (var predicate : this.parts.values()) {
                if (!predicate.test(instance, position, entity, vanilla)) {

                    return false;
                }
            }

            return true;
        }
    }

    record EntityTypePredicate(@NotNull List<EntityType> types) implements EntityPredicate {
        public static final @NotNull Codec<EntityTypePredicate> CODEC = EntityType.CODEC.list()
                .orElse(EntityType.CODEC, List::of)
                .transform(EntityTypePredicate::new, EntityTypePredicate::types);

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity != null && this.types.contains(entity.getEntityType());
        }
    }

    record EntityFlagsPredicate(@Nullable Boolean isOnGround,
                                 @Nullable Boolean isOnFire,
                                 @Nullable Boolean isSneaking,
                                 @Nullable Boolean isSprinting,
                                 @Nullable Boolean isSwimming,
                                 @Nullable Boolean isFlying,
                                 @Nullable Boolean isBaby,
                                 @Nullable Boolean isInWater,
                                 @Nullable Boolean isFallFlying) implements EntityPredicate {
        public static final @NotNull StructCodec<EntityFlagsPredicate> CODEC = StructCodec.struct(
                "is_on_ground", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isOnGround,
                "is_on_fire", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isOnFire,
                "is_sneaking", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isSneaking,
                "is_sprinting", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isSprinting,
                "is_swimming", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isSwimming,
                "is_flying", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isFlying,
                "is_baby", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isBaby,
                "is_in_water", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isInWater,
                "is_fall_flying", Codec.BOOLEAN.optional(), EntityFlagsPredicate::isFallFlying,
                EntityFlagsPredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            if (entity == null) {

                return false;
            }

            if (this.isOnGround != null && entity.isOnGround() != this.isOnGround) {

                return false;
            }

            if (this.isOnFire != null && entity.isOnFire() != this.isOnFire) {

                return false;
            }

            if (this.isSneaking != null && entity.isSneaking() != this.isSneaking) {

                return false;
            }

            if (this.isSprinting != null && entity.isSprinting() != this.isSprinting) {

                return false;
            }

            if (this.isSwimming != null && entity.getEntityMeta().isSwimming() != this.isSwimming) {

                return false;
            }

            var isFlying = entity instanceof Player player && player.isFlying()
                    || entity instanceof LivingEntity livingEntity && livingEntity.isFlyingWithElytra();
            if (this.isFlying != null && isFlying != this.isFlying) {

                return false;
            }

            var isBaby = entity.getEntityMeta() instanceof AgeableMobMeta ageableMobMeta && ageableMobMeta.isBaby();
            if (this.isBaby != null && isBaby != this.isBaby) {

                return false;
            }

            var isInWater = instance.getBlock(entity.getPosition()).fluid();
            if (this.isInWater != null && isInWater != this.isInWater) {

                return false;
            }

            var isFallFlying = entity instanceof LivingEntity livingEntity && livingEntity.isFlyingWithElytra();
            return this.isFallFlying == null || isFallFlying == this.isFallFlying;
        }
    }

    record EntityDistancePredicate(@NotNull LootNumberRange x,
                                   @NotNull LootNumberRange y,
                                   @NotNull LootNumberRange z,
                                   @NotNull LootNumberRange horizontal,
                                   @NotNull LootNumberRange absolute) implements EntityPredicate {
        public static final @NotNull StructCodec<EntityDistancePredicate> CODEC = StructCodec.struct(
                "x", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityDistancePredicate::x,
                "y", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityDistancePredicate::y,
                "z", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityDistancePredicate::z,
                "horizontal", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityDistancePredicate::horizontal,
                "absolute", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityDistancePredicate::absolute,
                EntityDistancePredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            if (position == null || entity == null) {

                return false;
            }

            var deltaX = Math.abs((float) (position.x() - entity.getPosition().x()));
            var deltaY = Math.abs((float) (position.y() - entity.getPosition().y()));
            var deltaZ = Math.abs((float) (position.z() - entity.getPosition().z()));
            var horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            var absoluteDistance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
            var context = LootContext.from(VanillaInterface.defaults(), Map.of());
            return this.x.check(context, deltaX)
                    && this.y.check(context, deltaY)
                    && this.z.check(context, deltaZ)
                    && this.horizontal.check(context, horizontalDistance)
                    && this.absolute.check(context, absoluteDistance);
        }
    }

    record EntityMovementPredicate(@NotNull LootNumberRange x,
                                   @NotNull LootNumberRange y,
                                   @NotNull LootNumberRange z,
                                   @NotNull LootNumberRange speed,
                                   @NotNull LootNumberRange horizontalSpeed,
                                   @NotNull LootNumberRange verticalSpeed,
                                   @NotNull LootNumberRange fallDistance) implements EntityPredicate {
        public static final @NotNull StructCodec<EntityMovementPredicate> CODEC = StructCodec.struct(
                "x", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityMovementPredicate::x,
                "y", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityMovementPredicate::y,
                "z", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityMovementPredicate::z,
                "speed", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityMovementPredicate::speed,
                "horizontal_speed", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityMovementPredicate::horizontalSpeed,
                "vertical_speed", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityMovementPredicate::verticalSpeed,
                "fall_distance", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EntityMovementPredicate::fallDistance,
                EntityMovementPredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return this.test(entity, 0.0);
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            return entity == null ? false : this.test(entity, vanilla.entityFallDistance(entity));
        }

        private boolean test(@Nullable Entity entity, @Nullable Double fallDistance) {
            if (entity == null || fallDistance == null) {

                return false;
            }

            var velocity = entity.getVelocity();
            var x = velocity.x() * 20.0;
            var y = velocity.y() * 20.0;
            var z = velocity.z() * 20.0;
            var speed = Math.sqrt(x * x + y * y + z * z);
            var horizontalSpeed = Math.sqrt(x * x + z * z);
            var verticalSpeed = Math.abs(y);
            var context = LootContext.from(VanillaInterface.defaults(), Map.of());
            return this.x.check(context, x)
                    && this.y.check(context, y)
                    && this.z.check(context, z)
                    && this.speed.check(context, speed)
                    && this.horizontalSpeed.check(context, horizontalSpeed)
                    && this.verticalSpeed.check(context, verticalSpeed)
                    && this.fallDistance.check(context, fallDistance);
        }
    }

    record EntitySteppingOn(@NotNull LocationPredicate predicate) implements EntityPredicate {
        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            if (entity == null || !entity.isOnGround()) {

                return false;
            }

            var entityPosition = entity.getPosition();
            var blockPosition = new Pos(entityPosition.blockX() + 0.5, entityPosition.blockY() - 0.5, entityPosition.blockZ() + 0.5);
            return this.predicate.test(instance, blockPosition);
        }
    }

    record EntityMovementAffectedBy(@NotNull LocationPredicate predicate) implements EntityPredicate {
        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            if (entity == null) {

                return false;
            }

            var entityPosition = entity.getPosition();
            var blockPosition = new Pos(entityPosition.blockX() + 0.5, entityPosition.blockY() - 0.5, entityPosition.blockZ() + 0.5);
            return this.predicate.test(instance, blockPosition);
        }
    }

    record EntityEffectsPredicate(@NotNull Map<PotionEffect, EffectPredicate> effects) implements EntityPredicate {
        public static final @NotNull Codec<EntityEffectsPredicate> CODEC = PotionEffect.CODEC.mapValue(EffectPredicate.CODEC)
                .transform(EntityEffectsPredicate::new, EntityEffectsPredicate::effects);

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            if (entity == null) {

                return false;
            }

            for (var entry : this.effects.entrySet()) {
                var effect = entity.getEffect(entry.getKey());
                if (effect == null || !entry.getValue().test(effect)) {

                    return false;
                }
            }

            return true;
        }
    }

    record EffectPredicate(@NotNull LootNumberRange amplifier,
                           @NotNull LootNumberRange duration,
                           @Nullable Boolean ambient,
                           @Nullable Boolean visible) {
        public static final @NotNull StructCodec<EffectPredicate> CODEC = StructCodec.struct(
                "amplifier", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EffectPredicate::amplifier,
                "duration", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), EffectPredicate::duration,
                "ambient", Codec.BOOLEAN.optional(), EffectPredicate::ambient,
                "visible", Codec.BOOLEAN.optional(), EffectPredicate::visible,
                EffectPredicate::new
        );

        public boolean test(@NotNull TimedPotion effect) {
            var context = LootContext.from(VanillaInterface.defaults(), Map.of());
            var potion = effect.potion();
            return this.amplifier.check(context, potion.amplifier())
                    && this.duration.check(context, potion.duration())
                    && (this.ambient == null || this.ambient == potion.isAmbient())
                    && (this.visible == null || this.visible == potion.hasParticles());
        }
    }

    record EntityNbtPredicate(@NotNull NbtPredicate predicate) implements EntityPredicate {
        public static final @NotNull Codec<EntityNbtPredicate> CODEC = NbtPredicate.CODEC.transform(EntityNbtPredicate::new, EntityNbtPredicate::predicate);

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity != null && this.predicate.test(VanillaInterface.defaults().serializeEntity(entity));
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            return entity != null && this.predicate.test(vanilla.serializeEntity(entity));
        }
    }

    record EntityEquipmentPredicate(@Nullable ItemPredicate head,
                                    @Nullable ItemPredicate chest,
                                    @Nullable ItemPredicate legs,
                                    @Nullable ItemPredicate feet,
                                    @Nullable ItemPredicate body,
                                    @Nullable ItemPredicate mainhand,
                                    @Nullable ItemPredicate offhand) implements EntityPredicate {
        public static final @NotNull StructCodec<EntityEquipmentPredicate> CODEC = StructCodec.struct(
                "head", ItemPredicate.CODEC.optional(), EntityEquipmentPredicate::head,
                "chest", ItemPredicate.CODEC.optional(), EntityEquipmentPredicate::chest,
                "legs", ItemPredicate.CODEC.optional(), EntityEquipmentPredicate::legs,
                "feet", ItemPredicate.CODEC.optional(), EntityEquipmentPredicate::feet,
                "body", ItemPredicate.CODEC.optional(), EntityEquipmentPredicate::body,
                "mainhand", ItemPredicate.CODEC.optional(), EntityEquipmentPredicate::mainhand,
                "offhand", ItemPredicate.CODEC.optional(), EntityEquipmentPredicate::offhand,
                EntityEquipmentPredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return this.test(instance, position, entity, VanillaInterface.defaults());
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            if (!(entity instanceof LivingEntity livingEntity)) {

                return false;
            }

            var context = LootContext.from(vanilla, Map.of());
            return this.matches(this.head, livingEntity.getEquipment(EquipmentSlot.HELMET), context)
                    && this.matches(this.chest, livingEntity.getEquipment(EquipmentSlot.CHESTPLATE), context)
                    && this.matches(this.legs, livingEntity.getEquipment(EquipmentSlot.LEGGINGS), context)
                    && this.matches(this.feet, livingEntity.getEquipment(EquipmentSlot.BOOTS), context)
                    && this.matches(this.body, livingEntity.getEquipment(EquipmentSlot.BODY), context)
                    && this.matches(this.mainhand, livingEntity.getEquipment(EquipmentSlot.MAIN_HAND), context)
                    && this.matches(this.offhand, livingEntity.getEquipment(EquipmentSlot.OFF_HAND), context);
        }

        private boolean matches(@Nullable ItemPredicate predicate, @NotNull ItemStack item, @NotNull LootContext context) {
            return predicate == null || predicate.test(item, context);
        }
    }

    record PeriodicTickPredicate(int periodicTick) implements EntityPredicate {
        public static final @NotNull Codec<PeriodicTickPredicate> CODEC = Codec.INT.transform(PeriodicTickPredicate::new, PeriodicTickPredicate::periodicTick);

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity != null && this.periodicTick > 0 && entity.getAliveTicks() % this.periodicTick == 0L;
        }
    }

    record TeamPredicate(@NotNull String team) implements EntityPredicate {
        public static final @NotNull Codec<TeamPredicate> CODEC = Codec.STRING.transform(TeamPredicate::new, TeamPredicate::team);

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity instanceof LivingEntity livingEntity
                    && livingEntity.getTeam() != null
                    && this.team.equals(livingEntity.getTeam().getTeamName());
        }
    }

    record EntitySlotsPredicate(@NotNull Map<SlotRange, ItemPredicate> slots) implements EntityPredicate {
        public static final @NotNull Codec<EntitySlotsPredicate> CODEC = SlotRange.CODEC.mapValue(ItemPredicate.CODEC)
                .transform(EntitySlotsPredicate::new, EntitySlotsPredicate::slots);

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return this.test(instance, position, entity, VanillaInterface.defaults());
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            if (!(entity instanceof LivingEntity livingEntity)) {

                return false;
            }

            var context = LootContext.from(vanilla, Map.of());
            for (var entry : this.slots.entrySet()) {
                var matches = entry.getKey().slots().stream()
                        .anyMatch(slot -> entry.getValue().test(livingEntity.getEquipment(slot), context));
                if (!matches) {

                    return false;
                }
            }

            return true;
        }
    }

    record SlotRange(@NotNull List<EquipmentSlot> slots) {
        public static final @NotNull Codec<SlotRange> CODEC = Codec.STRING.transform(SlotRange::fromName, SlotRange::name);

        private static @NotNull SlotRange fromName(@NotNull String name) {
            return switch (name) {
                case "hand" -> new SlotRange(List.of(EquipmentSlot.MAIN_HAND, EquipmentSlot.OFF_HAND));
                case "armor" -> new SlotRange(List.of(EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS));
                case "feet" -> new SlotRange(List.of(EquipmentSlot.BOOTS));
                case "legs" -> new SlotRange(List.of(EquipmentSlot.LEGGINGS));
                case "chest" -> new SlotRange(List.of(EquipmentSlot.CHESTPLATE));
                case "head" -> new SlotRange(List.of(EquipmentSlot.HELMET));
                case "body" -> new SlotRange(List.of(EquipmentSlot.BODY));
                case "saddle" -> new SlotRange(List.of(EquipmentSlot.SADDLE));
                case "mainhand" -> new SlotRange(List.of(EquipmentSlot.MAIN_HAND));
                case "offhand" -> new SlotRange(List.of(EquipmentSlot.OFF_HAND));
                default -> throw new IllegalArgumentException("Unknown entity slot range: " + name);
            };
        }

        private @NotNull String name() {
            if (this.slots.size() == 1) {

                return this.slots.getFirst().nbtName();
            }

            return this.slots.equals(List.of(EquipmentSlot.MAIN_HAND, EquipmentSlot.OFF_HAND)) ? "hand" : "armor";
        }
    }

    record EntityComponentsPredicate(@NotNull DataComponentPredicates predicate) implements EntityPredicate {
        public static final @NotNull Codec<EntityComponentsPredicate> CODEC = DataComponentPredicates.CODEC
                .transform(EntityComponentsPredicate::new, EntityComponentsPredicate::predicate);

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity != null && this.predicate.test(entity);
        }
    }

    record SheepPredicate(@Nullable Boolean sheared) implements EntityPredicate {
        public static final @NotNull StructCodec<SheepPredicate> CODEC = StructCodec.struct(
                "sheared", Codec.BOOLEAN.optional(), SheepPredicate::sheared,
                SheepPredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            var meta = entity == null ? null : entity.getEntityMeta();
            return meta instanceof SheepMeta sheepMeta
                    && (this.sheared == null || sheepMeta.isSheared() == this.sheared);
        }
    }

    record CubeMobPredicate(@NotNull LootNumberRange size) implements EntityPredicate {
        public static final @NotNull StructCodec<CubeMobPredicate> CODEC = StructCodec.struct(
                "size", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), CubeMobPredicate::size,
                CubeMobPredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            var meta = entity == null ? null : entity.getEntityMeta();
            if (!(meta instanceof AbstractCubeMeta cubeMeta)) {

                return false;
            }

            var context = LootContext.from(VanillaInterface.defaults(), Map.of());
            return this.size.check(context, cubeMeta.getSize());
        }
    }

    record FishingHookPredicate(@Nullable Boolean inOpenWater) implements EntityPredicate {
        public static final @NotNull StructCodec<FishingHookPredicate> CODEC = StructCodec.struct(
                "in_open_water", Codec.BOOLEAN.optional(), FishingHookPredicate::inOpenWater,
                FishingHookPredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity != null && entity.getEntityType() == EntityType.FISHING_BOBBER && this.inOpenWater == null;
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            if (entity == null || entity.getEntityType() != EntityType.FISHING_BOBBER) {

                return false;
            }

            var openWater = vanilla.entityBoolean(Key.key("minecraft:in_open_water"), entity);
            return this.inOpenWater == null || openWater != null && openWater == this.inOpenWater;
        }
    }

    record RaiderPredicate(boolean hasRaid, boolean isCaptain) implements EntityPredicate {
        public static final @NotNull StructCodec<RaiderPredicate> CODEC = StructCodec.struct(
                "has_raid", Codec.BOOLEAN.optional(false), RaiderPredicate::hasRaid,
                "is_captain", Codec.BOOLEAN.optional(false), RaiderPredicate::isCaptain,
                RaiderPredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity != null && entity.getEntityMeta() instanceof RaiderMeta && !this.hasRaid && !this.isCaptain;
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            if (entity == null || !(entity.getEntityMeta() instanceof RaiderMeta)) {

                return false;
            }

            var hasRaid = vanilla.entityBoolean(Key.key("minecraft:has_raid"), entity);
            var isCaptain = vanilla.entityBoolean(Key.key("minecraft:is_captain"), entity);
            return hasRaid != null && isCaptain != null && hasRaid == this.hasRaid && isCaptain == this.isCaptain;
        }
    }

    record FishingHookInput(@Nullable Boolean forward,
                            @Nullable Boolean backward,
                            @Nullable Boolean left,
                            @Nullable Boolean right,
                            @Nullable Boolean jump,
                            @Nullable Boolean sneak,
                            @Nullable Boolean sprint) {
        public static final @NotNull StructCodec<FishingHookInput> CODEC = StructCodec.struct(
                "forward", Codec.BOOLEAN.optional(), FishingHookInput::forward,
                "backward", Codec.BOOLEAN.optional(), FishingHookInput::backward,
                "left", Codec.BOOLEAN.optional(), FishingHookInput::left,
                "right", Codec.BOOLEAN.optional(), FishingHookInput::right,
                "jump", Codec.BOOLEAN.optional(), FishingHookInput::jump,
                "sneak", Codec.BOOLEAN.optional(), FishingHookInput::sneak,
                "sprint", Codec.BOOLEAN.optional(), FishingHookInput::sprint,
                FishingHookInput::new
        );

        public boolean test(@NotNull Player player) {
            var input = player.inputs();
            return this.forward == null || this.forward == input.forward()
                    && (this.backward == null || this.backward == input.backward())
                    && (this.left == null || this.left == input.left())
                    && (this.right == null || this.right == input.right())
                    && (this.jump == null || this.jump == input.jump())
                    && (this.sneak == null || this.sneak == input.shift())
                    && (this.sprint == null || this.sprint == input.sprint());
        }
    }

    record FoodPredicate(@NotNull LootNumberRange level, @NotNull LootNumberRange saturation) {
        public static final @NotNull StructCodec<FoodPredicate> CODEC = StructCodec.struct(
                "level", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), FoodPredicate::level,
                "saturation", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), FoodPredicate::saturation,
                FoodPredicate::new
        );

        public boolean test(@NotNull Player player) {
            var context = LootContext.from(VanillaInterface.defaults(), Map.of());
            return this.level.check(context, player.getFood()) && this.saturation.check(context, player.getFoodSaturation());
        }
    }

    record PlayerPredicate(@NotNull LootNumberRange level,
                           @NotNull FoodPredicate food,
                           @NotNull List<String> gamemode,
                           @Nullable EntityPredicate lookingAt,
                           @Nullable FishingHookInput input,
                           @NotNull List<Codec.RawValue> stats,
                           @NotNull Map<String, Boolean> recipes,
                           @NotNull Map<String, Codec.RawValue> advancements) implements EntityPredicate {
        private static final @NotNull List<String> ALL_GAMEMODES = List.of("survival", "creative", "adventure", "spectator");

        private static @NotNull Codec<PlayerPredicate> codec(@NotNull Codec<EntityPredicate> recursiveCodec) {
            return StructCodec.struct(
                    "level", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), PlayerPredicate::level,
                    "food", FoodPredicate.CODEC.optional(new FoodPredicate(new LootNumberRange(null, null), new LootNumberRange(null, null))), PlayerPredicate::food,
                    "gamemode", Codec.STRING.list().orElse(Codec.STRING, List::of).optional(ALL_GAMEMODES), PlayerPredicate::gamemode,
                    "looking_at", recursiveCodec.optional(), PlayerPredicate::lookingAt,
                    "input", FishingHookInput.CODEC.optional(), PlayerPredicate::input,
                    "stats", Codec.RAW_VALUE.list().optional(List.of()), PlayerPredicate::stats,
                    "recipes", Codec.STRING.mapValue(Codec.BOOLEAN).optional(Map.of()), PlayerPredicate::recipes,
                    "advancements", Codec.STRING.mapValue(Codec.RAW_VALUE).optional(Map.of()), PlayerPredicate::advancements,
                    PlayerPredicate::new
            );
        }

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return this.test(instance, position, entity, VanillaInterface.defaults());
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            if (!(entity instanceof Player player)) {

                return false;
            }

            var gameMode = player.getGameMode().name().toLowerCase(Locale.ROOT);
            var context = LootContext.from(vanilla, Map.of());
            if (!this.level.check(context, player.getLevel())
                    || !this.food.test(player)
                    || !this.gamemode.contains(gameMode)
                    || this.input != null && !this.input.test(player)
                    || !this.stats.isEmpty()
                    || !this.recipes.isEmpty()
                    || !this.advancements.isEmpty()) {

                return false;
            }

            if (this.lookingAt != null) {
                var lookedAt = vanilla.entityLookedAt(player);
                if (lookedAt == null || !this.lookingAt.test(instance, position, lookedAt, vanilla)) {

                    return false;
                }
            }

            return true;
        }
    }

    record LightningPredicate(@NotNull LootNumberRange blocksSetOnFire,
                              @Nullable EntityPredicate entityStruck) implements EntityPredicate {
        private static @NotNull Codec<LightningPredicate> codec(@NotNull Codec<EntityPredicate> recursiveCodec) {
            return StructCodec.struct(
                    "blocks_set_on_fire", LootNumberRange.CODEC.optional(new LootNumberRange(null, null)), LightningPredicate::blocksSetOnFire,
                    "entity_struck", recursiveCodec.optional(), LightningPredicate::entityStruck,
                    LightningPredicate::new
            );
        }

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return this.test(instance, position, entity, VanillaInterface.defaults());
        }

        @Override
        public boolean test(@NotNull Instance instance,
                            @Nullable Point position,
                            @Nullable Entity entity,
                            @NotNull VanillaInterface vanilla) {
            if (entity == null || entity.getEntityType() != EntityType.LIGHTNING_BOLT) {

                return false;
            }

            var blocks = vanilla.entityInteger(Key.key("minecraft:blocks_set_on_fire"), entity);
            return blocks != null
                    && this.blocksSetOnFire.check(LootContext.from(vanilla, Map.of()), blocks)
                    && (this.entityStruck == null || false);
        }
    }

    record EntityTagPredicate(@Nullable List<String> anyOf,
                              @Nullable List<String> allOf,
                              @Nullable List<String> noneOf) implements EntityPredicate {
        public static final @NotNull StructCodec<EntityTagPredicate> CODEC = StructCodec.struct(
                "any_of", Codec.STRING.list().optional(), EntityTagPredicate::anyOf,
                "all_of", Codec.STRING.list().optional(), EntityTagPredicate::allOf,
                "none_of", Codec.STRING.list().optional(), EntityTagPredicate::noneOf,
                EntityTagPredicate::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            if (entity == null) {

                return false;
            }

            var tags = entity.getTag(ENTITY_TAGS);
            if (this.anyOf != null && this.anyOf.stream().noneMatch(tags::contains)) {

                return false;
            }

            if (this.allOf != null && !tags.containsAll(this.allOf)) {

                return false;
            }
            return this.noneOf == null || this.noneOf.stream().noneMatch(tags::contains);
        }
    }

    record EntityLocation(@NotNull LocationPredicate predicate) implements EntityPredicate {
        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity != null && this.predicate.test(instance, entity.getPosition());
        }
    }

    record EntityVehicle(@NotNull EntityPredicate predicate) implements EntityPredicate {
        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            var vehicle = entity == null ? null : entity.getVehicle();
            return vehicle != null && this.predicate.test(instance, vehicle.getPosition(), vehicle);
        }
    }

    record EntityPassenger(@NotNull EntityPredicate predicate) implements EntityPredicate {
        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            return entity != null && entity.getPassengers().stream()
                    .anyMatch(passenger -> this.predicate.test(instance, passenger.getPosition(), passenger));
        }
    }

    record EntityTargeted(@NotNull EntityPredicate predicate) implements EntityPredicate {
        @Override
        public boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity) {
            var targetedEntity = entity instanceof EntityCreature creature ? creature.getTarget() : null;
            return targetedEntity != null && this.predicate.test(instance, targetedEntity.getPosition(), targetedEntity);
        }
    }
}
