package net.goldenstack.loot.util.predicate;

import net.goldenstack.loot.LootContext;
import net.minestom.server.MinecraftServer;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Instance;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.registry.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public interface DamageSourcePredicate {

    @NotNull Codec<DamageSourcePredicate> CODEC = Properties.CODEC.transform(value -> value, value -> (Properties) value);

    default boolean test(@NotNull Instance instance, @NotNull Point position, @NotNull DamageType type) {
        return this.test(instance, position, type, null, null);
    }

    default boolean test(@NotNull Instance instance,
                         @NotNull Point position,
                         @NotNull DamageType type,
                         @Nullable Entity directEntity,
                         @Nullable Entity sourceEntity) {
        return this.test(instance, position, type);
    }

    record Properties(@NotNull List<TagPredicate> tags,
                      @Nullable EntityPredicate directEntity,
                      @Nullable EntityPredicate sourceEntity,
                      @Nullable Boolean isDirect) implements DamageSourcePredicate {
        public static final @NotNull StructCodec<Properties> CODEC = StructCodec.struct(
                "tags", TagPredicate.CODEC.list().optional(List.of()), Properties::tags,
                "direct_entity", EntityPredicate.CODEC.optional(), Properties::directEntity,
                "source_entity", EntityPredicate.CODEC.optional(), Properties::sourceEntity,
                "is_direct", Codec.BOOLEAN.optional(), Properties::isDirect,
                Properties::new
        );

        @Override
        public boolean test(@NotNull Instance instance, @NotNull Point position, @NotNull DamageType type,
                            @Nullable Entity directEntity, @Nullable Entity sourceEntity) {
            for (var tag : this.tags) {
                var typeKey = MinecraftServer.getDamageTypeRegistry().getKey(type);
                var registryTag = MinecraftServer.getDamageTypeRegistry().getTag(tag.id().key());
                var contains = typeKey != null && registryTag != null && registryTag.contains(typeKey);

                if (contains != tag.expected()) {

                    return false;
                }
            }

            if (this.directEntity != null
                    && (directEntity == null || !this.directEntity.test(instance, position, directEntity))) {

                return false;
            }

            if (this.sourceEntity != null
                    && (sourceEntity == null || !this.sourceEntity.test(instance, position, sourceEntity))) {

                return false;
            }

            return this.isDirect == null || this.isDirect == (directEntity == sourceEntity);
        }
    }

    record TagPredicate(@NotNull TagKey<DamageType> id, boolean expected) {
        public static final @NotNull StructCodec<TagPredicate> CODEC = StructCodec.struct(
                "id", TagKey.codec(Registries::damageType), TagPredicate::id,
                "expected", Codec.BOOLEAN, TagPredicate::expected,
                TagPredicate::new
        );
    }
}
