package dev.oglass.vibenilla.server;

import io.github.togar2.pvp.utils.RegistryTags;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLeaveBedEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.timer.TaskSchedule;

/**
 * Sleeping in beds. A player can lie down when it is dark outside; once every player in the
 * instance has slept for a full {@value #SLEEP_TICKS} ticks the night is skipped and the storms
 * end.
 */
final class Sleeping {
    private static final int SLEEP_TICKS = 100;
    private static final int DAY_TICKS = 24000;
    private static final int BRIGHT_SKY_DARKNESS = 4;
    private static final double REACH_HORIZONTAL = 3.0;
    private static final double REACH_VERTICAL = 2.0;
    private static final double BED_SURFACE = 0.6875;
    private static final double HEAD_OFFSET = 0.4;
    private static final double MONSTER_RANGE_HORIZONTAL = 8.0;
    private static final double MONSTER_RANGE_VERTICAL = 5.0;
    private static final Set<EntityType> MONSTERS =
            Set.of(
                    EntityType.BLAZE,
                    EntityType.BOGGED,
                    EntityType.BREEZE,
                    EntityType.CAVE_SPIDER,
                    EntityType.CREAKING,
                    EntityType.CREEPER,
                    EntityType.DROWNED,
                    EntityType.ELDER_GUARDIAN,
                    EntityType.ENDERMAN,
                    EntityType.ENDERMITE,
                    EntityType.EVOKER,
                    EntityType.GUARDIAN,
                    EntityType.HUSK,
                    EntityType.ILLUSIONER,
                    EntityType.PARCHED,
                    EntityType.PIGLIN,
                    EntityType.PIGLIN_BRUTE,
                    EntityType.PILLAGER,
                    EntityType.RAVAGER,
                    EntityType.SILVERFISH,
                    EntityType.SKELETON,
                    EntityType.SPIDER,
                    EntityType.STRAY,
                    EntityType.VEX,
                    EntityType.VINDICATOR,
                    EntityType.WARDEN,
                    EntityType.WITCH,
                    EntityType.WITHER,
                    EntityType.WITHER_SKELETON,
                    EntityType.ZOGLIN,
                    EntityType.ZOMBIE,
                    EntityType.ZOMBIE_VILLAGER);

    private final Instance instance;
    private final Weathers weathers;
    private final Map<Player, Integer> asleepFor = new ConcurrentHashMap<>();

    private Sleeping(Instance instance, Weathers weathers) {
        this.instance = instance;
        this.weathers = weathers;
    }

    static EventNode<Event> events(Instance instance, Weathers weathers) {
        var sleeping = new Sleeping(instance, weathers);
        instance.scheduler().buildTask(sleeping::tick).repeat(TaskSchedule.tick(1)).schedule();
        return EventNode.all("sleeping")
                .addListener(PlayerBlockInteractEvent.class, sleeping::onInteract)
                .addListener(PlayerLeaveBedEvent.class, sleeping::onLeaveBed)
                .addListener(EntityDamageEvent.class, sleeping::onDamage)
                .addListener(RemoveEntityFromInstanceEvent.class, sleeping::onRemove)
                .addListener(
                        PlayerDisconnectEvent.class,
                        event -> sleeping.asleepFor.remove(event.getPlayer()));
    }

    private void onInteract(PlayerBlockInteractEvent event) {
        var player = event.getPlayer();
        var block = event.getBlock();
        if (event.getHand() != PlayerHand.MAIN
                || event.getInstance() != instance
                || !block.key().value().endsWith("_bed")
                || player.getGameMode() == GameMode.SPECTATOR
                || skipsUse(player)) {
            return;
        }

        var head = event.getBlockPosition();
        if (!"head".equals(block.getProperty("part"))) {
            head = head.relative(BlockFace.valueOf(block.getProperty("facing").toUpperCase()));
        }
        trySleep(event, head);
    }

    private void onLeaveBed(PlayerLeaveBedEvent event) {
        if (event.getPlayer().getInstance() == instance) {
            standUp(event.getPlayer());
        }
    }

    private void onRemove(RemoveEntityFromInstanceEvent event) {
        if (event.getInstance() == instance && event.getEntity() instanceof Player player) {
            asleepFor.remove(player);
            if (bedOf(player) != null) {
                player.leaveBed();
            }
        }
    }

    /**
     * Mirrors vanilla's checks in order. Problems vanilla reports before it touches the spawn point
     * cancel the interaction so the bed handler leaves the spawn alone.
     */
    private void trySleep(PlayerBlockInteractEvent event, Point head) {
        var player = event.getPlayer();
        if (instance.getPlayers().stream()
                .map(Sleeping::bedOf)
                .anyMatch(bed -> bed != null && head.sameBlock(bed))) {
            reject(event, "block.minecraft.bed.occupied");
            return;
        }
        if (bedOf(player) != null || player.isDead()) {
            reject(event, null);
            return;
        }

        var direction =
                BlockFace.valueOf(instance.getBlock(head).getProperty("facing").toUpperCase());
        var foot = head.relative(direction.getOppositeFace());
        if (!inReach(player.getPosition(), head) && !inReach(player.getPosition(), foot)) {
            reject(event, "block.minecraft.bed.too_far_away");
            return;
        }
        if (suffocates(instance.getBlock(head.add(0, 1, 0)))
                || suffocates(instance.getBlock(foot.add(0, 1, 0)))) {
            reject(event, "block.minecraft.bed.obstructed");
            return;
        }
        if (isBrightOutside(instance)) {
            player.sendActionBar(Component.translatable("block.minecraft.bed.no_sleep"));
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE && monsterNear(head)) {
            player.sendActionBar(Component.translatable("block.minecraft.bed.not_safe"));
            return;
        }

        player.enterBed(head);
        asleepFor.put(player, 0);
        player.teleport(
                new Pos(
                        head.blockX() + 0.5 + direction.toDirection().normalX() * HEAD_OFFSET,
                        head.blockY() + BED_SURFACE,
                        head.blockZ() + 0.5 + direction.toDirection().normalZ() * HEAD_OFFSET,
                        player.getPosition().yaw(),
                        player.getPosition().pitch()));
    }

    private static void reject(PlayerBlockInteractEvent event, String message) {
        event.setBlockingItemUse(true);
        event.setCancelled(true);
        if (message != null) {
            event.getPlayer().sendActionBar(Component.translatable(message));
        }
    }

    private boolean monsterNear(Point head) {
        var x = head.blockX() + 0.5;
        var z = head.blockZ() + 0.5;
        var y = head.blockY();
        return instance.getEntities().stream()
                .filter(entity -> MONSTERS.contains(entity.getEntityType()))
                .anyMatch(
                        entity -> {
                            var box = entity.getBoundingBox();
                            var position = entity.getPosition();
                            return Math.abs(position.x() - x)
                                            < MONSTER_RANGE_HORIZONTAL + box.width() / 2
                                    && Math.abs(position.z() - z)
                                            < MONSTER_RANGE_HORIZONTAL + box.depth() / 2
                                    && position.y() < y + MONSTER_RANGE_VERTICAL
                                    && position.y() + box.height() > y - MONSTER_RANGE_VERTICAL;
                        });
    }

    /** Whether the block is a full cube that blocks motion, as vanilla's {@code isSuffocating}. */
    private static boolean suffocates(Block block) {
        if (block.air()
                || !RegistryTags.contains(RegistryTags.BLOCKS_MOTION, block.registryKey())) {
            return false;
        }
        var shape = block.collisionShape();
        for (var face : BlockFace.values()) {
            if (!shape.isFaceFull(face)) {
                return false;
            }
        }
        return !block.key().value().endsWith("glass")
                && !RegistryTags.contains(RegistryTags.LEAVES, block);
    }

    private void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && player.getInstance() == instance
                && !event.isCancelled()
                && bedOf(player) != null) {
            wake(player);
        }
    }

    private void tick() {
        var active = 0;
        var rested = 0;
        for (var player : instance.getPlayers()) {
            var bed = bedOf(player);
            if (bed != null && (isBrightOutside(instance) || !isBed(bed))) {
                wake(player);
                bed = null;
            }
            if (bed == null) {
                asleepFor.remove(player);
            } else {
                asleepFor.merge(player, 1, (ticks, one) -> Math.min(ticks + one, SLEEP_TICKS));
            }
            if (player.getGameMode() != GameMode.SPECTATOR) {
                active++;
                if (asleepFor.getOrDefault(player, 0) >= SLEEP_TICKS) {
                    rested++;
                }
            }
        }

        if (active > 0 && rested == active) {
            skipNight();
        }
    }

    private void skipNight() {
        var morning = instance.getTime() + DAY_TICKS;
        instance.setTime(morning - morning % DAY_TICKS);
        instance.getPlayers().stream().filter(player -> bedOf(player) != null).forEach(this::wake);
        weathers.clearStorms();
    }

    private void wake(Player player) {
        standUp(player);
        player.leaveBed();
        asleepFor.remove(player);
    }

    private void standUp(Player player) {
        var bed = bedOf(player);
        if (bed == null) {
            return;
        }
        var block = instance.getBlock(bed);
        var spawn = isBed(bed) ? Respawns.findBedSpawn(instance, bed, block) : null;
        player.teleport(
                spawn == null
                        ? player.getPosition().withY(bed.y() + 1)
                        : spawn.withView(player.getPosition()));
    }

    private boolean isBed(Point position) {
        return instance.getBlock(position).key().value().endsWith("_bed");
    }

    private static Point bedOf(Player player) {
        return player.getLivingEntityMeta().getBedInWhichSleepingPosition();
    }

    private static boolean skipsUse(Player player) {
        return player.isSneaking()
                && (!player.getItemInMainHand().isAir() || !player.getItemInOffHand().isAir());
    }

    private static boolean inReach(Point player, Point bed) {
        return Math.abs(player.x() - (bed.blockX() + 0.5)) <= REACH_HORIZONTAL
                && Math.abs(player.y() - bed.blockY()) <= REACH_VERTICAL
                && Math.abs(player.z() - (bed.blockZ() + 0.5)) <= REACH_HORIZONTAL;
    }

    /**
     * Whether the sky is bright enough that beds refuse sleepers: vanilla's sky darkness below 4.
     */
    static boolean isBrightOutside(Instance instance) {
        var day = instance.getTime() / 24000.0 - 0.25;
        var fraction = day - Math.floor(day);
        var smoothed = 0.5 - Math.cos(fraction * Math.PI) / 2.0;
        var timeOfDay = (float) (fraction * 2.0 + smoothed) / 3.0F;
        var brightness = 1.0F - (float) (Math.cos(timeOfDay * (float) (Math.PI * 2)) * 2.0F + 0.5F);
        brightness = 1.0F - Math.clamp(brightness, 0.0F, 1.0F);
        var weather = instance.getWeather();
        brightness *= 1.0F - weather.rainLevel() * 5.0F / 16.0F;
        brightness *= 1.0F - weather.thunderLevel() * 5.0F / 16.0F;
        return (int) ((1.0F - brightness) * 11.0F) < BRIGHT_SKY_DARKNESS;
    }
}
