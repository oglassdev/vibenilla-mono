package dev.oglass.vibenilla.server;

import dev.oglass.vibenilla.containers.Containers;
import dev.oglass.vibenilla.containers.CookingFuels;
import dev.oglass.vibenilla.containers.Furnaces;
import dev.oglass.vibenilla.ticking.LeavesDecayEvent;
import dev.oglass.vibenilla.ticking.RandomTicks;
import dev.oglass.vibenilla.ticking.TntIgniteEvent;
import dev.oglass.vibenilla.ticking.VanillaRandomTicks;
import io.github.togar2.fluids.MinestomFluids;
import io.github.togar2.pvp.MinestomPvP;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.explosion.ExplosionFeature;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.timer.TaskSchedule;
import rocks.minestom.crafting.CookingRecipes;
import rocks.minestom.crafting.CraftingManager;
import rocks.minestom.placement.Registrations;

public final class Server {
    private static final int TNT_FUSE_TICKS = 80;

    public static void main(String[] args) {
        var dataPath = Path.of(System.getProperty("vibenilla.data", "vanilla"));
        var vanillaData = dataPath.resolve("data").resolve("minecraft");
        var worldPath = Path.of(System.getProperty("vibenilla.world", "world"));
        var port = Integer.getInteger("vibenilla.port", 25565);

        var server = MinecraftServer.init(new Auth.Online());
        var events = MinecraftServer.getGlobalEventHandler();

        // Placement rules first: fluids wraps whatever rule is already registered.
        Registrations.registerAllVanilla(MinecraftServer.getBlockManager());
        var containers = Containers.register(MinecraftServer.getBlockManager());
        var furnaces =
                Furnaces.register(
                        MinecraftServer.getBlockManager(),
                        new CookingRecipes(vanillaData),
                        new CookingFuels(vanillaData),
                        (instance, position, points) ->
                                Experience.spawnOrbs(instance, position, points, false));
        MinestomFluids.init();
        MinestomPvP.init();

        var combat = CombatFeatures.modernVanilla();
        var world = new WorldStorage(worldPath, dataPath, Long.getLong("vibenilla.seed"));
        var overworld = world.instance();
        overworld.setExplosionSupplier(combat.get(FeatureType.EXPLOSION).getExplosionSupplier());
        var spawn = world.spawn();
        var environment =
                new WorldEnvironment(worldPath.resolve("environment.properties"), overworld);
        var players = new PlayerStorage(worldPath.resolve("playerdata"));
        var saved = new ConcurrentHashMap<Player, CompoundBinaryTag>();

        var crafting = new CraftingManager(vanillaData);
        var randomTicks = new RandomTicks();
        VanillaRandomTicks.register(randomTicks, dataPath);

        var drops = new BlockDrops(vanillaData.resolve("loot_table"));
        var explosions = combat.get(FeatureType.EXPLOSION);
        var breaking = new BlockBreaking(combat.get(FeatureType.ITEM_DAMAGE), drops);

        // Breaking runs before combat so a rejected break adds no exhaustion.
        events.addChild(breaking.events())
                .addChild(combat.createNode())
                .addChild(containers)
                .addChild(furnaces)
                .addChild(MinestomFluids.events())
                .addChild(MinestomFluids.bucketEvents())
                .addChild(randomTicks.events())
                .addListener(
                        LeavesDecayEvent.class,
                        event ->
                                drops.drop(
                                        event.getInstance(), event.getPosition(), event.getBlock()))
                .addListener(
                        TntIgniteEvent.class,
                        event ->
                                explosions.primeExplosive(
                                        event.getInstance(),
                                        event.getPosition(),
                                        new ExplosionFeature.IgnitionCause.Explosion(null),
                                        TNT_FUSE_TICKS))
                .addChild(Items.events())
                .addChild(Deaths.events())
                .addChild(Experience.events())
                .addChild(Respawns.events(spawn))
                .addChild(Sleeping.events(overworld, environment.weathers()))
                .addChild(crafting.events())
                .addListener(
                        AsyncPlayerConfigurationEvent.class,
                        event -> {
                            var player = event.getPlayer();
                            event.setSpawningInstance(overworld);
                            if (!players.join(player)) {
                                return;
                            }
                            var data = players.load(player);
                            var position =
                                    data == null || PlayerStorage.dead(data)
                                            ? null
                                            : PlayerStorage.position(data);
                            if (data != null) {
                                saved.put(player, data);
                                if (!player.isOnline()) {
                                    saved.remove(player);
                                }
                            }
                            player.setRespawnPoint(position == null ? spawn : position);
                        })
                .addListener(
                        PlayerSpawnEvent.class,
                        event -> {
                            if (!event.isFirstSpawn()) {
                                return;
                            }
                            var player = event.getPlayer();
                            var data = saved.remove(player);
                            if (data == null) {
                                player.setGameMode(GameMode.SURVIVAL);
                                return;
                            }
                            PlayerStorage.restore(player, data);
                            crafting.restoreRecipeBook(player, PlayerStorage.recipeBook(data));
                            var respawn = PlayerStorage.respawnPoint(data);
                            player.setRespawnPoint(respawn == null ? spawn : respawn);
                            if (PlayerStorage.dead(data)) {
                                player.teleport(Respawns.resolve(player));
                            }
                        })
                .addListener(
                        PlayerDisconnectEvent.class,
                        event -> {
                            saved.remove(event.getPlayer());
                            players.finish(event.getPlayer());
                        });

        var scheduler = MinecraftServer.getSchedulerManager();
        scheduler
                .buildTask(
                        () -> {
                            MinecraftServer.getConnectionManager()
                                    .getOnlinePlayers()
                                    .forEach(players::save);
                            world.save();
                            environment.save();
                        })
                .repeat(TaskSchedule.minutes(5))
                .schedule();
        scheduler.buildShutdownTask(
                () -> {
                    players.close();
                    world.saveAndWait();
                    environment.save();
                    MinecraftServer.LOGGER.info("World saved");
                });

        var commands = MinecraftServer.getCommandManager();
        commands.register(new GameModeCommand());
        commands.register(new WeatherCommand(environment.weathers()));
        commands.register(new TimeCommand(overworld));

        server.start("0.0.0.0", port);
        MinecraftServer.LOGGER.info("Listening on port {} with seed {}", port, world.seed());
    }
}
