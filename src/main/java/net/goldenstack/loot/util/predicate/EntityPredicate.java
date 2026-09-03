package net.goldenstack.loot.util.predicate;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.AgeableMobMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
            case "flags", "minecraft:flags" -> asPredicate(EntityFlagsPredicate.CODEC);
            case "entity_tags", "minecraft:entity_tags" -> asPredicate(EntityTagPredicate.CODEC);
            case "location", "minecraft:location" -> asPredicate(LocationPredicate.CODEC.transform(EntityLocation::new, EntityLocation::predicate));
            case "vehicle", "minecraft:vehicle" -> asPredicate(recursiveCodec.transform(EntityVehicle::new, EntityVehicle::predicate));
            case "passenger", "minecraft:passenger" -> asPredicate(recursiveCodec.transform(EntityPassenger::new, EntityPassenger::predicate));
            case "targeted_entity", "minecraft:targeted_entity" -> asPredicate(recursiveCodec.transform(EntityTargeted::new, EntityTargeted::predicate));
            default -> throw new IllegalArgumentException("Unknown entity predicate: " + key);
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends EntityPredicate> @NotNull Codec<EntityPredicate> asPredicate(@NotNull Codec<T> codec) {
        return codec.transform(value -> value, value -> (T) value);
    }

    boolean test(@NotNull Instance instance, @Nullable Point position, @Nullable Entity entity);

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
