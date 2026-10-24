package dev.oglass.vibenilla.ticking;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

/**
 * Vanilla {@code FireBlock}. Fire runs on scheduled ticks rather than random ticks: every fire
 * started here, placed by a player, or found by a random tick schedules itself every 30 to 39 ticks
 * for as long as it burns.
 */
final class Fire {
    private static final int MAX_AGE = 15;
    private static final int BASE_DELAY = 30;
    private static final int DELAY_VARIATION = 10;
    private static final int DIFFICULTY_BONUS = 2 * 7;
    private static final Set<String> HUMID_BIOMES =
            Set.of("jungle", "bamboo_jungle", "sparse_jungle", "swamp", "mangrove_swamp");
    private static final Tag<Set<BlockVec>> SCHEDULED = Tag.Transient("random-ticks-fire");

    private Fire() {}

    static void register(RandomTicks ticks) {
        ticks.register(
                Block.FIRE,
                (instance, position, block, random) -> schedule(instance, position, random));
        ticks.events()
                .addListener(
                        PlayerBlockPlaceEvent.class,
                        event -> {
                            if (event.getBlock().compare(Block.FIRE)) {
                                schedule(
                                        event.getInstance(),
                                        event.getBlockPosition(),
                                        ThreadLocalRandom.current());
                            }
                        });
    }

    /** Starts a fire if the position can hold one. */
    static void ignite(Instance instance, BlockVec position, RandomGenerator random) {
        var fire = stateAt(instance, position, 0);
        if (fire.air()) {
            return;
        }
        Level.setAndUpdate(instance, position, fire);
        schedule(instance, position, random);
    }

    private static void schedule(Instance instance, BlockVec position, RandomGenerator random) {
        var scheduled =
                instance.updateAndGetTag(
                        SCHEDULED, set -> set != null ? set : ConcurrentHashMap.newKeySet());
        if (!scheduled.add(position)) {
            return;
        }

        instance.scheduler()
                .buildTask(
                        () -> {
                            scheduled.remove(position);
                            tick(instance, position, ThreadLocalRandom.current());
                        })
                .delay(TaskSchedule.tick(BASE_DELAY + random.nextInt(DELAY_VARIATION)))
                .schedule();
    }

    static void tick(Instance instance, BlockVec position, RandomGenerator random) {
        if (!Level.isLoaded(instance, position)) {
            return;
        }
        var fire = Level.block(instance, position);
        if (!fire.compare(Block.FIRE)) {
            return;
        }
        schedule(instance, position, random);

        var age = Level.property(fire, "age");
        if (stateAt(instance, position, age).air()) {
            Level.setAndUpdate(instance, position, Block.AIR);
            return;
        }

        var below = Level.block(instance, position.add(0, -1, 0));
        var infiniburn = isInfiniburn(instance, below);
        if (!infiniburn
                && isNearRain(instance, position)
                && random.nextFloat() < 0.2F + age * 0.03F) {
            Level.setAndUpdate(instance, position, Block.AIR);
            return;
        }

        var newAge = Math.min(MAX_AGE, age + random.nextInt(3) / 2);
        if (newAge != age) {
            Level.set(instance, position, Level.withProperty(fire, "age", newAge));
        }

        if (!infiniburn) {
            if (!hasFlammableNeighbor(instance, position)) {
                if (!isSturdyTop(below) || age > 3) {
                    Level.setAndUpdate(instance, position, Block.AIR);
                }
                return;
            }
            if (age == MAX_AGE && random.nextInt(4) == 0 && !Flammability.canBurn(below)) {
                Level.setAndUpdate(instance, position, Block.AIR);
                return;
            }
        }

        var humid = isHumid(instance, position);
        var humidity = humid ? -50 : 0;
        checkBurnOut(instance, position.add(1, 0, 0), 300 + humidity, random, age);
        checkBurnOut(instance, position.add(-1, 0, 0), 300 + humidity, random, age);
        checkBurnOut(instance, position.add(0, -1, 0), 250 + humidity, random, age);
        checkBurnOut(instance, position.add(0, 1, 0), 250 + humidity, random, age);
        checkBurnOut(instance, position.add(0, 0, -1), 300 + humidity, random, age);
        checkBurnOut(instance, position.add(0, 0, 1), 300 + humidity, random, age);

        for (var x = -1; x <= 1; x++) {
            for (var z = -1; z <= 1; z++) {
                for (var y = -1; y <= 4; y++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }

                    var rate = y > 1 ? 100 + (y - 1) * 100 : 100;
                    var target = position.add(x, y, z);
                    var igniteOdds = igniteOdds(instance, target);
                    if (igniteOdds <= 0) {
                        continue;
                    }

                    var odds = (igniteOdds + 40 + DIFFICULTY_BONUS) / (age + 30);
                    if (humid) {
                        odds /= 2;
                    }
                    if (odds > 0 && random.nextInt(rate) <= odds && !isNearRain(instance, target)) {
                        var spreadAge = Math.min(MAX_AGE, age + random.nextInt(5) / 4);
                        var spread = stateAt(instance, target, spreadAge);
                        if (!spread.air()) {
                            Level.setAndUpdate(instance, target, spread);
                            schedule(instance, target, random);
                        }
                    }
                }
            }
        }
    }

    private static void checkBurnOut(
            Instance instance, BlockVec position, int chance, RandomGenerator random, int age) {
        var block = Level.block(instance, position);
        if (random.nextInt(chance) >= Flammability.burnOdds(block)) {
            return;
        }

        if (random.nextInt(age + 10) < 5 && !Level.isRainingAt(instance, position)) {
            var newAge = Math.min(age + random.nextInt(5) / 4, MAX_AGE);
            var fire = stateAt(instance, position, newAge);
            if (!fire.air()) {
                Level.setAndUpdate(instance, position, fire);
                schedule(instance, position, random);
                return;
            }
        }
        Level.setAndUpdate(instance, position, Block.AIR);
    }

    /**
     * The fire block at the position as the fire placement rule shapes it, or air if it cannot
     * stand.
     */
    private static Block stateAt(Instance instance, BlockVec position, int age) {
        var fire = Level.withProperty(Block.FIRE, "age", age);
        var rule = MinecraftServer.getBlockManager().getBlockPlacementRule(Block.FIRE);
        if (rule == null) {
            return fire;
        }
        return rule.blockUpdate(
                new BlockPlacementRule.UpdateState(instance, position, fire, BlockFace.BOTTOM));
    }

    private static int igniteOdds(Instance instance, BlockVec position) {
        if (!Level.block(instance, position).air()) {
            return 0;
        }

        var odds = 0;
        for (var face : BlockFace.values()) {
            odds =
                    Math.max(
                            odds,
                            Flammability.igniteOdds(
                                    Level.block(instance, position.relative(face))));
        }
        return odds;
    }

    private static boolean hasFlammableNeighbor(Instance instance, BlockVec position) {
        for (var face : BlockFace.values()) {
            if (Flammability.canBurn(Level.block(instance, position.relative(face)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSturdyTop(Block block) {
        return block.solid() && block.collisionShape().isFaceFull(BlockFace.TOP);
    }

    private static boolean isNearRain(Instance instance, Point position) {
        return Level.isRainingAt(instance, position)
                || Level.isRainingAt(instance, position.add(-1, 0, 0))
                || Level.isRainingAt(instance, position.add(1, 0, 0))
                || Level.isRainingAt(instance, position.add(0, 0, -1))
                || Level.isRainingAt(instance, position.add(0, 0, 1));
    }

    private static boolean isInfiniburn(Instance instance, Block below) {
        var tag = instance.getCachedDimensionType().infiniburn();
        return Level.hasTag(below, tag.startsWith("#") ? tag.substring(1) : tag);
    }

    private static boolean isHumid(Instance instance, BlockVec position) {
        var biome = instance.getBiome(position.blockX(), position.blockY(), position.blockZ());
        return HUMID_BIOMES.contains(biome.key().value());
    }
}
