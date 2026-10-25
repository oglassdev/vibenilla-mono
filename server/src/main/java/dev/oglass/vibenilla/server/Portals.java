package dev.oglass.vibenilla.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceBlockUpdateEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.tag.Tag;
import org.jspecify.annotations.Nullable;
import rocks.minestom.placement.PortalShape;
import rocks.minestom.placement.PortalShape.Axis;

/**
 * Nether portals and end portals: lighting a frame, and sending players who stand in a portal to
 * the other dimension.
 */
final class Portals {
    private static final int SURVIVAL_WAIT_TICKS = 80;
    private static final int CREATIVE_WAIT_TICKS = 1;
    private static final int COOLDOWN_TICKS = 10;
    private static final int DECAY_PER_TICK = 4;

    private static final double PLAYER_HALF_WIDTH = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double SHRINK = 1.0E-5;
    private static final double PORTAL_MIN = 6 / 16.0;
    private static final double PORTAL_MAX = 10 / 16.0;
    private static final double END_PORTAL_HEIGHT = 12 / 16.0;

    private static final Tag<Travel> TRAVEL = Tag.Transient("vibenilla:portal");

    private static final class Travel {
        int ticksInside;
        int cooldown;
        boolean traveling;
    }

    private record Hit(Block block, BlockVec position) {}

    private final WorldStorage world;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    Portals(WorldStorage world) {
        this.world = world;
    }

    EventNode<Event> events() {
        return EventNode.all("portals")
                .addListener(InstanceBlockUpdateEvent.class, this::onBlockUpdate)
                .addListener(PlayerTickEvent.class, this::onTick);
    }

    /** Fire placed inside an obsidian frame becomes the portal, as vanilla {@code FireBlock}. */
    private void onBlockUpdate(InstanceBlockUpdateEvent event) {
        if (!event.getBlock().compare(Block.FIRE)) {
            return;
        }
        var instance = event.getInstance();
        var dimension = instance.getDimensionType().key();
        if (!dimension.equals(WorldStorage.OVERWORLD)
                && !dimension.equals(WorldStorage.THE_NETHER)) {
            return;
        }
        var shape = PortalShape.findEmpty(instance, event.getBlockPosition());
        if (shape == null) {
            return;
        }
        var portal = Block.NETHER_PORTAL.withProperty("axis", shape.axis().property());
        for (var position : shape.interior()) {
            instance.setBlock(position, portal, false);
        }
    }

    private void onTick(PlayerTickEvent event) {
        var player = event.getPlayer();
        var instance = player.getInstance();
        if (instance == null || player.isDead()) {
            return;
        }
        var travel = player.getTag(TRAVEL);
        if (travel == null) {
            travel = new Travel();
            player.setTag(TRAVEL, travel);
        }

        var hit =
                player.getGameMode() == GameMode.SPECTATOR ? null : insidePortal(instance, player);
        if (hit == null) {
            travel.ticksInside = Math.max(0, travel.ticksInside - DECAY_PER_TICK);
            travel.cooldown = Math.max(0, travel.cooldown - 1);
            return;
        }
        if (travel.cooldown > 0) {
            travel.cooldown = COOLDOWN_TICKS;
            return;
        }
        if (travel.traveling) {
            return;
        }
        travel.ticksInside++;
        if (travel.ticksInside < waitTicks(player, hit.block())) {
            return;
        }
        travel.ticksInside = 0;
        travel.traveling = true;
        travel.cooldown = COOLDOWN_TICKS;
        travel(player, instance, hit, travel);
    }

    private static int waitTicks(Player player, Block portal) {
        if (portal.compare(Block.END_PORTAL)) {
            return 0;
        }
        return player.getGameMode() == GameMode.CREATIVE
                ? CREATIVE_WAIT_TICKS
                : SURVIVAL_WAIT_TICKS;
    }

    private void travel(Player player, Instance from, Hit hit, Travel travel) {
        var origin = from.getDimensionType().key();
        var position = player.getPosition();
        if (hit.block().compare(Block.END_PORTAL) && origin.equals(WorldStorage.THE_END)) {
            player.scheduleNextTick(
                    entity -> arrive(player, travel, world.overworld(), Respawns.resolve(player)));
            return;
        }

        var target = destination(origin, hit.block());
        if (target == null) {
            travel.traveling = false;
            return;
        }
        var to = world.instance(target);
        var axis = Axis.of(hit.block());
        executor.execute(
                () -> {
                    try {
                        var exit =
                                target.equals(WorldStorage.THE_END)
                                        ? PortalForcer.buildEndPlatform(to)
                                        : PortalForcer.netherExit(from, to, position, axis);
                        player.scheduleNextTick(entity -> arrive(player, travel, to, exit));
                    } catch (RuntimeException e) {
                        MinecraftServer.LOGGER.error(
                                "Failed to send {} through a portal", player.getUsername(), e);
                        travel.traveling = false;
                    }
                });
    }

    private static @Nullable Key destination(Key origin, Block portal) {
        if (portal.compare(Block.END_PORTAL)) {
            return WorldStorage.THE_END;
        }
        if (origin.equals(WorldStorage.OVERWORLD)) {
            return WorldStorage.THE_NETHER;
        }
        return origin.equals(WorldStorage.THE_NETHER) ? WorldStorage.OVERWORLD : null;
    }

    private static void arrive(Player player, Travel travel, Instance to, Pos position) {
        if (!player.isOnline()) {
            return;
        }
        player.setInstance(to, position)
                .whenComplete(
                        (ignored, error) -> {
                            if (error != null) {
                                MinecraftServer.LOGGER.error(
                                        "Failed to move {} to {}", player.getUsername(), to, error);
                            }
                            player.setVelocity(Vec.ZERO);
                            travel.cooldown = COOLDOWN_TICKS;
                            travel.traveling = false;
                        });
    }

    /** The portal block whose collision box the player overlaps, if any. */
    private static @Nullable Hit insidePortal(Instance instance, Player player) {
        var position = player.getPosition();
        var minX = position.x() - PLAYER_HALF_WIDTH + SHRINK;
        var maxX = position.x() + PLAYER_HALF_WIDTH - SHRINK;
        var minY = position.y() + SHRINK;
        var maxY = position.y() + PLAYER_HEIGHT - SHRINK;
        var minZ = position.z() - PLAYER_HALF_WIDTH + SHRINK;
        var maxZ = position.z() + PLAYER_HALF_WIDTH - SHRINK;
        for (var x = (int) Math.floor(minX); x <= (int) Math.floor(maxX); x++) {
            for (var z = (int) Math.floor(minZ); z <= (int) Math.floor(maxZ); z++) {
                if (!instance.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (var y = (int) Math.floor(minY); y <= (int) Math.floor(maxY); y++) {
                    var block = instance.getBlock(x, y, z);
                    var inside =
                            block.compare(Block.NETHER_PORTAL)
                                    ? overlapsPortal(block, x, z, minX, maxX, minZ, maxZ)
                                    : block.compare(Block.END_PORTAL)
                                            && minY < y + END_PORTAL_HEIGHT;
                    if (inside) {
                        return new Hit(block, new BlockVec(x, y, z));
                    }
                }
            }
        }
        return null;
    }

    private static boolean overlapsPortal(
            Block block, int x, int z, double minX, double maxX, double minZ, double maxZ) {
        if (Axis.of(block) == Axis.X) {
            return minZ < z + PORTAL_MAX && maxZ > z + PORTAL_MIN;
        }
        return minX < x + PORTAL_MAX && maxX > x + PORTAL_MIN;
    }
}
