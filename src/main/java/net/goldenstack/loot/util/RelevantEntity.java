package net.goldenstack.loot.util;

import net.goldenstack.loot.LootContext;
import net.minestom.server.codec.Codec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.NotNull;

public enum RelevantEntity {
    THIS("this", LootContext.THIS_ENTITY),
    ATTACKER("attacker", LootContext.ATTACKING_ENTITY),
    DIRECT_ATTACKER("direct_attacker", LootContext.DIRECT_ATTACKING_ENTITY),
    LAST_PLAYER_DAMAGE("attacking_player", LootContext.LAST_DAMAGE_PLAYER),
    TARGET_ENTITY("target_entity", LootContext.TARGET_ENTITY),
    INTERACTING_ENTITY("interacting_entity", LootContext.INTERACTING_ENTITY);

    @SuppressWarnings("UnstableApiUsage")
    public static final @NotNull Codec<RelevantEntity> CODEC = Codec.STRING.transform(value -> switch (value) {
        case "this" -> THIS;
        case "attacker" -> ATTACKER;
        case "direct_attacker" -> DIRECT_ATTACKER;
        case "attacking_player", "killer_player" -> LAST_PLAYER_DAMAGE;
        case "target_entity" -> TARGET_ENTITY;
        case "interacting_entity" -> INTERACTING_ENTITY;
        default -> throw new IllegalArgumentException("Unknown entity target: " + value);
    }, RelevantEntity::id);

    private final @NotNull String id;
    private final @NotNull LootContext.Key<? extends Entity> key;

    RelevantEntity(@NotNull String id, @NotNull LootContext.Key<? extends Entity> key) {
        this.id = id;
        this.key = key;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull LootContext.Key<? extends Entity> key() {
        return key;
    }

}
