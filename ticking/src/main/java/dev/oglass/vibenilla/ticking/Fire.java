package dev.oglass.vibenilla.ticking;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.EventDispatcher;
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
 * for as long as it burns and a player is near enough to simulate it. Fire left alone is picked up
 * again by the random tick that finds it once a player returns.
 */
final class Fire {
    private static final int MAX_AGE = 15;
    private static final int BASE_DELAY = 30;
    private static final int DELAY_VARIATION = 10;
    private static final int DIFFICULTY_BONUS = 2 * 7;
    private static final String INCREASED_BURNOUT = "minecraft:gameplay/increased_fire_burnout";
    private static final Tag<Set<BlockVec>> SCHEDULED = Tag.Transient("random-ticks-fire");

    private final RandomTicks ticks;
    private final Set<String> humidBiomes;

    private Fire(RandomTicks ticks, Set<String> humidBiomes) {
        this.ticks = ticks;
        this.humidBiomes = humidBiomes;
    }

    /**
     * @param humidBiomes the biomes whose environment sets {@code increased_fire_burnout}
     */
    static Fire register(RandomTicks ticks, Set<String> humidBiomes) {
        var fire = new Fire(ticks, humidBiomes);
        ticks.register(
                Block.FIRE,
                (instance, position, block, random) -> fire.schedule(instance, position, random));
        ticks.events()
                .addListener(
                        PlayerBlockPlaceEvent.class,
                        event -> {
                            if (event.getBlock().compare(Block.FIRE)) {
                                fire.schedule(
                                        event.getInstance(),
                                        event.getBlockPosition(),
                                        ThreadLocalRandom.current());
                            }
                        });
        return fire;
    }

    /** The biomes of the vanilla data that set {@code increased_fire_burnout}. */
    static Set<String> humidBiomes(Path dataRoot) {
        var biomes = new HashSet<String>();
        var directory = dataRoot.resolve("data/minecraft/worldgen/biome");
        try (var files = Files.list(directory)) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(file)) {
                    var attribute =
                            JsonParser.parseReader(reader)
                                    .getAsJsonObject()
                                    .getAsJsonObject("attributes")
                                    .get(INCREASED_BURNOUT);
                    if (attribute != null && attribute.getAsBoolean()) {
                        var name = file.getFileName().toString();
                        biomes.add(name.substring(0, name.length() - ".json".length()));
                    }
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return Set.copyOf(biomes);
    }

    /** Starts a fire if the position can hold one. */
    void ignite(Instance instance, BlockVec position, RandomGenerator random) {
        var fire = stateAt(instance, position, 0);
        if (fire.air()) {
            return;
        }
        Level.setAndUpdate(instance, position, fire);
        schedule(instance, position, random);
    }

    private void schedule(Instance instance, BlockVec position, RandomGenerator random) {
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

    /**
     * Burns once and schedules the next tick if the fire is still there and a player is near
     * enough. A failing tick is not scheduled again.
     */
    void tick(Instance instance, BlockVec position, RandomGenerator random) {
        if (!isSimulated(instance, position)) {
            return;
        }
        var fire = Level.block(instance, position);
        if (fire.compare(Block.FIRE) && burn(instance, position, fire, random)) {
            schedule(instance, position, random);
        }
    }

    private boolean isSimulated(Instance instance, BlockVec position) {
        return hasLoadedNeighbors(instance, position)
                && this.ticks.isSimulated(instance, position.blockX() >> 4, position.blockZ() >> 4);
    }

    private static boolean hasLoadedNeighbors(Instance instance, BlockVec position) {
        for (var face : BlockFace.values()) {
            if (!Level.isLoaded(instance, position.relative(face))) {
                return false;
            }
        }
        return true;
    }

    /** Returns whether the fire is still burning. */
    private boolean burn(Instance instance, BlockVec position, Block fire, RandomGenerator random) {
        var age = Level.property(fire, "age");
        if (stateAt(instance, position, age).air()) {
            Level.setAndUpdate(instance, position, Block.AIR);
            return false;
        }

        var below = Level.block(instance, position.add(0, -1, 0));
        var infiniburn = isInfiniburn(instance, below);
        if (!infiniburn
                && isNearRain(instance, position)
                && random.nextFloat() < 0.2F + age * 0.03F) {
            Level.setAndUpdate(instance, position, Block.AIR);
            return false;
        }

        var newAge = Math.min(MAX_AGE, age + random.nextInt(3) / 2);
        if (newAge != age) {
            Level.set(instance, position, Level.withProperty(fire, "age", newAge));
        }

        if (!infiniburn) {
            if (!hasFlammableNeighbor(instance, position)) {
                if (!isSturdyTop(below) || age > 3) {
                    Level.setAndUpdate(instance, position, Block.AIR);
                    return false;
                }
                return true;
            }
            if (age == MAX_AGE && random.nextInt(4) == 0 && !Flammability.canBurn(below)) {
                Level.setAndUpdate(instance, position, Block.AIR);
                return false;
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
        return true;
    }

    private void checkBurnOut(
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
                prime(instance, position, block);
                return;
            }
        }
        Level.setAndUpdate(instance, position, Block.AIR);
        prime(instance, position, block);
    }

    private static void prime(Instance instance, BlockVec position, Block burnt) {
        if (burnt.compare(Block.TNT)) {
            EventDispatcher.call(new TntIgniteEvent(instance, position));
        }
    }

    /**
     * The fire block at the position as the fire placement rule shapes it, or air if it cannot
     * stand or its neighbors are not loaded.
     */
    private static Block stateAt(Instance instance, BlockVec position, int age) {
        if (!hasLoadedNeighbors(instance, position)) {
            return Block.AIR;
        }
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

    private boolean isHumid(Instance instance, BlockVec position) {
        var biome = instance.getBiome(position.blockX(), position.blockY(), position.blockZ());
        return this.humidBiomes.contains(biome.key().value());
    }
}
