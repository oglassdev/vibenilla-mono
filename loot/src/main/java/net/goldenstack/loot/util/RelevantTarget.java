package net.goldenstack.loot.util;

import net.goldenstack.loot.LootContext;
import net.minestom.server.codec.Codec;
import org.jetbrains.annotations.NotNull;

public enum RelevantTarget {
    THIS("this", LootContext.THIS_ENTITY),
    ATTACKER("attacker", LootContext.ATTACKING_ENTITY),
    DIRECT_ATTACKER("direct_attacker", LootContext.DIRECT_ATTACKING_ENTITY),
    ATTACKING_PLAYER("attacking_player", LootContext.LAST_DAMAGE_PLAYER),
    BLOCK_ENTITY("block_entity", LootContext.BLOCK_STATE),
    TOOL("tool", LootContext.TOOL),
    TARGET_ENTITY("target_entity", LootContext.TARGET_ENTITY),
    INTERACTING_ENTITY("interacting_entity", LootContext.INTERACTING_ENTITY);

    @SuppressWarnings("UnstableApiUsage")
    public static final Codec<RelevantTarget> CODEC =
            Codec.STRING.transform(
                    value ->
                            switch (value) {
                                case "this" -> THIS;
                                case "attacker" -> ATTACKER;
                                case "direct_attacker" -> DIRECT_ATTACKER;
                                case "attacking_player" -> ATTACKING_PLAYER;
                                case "block_entity" -> BLOCK_ENTITY;
                                case "tool" -> TOOL;
                                case "target_entity" -> TARGET_ENTITY;
                                case "interacting_entity" -> INTERACTING_ENTITY;
                                default ->
                                        throw new IllegalArgumentException(
                                                "Unknown context target: " + value);
                            },
                    RelevantTarget::id);

    @SuppressWarnings("UnstableApiUsage")
    public static final Codec<RelevantTarget> ENTITY_OR_BLOCK_CODEC =
            Codec.STRING.transform(
                    value ->
                            switch (value) {
                                case "this" -> THIS;
                                case "attacker" -> ATTACKER;
                                case "direct_attacker" -> DIRECT_ATTACKER;
                                case "attacking_player" -> ATTACKING_PLAYER;
                                case "block_entity" -> BLOCK_ENTITY;
                                case "target_entity" -> TARGET_ENTITY;
                                case "interacting_entity" -> INTERACTING_ENTITY;
                                default ->
                                        throw new IllegalArgumentException(
                                                "Unknown entity or block target: " + value);
                            },
                    RelevantTarget::id);

    private final @NotNull String id;
    private final @NotNull LootContext.Key<?> key;

    RelevantTarget(@NotNull String id, @NotNull LootContext.Key<?> key) {
        this.id = id;
        this.key = key;
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull LootContext.Key<?> key() {
        return key;
    }
}
