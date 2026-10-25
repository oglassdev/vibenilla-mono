package dev.oglass.vibenilla.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerRespawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.play.ClientStatusPacket;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.placement.Utility;

/**
 * Chooses where a respawning player appears: a valid bed in its dimension or an anchor that works
 * there, else near the overworld's world spawn.
 *
 * <p>The world spawn's surroundings are loaded at startup and never unloaded, so respawning there
 * is immediate. When a player dies, the bed or anchor and the view area around it are loaded in the
 * background. A respawn request is held on the death screen until that has finished, or until a
 * deadline passes, and then the bed or anchor is checked again; if it is gone, or its view area is
 * not loaded, the player appears at world spawn instead.
 */
final class Respawns {
    /** Where a player respawns. */
    record Spawn(Key dimension, Pos position) {}

    private static final class Pending {
        final CompletableFuture<Void> ready;
        final Pos fallback;
        boolean waiting;

        Pending(CompletableFuture<Void> ready, Pos fallback) {
            this.ready = ready;
            this.fallback = fallback;
        }
    }

    private static final Tag<Pending> PENDING = Tag.Transient("vibenilla:respawn");

    private static final long DEADLINE_SECONDS = 10;
    private static final int SPAWN_RADIUS = 10;
    private static final int SPAWN_ATTEMPTS = 16;
    private static final int SEARCH_RADIUS = 32;
    private static final int BED_REACH = 2;
    private static final double EPSILON = 1.0E-4;
    private static final BoundingBox PLAYER_BOX = new BoundingBox(0.6, 1.8, 0.6);
    private static final BoundingBox FOOTPRINT = new BoundingBox(0.6, 2 * EPSILON, 0.6);
    private static final Set<String> HAZARDS =
            Set.of(
                    "fire",
                    "soul_fire",
                    "cactus",
                    "sweet_berry_bush",
                    "powder_snow",
                    "magma_block",
                    "campfire",
                    "soul_campfire",
                    "wither_rose");

    private static WorldStorage world;
    private static Pos worldSpawn = Pos.ZERO;
    private static @Nullable Pos searchedSpawn;

    private Respawns() {}

    static EventNode<Event> events(WorldStorage world) {
        Respawns.world = world;
        Respawns.worldSpawn = world.spawn();
        return EventNode.all("respawns")
                .addListener(PlayerDeathEvent.class, Respawns::onDeath)
                .addListener(PlayerPacketEvent.class, Respawns::onPacket)
                .addListener(PlayerRespawnEvent.class, Respawns::onRespawn);
    }

    /** Makes a restored bed or anchor, or none, the player's respawn point. */
    static void restore(Player player, @Nullable Spawn spawn) {
        if (spawn == null) {
            player.setRespawnPoint(worldSpawn);
            player.removeTag(Utility.RESPAWN_DIMENSION);
            return;
        }
        player.setRespawnPoint(spawn.position());
        player.setTag(Utility.RESPAWN_DIMENSION, spawn.dimension().asString());
    }

    /**
     * Loads the world spawn's surroundings, to cover every spot a respawn can pick and the view
     * area around it. Minestom unloads chunks only on request, and nothing here asks for these.
     */
    static void loadSpawnArea() {
        var overworld = world.overworld();
        var radius = SEARCH_RADIUS + (overworld.viewDistance() + 1) * 16;
        Chunks.loadAround(overworld, worldSpawn.blockX(), worldSpawn.blockZ(), radius).join();
    }

    /**
     * Loads the bed or anchor the player would respawn at and the view area around it, spending
     * nothing. The result completes on the executor, which must be the tick thread.
     */
    static CompletableFuture<Void> preload(Player player, Executor tick) {
        var instance = respawnInstance(player);
        var point = player.getRespawnPoint();
        if (instance == null || point == null) {
            return CompletableFuture.completedFuture(null);
        }
        return Chunks.loadAround(instance, point.blockX(), point.blockZ(), BED_REACH)
                .thenComposeAsync(
                        ignored -> {
                            var spawn = personal(player);
                            return spawn == null
                                    ? CompletableFuture.<Void>completedFuture(null)
                                    : Chunks.loadView(
                                            player,
                                            world.instance(spawn.dimension()),
                                            spawn.position());
                        },
                        tick);
    }

    /**
     * Where the player lands when leaving the End: their bed or charged anchor, else near world
     * spawn, without spending a charge. The bed or anchor must have been preloaded.
     */
    static Spawn exit(Player player) {
        return choose(player, randomSpawn(world.overworld()), false);
    }

    /** Respawns a player who joined dead, once the way there is loaded or the deadline passes. */
    static void placeDead(Player player) {
        var pending = begin(player);
        pending.ready
                .orTimeout(DEADLINE_SECONDS, TimeUnit.SECONDS)
                .whenCompleteAsync(
                        (ignored, error) -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            var spawn = resolve(player, pending);
                            var to = world.instance(spawn.dimension());
                            if (player.getInstance() == to) {
                                player.teleport(spawn.position());
                            } else {
                                player.setInstance(to, spawn.position());
                            }
                        },
                        Chunks.tick(player));
    }

    private static Pending begin(Player player) {
        var ready = preload(player, Chunks.tick(player));
        ready.whenComplete(
                (ignored, error) -> {
                    if (error != null) {
                        MinecraftServer.LOGGER.warn(
                                "Respawning {} at world spawn: preload failed",
                                player.getUsername(),
                                error);
                    }
                });
        return new Pending(ready, randomSpawn(world.overworld()));
    }

    private static void onDeath(PlayerDeathEvent event) {
        var player = event.getPlayer();
        player.setTag(PENDING, begin(player));
    }

    private static void onPacket(PlayerPacketEvent event) {
        var player = event.getPlayer();
        if (!(event.getPacket() instanceof ClientStatusPacket packet)
                || packet.action() != ClientStatusPacket.Action.PERFORM_RESPAWN) {
            return;
        }
        var pending = player.getTag(PENDING);
        if (!player.isDead() || pending == null || pending.ready.isDone()) {
            return;
        }
        event.setCancelled(true);
        if (pending.waiting) {
            return;
        }
        pending.waiting = true;
        pending.ready
                .orTimeout(DEADLINE_SECONDS, TimeUnit.SECONDS)
                .whenCompleteAsync(
                        (ignored, error) -> {
                            if (player.isOnline() && player.isDead()) {
                                player.respawn();
                            }
                        },
                        Chunks.tick(player));
    }

    private static void onRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        var pending = player.getTag(PENDING);
        player.removeTag(PENDING);
        player.clearEffects();
        var spawn =
                pending == null
                        ? new Spawn(WorldStorage.OVERWORLD, randomSpawn(world.overworld()))
                        : resolve(player, pending);
        event.setRespawnPosition(spawn.position());
        var to = world.instance(spawn.dimension());
        if (player.getInstance() != to) {
            // The player still reads as dead here, so the void can't hurt them if this is late.
            player.setInstance(to, spawn.position());
        }
    }

    private static Spawn resolve(Player player, Pending pending) {
        var ready = pending.ready.isDone() && !pending.ready.isCompletedExceptionally();
        return ready ? choose(player, pending.fallback, true) : atWorldSpawn(pending.fallback);
    }

    /**
     * The player's bed or anchor if it is still valid and its view area is loaded, else the
     * fallback. With {@code use}, an anchor charge is spent and an invalid bed or anchor is
     * forgotten. The chunks around the bed or anchor must be loaded.
     */
    private static Spawn choose(Player player, Pos fallback, boolean use) {
        if (player.getTag(Utility.RESPAWN_DIMENSION) == null) {
            return atWorldSpawn(fallback);
        }
        var spawn = personal(player);
        if (spawn == null) {
            if (use) {
                player.removeTag(Utility.RESPAWN_DIMENSION);
                player.setRespawnPoint(worldSpawn);
            }
            player.sendMessage(Component.translatable("block.minecraft.spawn.not_valid"));
            return atWorldSpawn(fallback);
        }
        var instance = world.instance(spawn.dimension());
        if (!Chunks.isViewLoaded(player, instance, spawn.position())) {
            return atWorldSpawn(fallback);
        }
        if (use) {
            spendCharge(instance, player.getRespawnPoint());
        }
        return spawn;
    }

    private static Spawn atWorldSpawn(Pos position) {
        return new Spawn(WorldStorage.OVERWORLD, position);
    }

    private static @Nullable Instance respawnInstance(Player player) {
        var dimension = player.getTag(Utility.RESPAWN_DIMENSION);
        return dimension == null ? null : world.instance(Key.key(dimension));
    }

    /** The player's bed, or anchor with a charge, if still valid. Spends nothing. */
    private static @Nullable Spawn personal(Player player) {
        var instance = respawnInstance(player);
        var point = player.getRespawnPoint();
        if (instance == null || point == null) {
            return null;
        }
        if (!instance.isChunkLoaded(point)) {
            return null;
        }
        var block = instance.getBlock(point);
        Pos position = null;
        if (block.key().value().endsWith("_bed")) {
            position = findBedSpawn(instance, point, block);
        } else if (block.compare(Block.RESPAWN_ANCHOR)) {
            position = findAnchorSpawn(instance, point, block);
        }
        return position == null
                ? null
                : new Spawn(Key.key(player.getTag(Utility.RESPAWN_DIMENSION)), position);
    }

    static @Nullable Pos findBedSpawn(Instance instance, Point head, Block block) {
        var foot = head.sub(horizontal(block.getProperty("facing")));
        var spawn = findStandUp(instance, List.of(head, foot));
        return spawn == null ? null : spawn.withLookAt(centerOf(head));
    }

    private static @Nullable Pos findAnchorSpawn(Instance instance, Point anchor, Block block) {
        if (Integer.parseInt(block.getProperty("charges")) == 0
                || !Utility.environmentBoolean(
                        instance, EnvironmentAttribute.RESPAWN_ANCHOR_WORKS.key(), false)) {
            return null;
        }
        var spawn = findStandUp(instance, List.of(anchor));
        return spawn == null ? null : spawn.withLookAt(centerOf(anchor));
    }

    private static void spendCharge(Instance instance, Point point) {
        var block = instance.getBlock(point);
        if (!block.compare(Block.RESPAWN_ANCHOR)) {
            return;
        }
        var charges = Integer.parseInt(block.getProperty("charges"));
        instance.setBlock(point, block.withProperty("charges", String.valueOf(charges - 1)));
        instance.playSound(
                Sound.sound(
                        SoundEvent.BLOCK_RESPAWN_ANCHOR_DEPLETE, Sound.Source.BLOCK, 1.0F, 1.0F),
                centerOf(point));
    }

    /** Nearest standable spot beside the occupied blocks, then on top of them. */
    private static @Nullable Pos findStandUp(Instance instance, List<Point> occupied) {
        var sum = Vec.ZERO;
        for (var block : occupied) {
            sum = sum.add(centerOf(block));
        }
        var center = sum.div(occupied.size());

        Set<Vec> ring = new LinkedHashSet<>();
        for (var block : occupied) {
            for (var dx = -1; dx <= 1; dx++) {
                for (var dz = -1; dz <= 1; dz++) {
                    ring.add(new Vec(block.blockX() + dx, block.blockY(), block.blockZ() + dz));
                }
            }
        }
        for (var block : occupied) {
            ring.remove(new Vec(block.blockX(), block.blockY(), block.blockZ()));
        }

        var candidates = new ArrayList<>(ring);
        candidates.sort(Comparator.comparingDouble(cell -> centerOf(cell).distanceSquared(center)));
        for (var block : occupied) {
            candidates.add(new Vec(block.blockX(), block.blockY() + 1, block.blockZ()));
        }

        for (var cell : candidates) {
            var spawn = standAt(instance, cell.blockX(), cell.blockY(), cell.blockZ());
            if (spawn == null) {
                spawn = standAt(instance, cell.blockX(), cell.blockY() - 1, cell.blockZ());
            }
            if (spawn != null) {
                return spawn;
            }
        }
        return null;
    }

    private static @Nullable Pos standAt(Instance instance, int x, int y, int z) {
        if (!instance.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        var cellHeight = height(instance.getBlock(x, y, z));
        var feetY =
                cellHeight > EPSILON
                        ? y + cellHeight
                        : y - 1 + height(instance.getBlock(x, y - 1, z));
        var spawn = new Pos(x + 0.5, feetY, z + 0.5);
        return isSafeStandingSpot(instance, spawn) ? spawn : null;
    }

    /**
     * Whether the player's full bounding box is free of collisions and hazards, on solid ground.
     */
    static boolean isSafeStandingSpot(Instance instance, Pos pos) {
        var minX = (int) Math.floor(pos.x() - PLAYER_BOX.width() / 2);
        var maxX = (int) Math.floor(pos.x() + PLAYER_BOX.width() / 2);
        var minZ = (int) Math.floor(pos.z() - PLAYER_BOX.depth() / 2);
        var maxZ = (int) Math.floor(pos.z() + PLAYER_BOX.depth() / 2);
        var minY = (int) Math.floor(pos.y());
        var maxY = (int) Math.floor(pos.y() + PLAYER_BOX.height());
        var dimension = instance.getCachedDimensionType();
        if (minY - 1 < dimension.minY() || maxY >= dimension.maxY()) {
            return false;
        }

        var test = pos.add(0, EPSILON, 0);
        for (var x = minX; x <= maxX; x++) {
            for (var z = minZ; z <= maxZ; z++) {
                if (!instance.isChunkLoaded(x >> 4, z >> 4)) {
                    return false;
                }
                for (var y = minY; y <= maxY; y++) {
                    var block = instance.getBlock(x, y, z);
                    if (isUnsafe(block)) {
                        return false;
                    }
                    var shape = block.collisionShape();
                    if (shape.intersectBox(test.sub(x, y, z), PLAYER_BOX)) {
                        return false;
                    }
                }
            }
        }

        var floorY = (int) Math.floor(pos.y() - EPSILON);
        var supported = false;
        for (var x = minX; x <= maxX; x++) {
            for (var z = minZ; z <= maxZ; z++) {
                var floor = instance.getBlock(x, floorY, z);
                if (isHazard(floor)) {
                    return false;
                }
                supported |=
                        floor.collisionShape()
                                .intersectBox(pos.sub(x, floorY + EPSILON, z), FOOTPRINT);
            }
        }
        return supported;
    }

    private static boolean isUnsafe(Block block) {
        return block.liquid()
                || block.fluid()
                || "true".equals(block.getProperty("waterlogged"))
                || isHazard(block);
    }

    private static boolean isHazard(Block block) {
        return HAZARDS.contains(block.key().value());
    }

    private static Pos randomSpawn(Instance instance) {
        var random = ThreadLocalRandom.current();
        for (var attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            var x = worldSpawn.blockX() + random.nextInt(-SPAWN_RADIUS, SPAWN_RADIUS + 1);
            var z = worldSpawn.blockZ() + random.nextInt(-SPAWN_RADIUS, SPAWN_RADIUS + 1);
            var surface = surfaceAt(instance, x, z);
            if (surface != null && isSafeStandingSpot(instance, surface)) {
                return surface;
            }
        }
        return searchSpawn(instance);
    }

    private static Pos searchSpawn(Instance instance) {
        var cached = searchedSpawn;
        if (cached != null && isSafeStandingSpot(instance, cached)) {
            return cached;
        }
        searchedSpawn = findSearchedSpawn(instance);
        return searchedSpawn;
    }

    private static Pos findSearchedSpawn(Instance instance) {
        for (var radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    var surface =
                            surfaceAt(instance, worldSpawn.blockX() + dx, worldSpawn.blockZ() + dz);
                    if (surface != null && isSafeStandingSpot(instance, surface)) {
                        return surface;
                    }
                }
            }
        }

        MinecraftServer.LOGGER.warn(
                "No safe respawn spot within {} blocks of world spawn", SEARCH_RADIUS);
        var surface = surfaceAt(instance, worldSpawn.blockX(), worldSpawn.blockZ());
        return surface != null ? surface : worldSpawn;
    }

    private static @Nullable Pos surfaceAt(Instance instance, int x, int z) {
        if (!instance.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        var dimension = instance.getCachedDimensionType();
        for (var y = dimension.maxY() - 1; y > dimension.minY(); y--) {
            var block = instance.getBlock(x, y, z);
            if (block.liquid()) {
                return null;
            }
            var height = height(block);
            if (height > EPSILON && !block.key().value().endsWith("_leaves")) {
                return height > 1.0 ? null : new Pos(x + 0.5, y + height, z + 0.5);
            }
        }
        return null;
    }

    private static double height(Block block) {
        return block.collisionShape().relativeEnd().y();
    }

    private static Vec horizontal(String facing) {
        return switch (facing) {
            case "north" -> new Vec(0, 0, -1);
            case "south" -> new Vec(0, 0, 1);
            case "west" -> new Vec(-1, 0, 0);
            default -> new Vec(1, 0, 0);
        };
    }

    private static Vec centerOf(Point block) {
        return new Vec(block.blockX() + 0.5, block.blockY() + 0.5, block.blockZ() + 0.5);
    }
}
