package dev.oglass.vibenilla.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerRespawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

/** Chooses where a respawning player appears: a valid bed or anchor, else near world spawn. */
final class Respawns {
    private static final int SPAWN_RADIUS = 10;
    private static final int SPAWN_ATTEMPTS = 16;
    private static final double EPSILON = 1.0E-4;

    private final Pos worldSpawn;

    Respawns(Pos worldSpawn) {
        this.worldSpawn = worldSpawn;
    }

    EventNode<Event> events() {
        return EventNode.all("respawns").addListener(PlayerRespawnEvent.class, this::onRespawn);
    }

    private void onRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        var instance = player.getInstance();
        player.clearEffects();

        var point = player.getRespawnPoint();
        if (point == null || point.samePoint(this.worldSpawn)) {
            event.setRespawnPosition(this.randomSpawn(instance));
            return;
        }

        var position = findPersonalSpawn(instance, point);
        if (position == null) {
            player.setRespawnPoint(this.worldSpawn);
            player.sendMessage(Component.translatable("block.minecraft.spawn.not_valid"));
            position = this.randomSpawn(instance);
        }
        event.setRespawnPosition(position);
    }

    private static @Nullable Pos findPersonalSpawn(Instance instance, Point point) {
        instance.loadChunk(point).join();
        var block = instance.getBlock(point);

        if (block.key().value().endsWith("_bed")) {
            return findBedSpawn(instance, point, block);
        }
        if (block.compare(Block.RESPAWN_ANCHOR)) {
            return useAnchor(instance, point, block);
        }
        return null;
    }

    private static @Nullable Pos findBedSpawn(Instance instance, Point head, Block block) {
        var foot = head.sub(horizontal(block.getProperty("facing")));
        var spawn = findStandUp(instance, List.of(head, foot));
        return spawn == null ? null : spawn.withLookAt(centerOf(head));
    }

    private static @Nullable Pos useAnchor(Instance instance, Point anchor, Block block) {
        var charges = Integer.parseInt(block.getProperty("charges"));
        if (charges == 0) {
            return null;
        }
        var spawn = findStandUp(instance, List.of(anchor));
        if (spawn == null) {
            return null;
        }

        instance.setBlock(anchor, block.withProperty("charges", String.valueOf(charges - 1)));
        instance.playSound(
                Sound.sound(
                        SoundEvent.BLOCK_RESPAWN_ANCHOR_DEPLETE, Sound.Source.BLOCK, 1.0F, 1.0F),
                centerOf(anchor));
        return spawn.withLookAt(centerOf(anchor));
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
        instance.loadChunk(x >> 4, z >> 4).join();
        var floor = height(instance.getBlock(x, y - 1, z));
        if (floor <= EPSILON || floor > 1.0) {
            return null;
        }

        var feetY = y - 1 + floor;
        var clearance = feetY - y > 0.2 ? 3 : 2;
        for (var i = 0; i < clearance; i++) {
            if (!isPassable(instance.getBlock(x, y + i, z))) {
                return null;
            }
        }
        return new Pos(x + 0.5, feetY, z + 0.5);
    }

    private Pos randomSpawn(Instance instance) {
        var random = ThreadLocalRandom.current();
        for (var attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            var x = this.worldSpawn.blockX() + random.nextInt(-SPAWN_RADIUS, SPAWN_RADIUS + 1);
            var z = this.worldSpawn.blockZ() + random.nextInt(-SPAWN_RADIUS, SPAWN_RADIUS + 1);
            var surface = surfaceAt(instance, x, z);
            if (surface != null) {
                return surface;
            }
        }
        return this.worldSpawn;
    }

    private static @Nullable Pos surfaceAt(Instance instance, int x, int z) {
        instance.loadChunk(x >> 4, z >> 4).join();
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

    private static boolean isPassable(Block block) {
        return !block.liquid() && height(block) <= EPSILON;
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
