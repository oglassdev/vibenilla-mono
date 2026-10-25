package dev.oglass.vibenilla.server;

import dev.oglass.vibenilla.server.Respawns.Spawn;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceBlockUpdateEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
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
        int generation;

        /** Drops the request in flight, if any: its result is no longer wanted. */
        void cancel() {
            generation++;
            traveling = false;
        }
    }

    private record Hit(Block block, BlockVec position) {}

    private final WorldStorage world;

    Portals(WorldStorage world) {
        this.world = world;
    }

    EventNode<Event> events() {
        return EventNode.all("portals")
                .addListener(InstanceBlockUpdateEvent.class, this::onBlockUpdate)
                .addListener(PlayerTickEvent.class, this::onTick)
                .addListener(PlayerDeathEvent.class, event -> cancel(event.getPlayer()))
                .addListener(PlayerSpawnEvent.class, event -> cancel(event.getPlayer()));
    }

    private static void cancel(Player player) {
        var travel = player.getTag(TRAVEL);
        if (travel != null) {
            travel.cancel();
        }
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
        world.portals(dimension).add(shape.interior().getFirst());
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

    /**
     * Starts the transfer on the tick thread. Only chunk loading runs elsewhere. The block work and
     * the final move happen back on the player's tick, and each re-checks that the request is still
     * wanted. The destination's view area is loaded first, so the move itself loads nothing and
     * cannot be overtaken by a death or an instance change.
     */
    private void travel(Player player, Instance from, Hit hit, Travel travel) {
        var origin = from.getDimensionType().key();
        var position = player.getPosition();
        var token = travel.generation;
        var tick = Chunks.tick(player);
        BooleanSupplier wanted =
                () ->
                        travel.generation == token
                                && player.isOnline()
                                && !player.isDead()
                                && player.getInstance() == from;

        var endExit = hit.block().compare(Block.END_PORTAL) && origin.equals(WorldStorage.THE_END);
        var target = endExit ? WorldStorage.OVERWORLD : destination(origin, hit.block());
        if (target == null) {
            travel.traveling = false;
            return;
        }
        CompletableFuture<Spawn> exit;
        if (endExit) {
            exit =
                    Respawns.preload(player, tick)
                            .thenApplyAsync(
                                    ignored -> wanted.getAsBoolean() ? Respawns.exit(player) : null,
                                    tick);
        } else {
            var to = world.instance(target);
            var arrival =
                    target.equals(WorldStorage.THE_END)
                            ? PortalForcer.buildEndPlatform(to, wanted, tick)
                            : PortalForcer.netherExit(
                                    from,
                                    to,
                                    world.portals(target),
                                    position,
                                    Axis.of(hit.block()),
                                    wanted,
                                    tick);
            exit = arrival.thenApply(pos -> pos == null ? null : new Spawn(target, pos));
        }

        exit.whenCompleteAsync(
                (spawn, error) -> {
                    if (error != null || spawn == null || !wanted.getAsBoolean()) {
                        stop(player, travel, token, error);
                        return;
                    }
                    arrive(player, travel, token, wanted, spawn);
                },
                tick);
    }

    private static void stop(Player player, Travel travel, int token, @Nullable Throwable error) {
        if (error != null) {
            MinecraftServer.LOGGER.error(
                    "Failed to send {} through a portal", player.getUsername(), error);
        }
        if (travel.generation == token) {
            travel.traveling = false;
        }
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

    /**
     * Moves the player once the view area around the spot is loaded. The view distance can change
     * while the destination loads, so it is checked again right before the move, and loaded again
     * if it grew.
     */
    private void arrive(
            Player player, Travel travel, int token, BooleanSupplier wanted, Spawn spawn) {
        var to = world.instance(spawn.dimension());
        if (!Chunks.isViewLoaded(player, to, spawn.position())) {
            Chunks.loadView(player, to, spawn.position())
                    .whenCompleteAsync(
                            (ignored, error) -> {
                                if (error != null || !wanted.getAsBoolean()) {
                                    stop(player, travel, token, error);
                                    return;
                                }
                                arrive(player, travel, token, wanted, spawn);
                            },
                            Chunks.tick(player));
            return;
        }
        try {
            player.setInstance(to, spawn.position())
                    .whenComplete(
                            (ignored, error) -> {
                                if (error != null) {
                                    MinecraftServer.LOGGER.error(
                                            "Failed to move {} to {}",
                                            player.getUsername(),
                                            to,
                                            error);
                                }
                                player.setVelocity(Vec.ZERO);
                                travel.cooldown = COOLDOWN_TICKS;
                                travel.traveling = false;
                            });
        } catch (RuntimeException e) {
            MinecraftServer.LOGGER.error("Failed to move {} to {}", player.getUsername(), to, e);
            travel.traveling = false;
        }
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
