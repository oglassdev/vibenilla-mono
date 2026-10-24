package dev.oglass.vibenilla.server;

import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.utils.FluidUtil;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.tag.Tag;

/** Survival block breaking: break-speed validation, the harvest requirement and tool wear. */
final class BlockBreaking {
    /** Vanilla accepts a finished dig once this fraction of the break progress has elapsed. */
    private static final float ACCEPTED_PROGRESS = 0.7f;

    private static final long TICK_NANOS = 50_000_000L;
    private static final Tag<Dig> DIG = Tag.Transient("vibenilla:dig");

    private record Dig(int x, int y, int z, long startNanos) {}

    private final ItemDamageFeature itemDamage;
    private final BlockDrops drops;

    BlockBreaking(ItemDamageFeature itemDamage, BlockDrops drops) {
        this.itemDamage = itemDamage;
        this.drops = drops;
    }

    EventNode<Event> events() {
        return EventNode.all("block-breaking")
                .addListener(PlayerStartDiggingEvent.class, this::onStart)
                .addListener(PlayerBlockBreakEvent.class, this::onBreak);
    }

    private void onStart(PlayerStartDiggingEvent event) {
        var player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        var position = event.getBlockPosition();
        if (progressPerTick(player, event.getBlock()) >= 1) {
            // Enchantments are not part of Minestom's own instant-break check.
            event.getInstance().breakBlock(player, position, event.getBlockFace());
            return;
        }
        player.setTag(
                DIG,
                new Dig(
                        position.blockX(),
                        position.blockY(),
                        position.blockZ(),
                        System.nanoTime()));
    }

    private void onBreak(PlayerBlockBreakEvent event) {
        var player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        var block = event.getBlock();
        var position = event.getBlockPosition();
        var dig = player.getTag(DIG);
        player.removeTag(DIG);
        var progress = progressPerTick(player, block);
        if (progress < 1
                && !finishedInTime(
                        dig, position.blockX(), position.blockY(), position.blockZ(), progress)) {
            event.setCancelled(true);
            player.sendPacket(new BlockChangePacket(position, block));
            return;
        }

        var tool = player.getItemInMainHand().get(DataComponents.TOOL);
        if (canHarvest(player, block)) {
            this.drops.drop(event);
        }
        if (tool != null && block.hardness() > 0) {
            this.itemDamage.damageEquipment(player, EquipmentSlot.MAIN_HAND, tool.damagePerBlock());
        }
    }

    private static boolean finishedInTime(Dig dig, int x, int y, int z, float progress) {
        if (progress <= 0 || dig == null || dig.x != x || dig.y != y || dig.z != z) {
            return false;
        }
        var elapsedTicks = (System.nanoTime() - dig.startNanos) / (double) TICK_NANOS;
        return isFinished(progress, elapsedTicks);
    }

    static boolean isFinished(float progressPerTick, double elapsedTicks) {
        return progressPerTick * (elapsedTicks + 1) >= ACCEPTED_PROGRESS;
    }

    private static boolean canHarvest(Player player, Block block) {
        if (!block.requiresTool()) {
            return true;
        }
        var tool = player.getItemInMainHand().get(DataComponents.TOOL);
        return tool != null && tool.isCorrectForDrops(block.registryKey());
    }

    /** The fraction of the block's break progress made per tick; 1 or more breaks instantly. */
    private static float progressPerTick(Player player, Block block) {
        var hardness = block.hardness();
        if (hardness < 0) {
            return 0;
        }

        var held = player.getItemInMainHand();
        var tool = held.get(DataComponents.TOOL);
        var toolSpeed = tool == null ? 1f : tool.getSpeed(block.registryKey());
        var efficiency =
                held.get(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY)
                        .level(Enchantment.EFFICIENCY);
        var aquaAffinity =
                player.getEquipment(EquipmentSlot.HELMET)
                                .get(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY)
                                .level(Enchantment.AQUA_AFFINITY)
                        > 0;
        var eye = player.getPosition().add(0, player.getEyeHeight(), 0);
        var submerged = FluidUtil.isWater(player.getInstance().getBlock(eye));

        var speed =
                destroySpeed(
                        toolSpeed,
                        (float) player.getAttributeValue(Attribute.MINING_EFFICIENCY)
                                + efficiencyBonus(efficiency),
                        hasteAmplifier(player),
                        fatigueAmplifier(player),
                        (float) player.getAttributeValue(Attribute.BLOCK_BREAK_SPEED),
                        submerged
                                ? (float) player.getAttributeValue(Attribute.SUBMERGED_MINING_SPEED)
                                        * (aquaAffinity ? 5 : 1)
                                : 1,
                        player.isOnGround());
        return progressPerTick(hardness, canHarvest(player, block), speed);
    }

    static float progressPerTick(float hardness, boolean canHarvest, float destroySpeed) {
        if (hardness == 0) {
            return 1;
        }
        return destroySpeed / hardness / (canHarvest ? 30 : 100);
    }

    /** Vanilla's {@code Player.getDestroySpeed}; negative amplifiers mean the effect is absent. */
    static float destroySpeed(
            float toolSpeed,
            float miningEfficiency,
            int hasteAmplifier,
            int fatigueAmplifier,
            float blockBreakSpeed,
            float submergedMultiplier,
            boolean onGround) {
        var speed = toolSpeed;
        if (speed > 1) {
            speed += miningEfficiency;
        }
        if (hasteAmplifier >= 0) {
            speed *= 1 + (hasteAmplifier + 1) * 0.2f;
        }
        if (fatigueAmplifier >= 0) {
            speed *=
                    switch (fatigueAmplifier) {
                        case 0 -> 0.3f;
                        case 1 -> 0.09f;
                        case 2 -> 0.0027f;
                        default -> 8.1E-4f;
                    };
        }
        speed *= blockBreakSpeed * submergedMultiplier;
        return onGround ? speed : speed / 5;
    }

    static float efficiencyBonus(int level) {
        return level > 0 ? level * level + 1 : 0;
    }

    private static int hasteAmplifier(Player player) {
        return Math.max(
                amplifier(player, PotionEffect.HASTE),
                amplifier(player, PotionEffect.CONDUIT_POWER));
    }

    private static int fatigueAmplifier(Player player) {
        return amplifier(player, PotionEffect.MINING_FATIGUE);
    }

    private static int amplifier(Player player, PotionEffect effect) {
        var active = player.getEffect(effect);
        return active == null ? -1 : active.potion().amplifier();
    }
}
