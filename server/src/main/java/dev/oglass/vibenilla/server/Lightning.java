package dev.oglass.vibenilla.server;

import java.util.HashSet;
import java.util.random.RandomGenerator;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.time.TimeUnit;

/**
 * Thunderstorm lightning. Each chunk near a player has a 1 in 100000 chance per tick of a strike at
 * a random column's surface, which burns the ground and hurts whatever stands close by.
 */
final class Lightning {
    private static final int CHUNK_CHANCE = 100_000;
    private static final int CHUNK_RADIUS = 10;
    private static final int FIRE_SPARKS = 4;
    private static final int FIRE_TICKS = 8 * 20;
    private static final float DAMAGE = 5.0F;
    private static final float COLD_ENOUGH_FOR_SNOW = 0.15F;

    private Lightning() {}

    static void tick(Instance instance, RandomGenerator random) {
        var chunks = new HashSet<Long>();
        for (var player : instance.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            var position = player.getPosition();
            for (var x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) {
                for (var z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) {
                    chunks.add(chunkIndex(position.chunkX() + x, position.chunkZ() + z));
                }
            }
        }

        for (long index : chunks) {
            if (random.nextInt(CHUNK_CHANCE) == 0) {
                var chunkX = (int) (index >> 32);
                var chunkZ = (int) index;
                if (instance.isChunkLoaded(chunkX, chunkZ)) {
                    strikeIn(instance, chunkX, chunkZ, random);
                }
            }
        }
    }

    private static void strikeIn(
            Instance instance, int chunkX, int chunkZ, RandomGenerator random) {
        var x = (chunkX << 4) + random.nextInt(16);
        var z = (chunkZ << 4) + random.nextInt(16);
        var chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        var y = chunk.motionBlockingHeightmap().getHeight(x & 15, z & 15) + 1;
        if (!isRainingAt(instance, x, y, z)) {
            return;
        }

        var target = new Pos(x + 0.5, y, z + 0.5);
        var bolt = new Entity(EntityType.LIGHTNING_BOLT);
        bolt.setInstance(instance, target);
        bolt.scheduleRemove(20, TimeUnit.SERVER_TICK);

        spreadFire(instance, new Vec(x, y, z), random);
        for (var entity : instance.getNearbyEntities(target, 12)) {
            if (entity instanceof LivingEntity living && isStruck(living.getPosition(), target)) {
                living.setFireTicks(Math.max(living.getFireTicks(), FIRE_TICKS));
                living.damage(DamageType.LIGHTNING_BOLT, DAMAGE);
            }
        }
    }

    private static boolean isStruck(Point position, Point target) {
        var dy = position.y() - target.y();
        return Math.abs(position.x() - target.x()) <= 3.0
                && Math.abs(position.z() - target.z()) <= 3.0
                && dy >= -3.0
                && dy <= 9.0;
    }

    private static void spreadFire(Instance instance, Point origin, RandomGenerator random) {
        igniteIfPossible(instance, origin);
        for (var spark = 0; spark < FIRE_SPARKS; spark++) {
            igniteIfPossible(
                    instance,
                    origin.add(
                            random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1));
        }
    }

    private static void igniteIfPossible(Instance instance, Point position) {
        var rule = MinecraftServer.getBlockManager().getBlockPlacementRule(Block.FIRE);
        if (rule == null
                || !instance.isChunkLoaded(position)
                || !instance.getBlock(position).air()) {
            return;
        }

        var fire =
                rule.blockPlace(
                        new BlockPlacementRule.PlacementState(
                                instance,
                                Block.FIRE,
                                BlockFace.TOP,
                                position,
                                Vec.ZERO,
                                Pos.ZERO,
                                ItemStack.AIR,
                                false));
        if (fire != null) {
            instance.setBlock(position, fire);
        }
    }

    private static boolean isRainingAt(Instance instance, int x, int y, int z) {
        var biome = instance.getBiome(x, y, z).resolve(instance.registries().biome());
        return biome != null
                && biome.hasPrecipitation()
                && biome.temperature() >= COLD_ENOUGH_FOR_SNOW;
    }

    private static long chunkIndex(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
