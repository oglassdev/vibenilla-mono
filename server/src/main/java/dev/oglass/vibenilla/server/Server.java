package dev.oglass.vibenilla.server;

import io.github.togar2.fluids.MinestomFluids;
import io.github.togar2.pvp.MinestomPvP;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.timer.TaskSchedule;
import rocks.minestom.crafting.CraftingManager;
import rocks.minestom.placement.Registrations;

public final class Server {
    public static void main(String[] args) {
        var dataPath = Path.of(System.getProperty("vibenilla.data", "vanilla"));
        var vanillaData = dataPath.resolve("data").resolve("minecraft");
        var worldPath = Path.of(System.getProperty("vibenilla.world", "world"));
        var port = Integer.getInteger("vibenilla.port", 25565);

        var server = MinecraftServer.init(new Auth.Online());
        var events = MinecraftServer.getGlobalEventHandler();

        // Placement rules first: fluids wraps whatever rule is already registered.
        Registrations.registerAllVanilla(MinecraftServer.getBlockManager());
        MinestomFluids.init();
        MinestomPvP.init();

        var combat = CombatFeatures.modernVanilla();
        var world = new WorldStorage(worldPath, dataPath, Long.getLong("vibenilla.seed"));
        var overworld = world.instance();
        overworld.setExplosionSupplier(combat.get(FeatureType.EXPLOSION).getExplosionSupplier());
        var spawn = world.spawn();
        var players = new PlayerStorage(worldPath.resolve("playerdata"));
        var saved = new ConcurrentHashMap<UUID, CompoundBinaryTag>();

        var crafting = new CraftingManager(vanillaData);

        events.addChild(combat.createNode())
                .addChild(MinestomFluids.events())
                .addChild(MinestomFluids.bucketEvents())
                .addChild(Items.events())
                .addChild(crafting.events())
                .addChild(new BlockDrops(vanillaData.resolve("loot_table")).events())
                .addListener(
                        AsyncPlayerConfigurationEvent.class,
                        event -> {
                            var player = event.getPlayer();
                            event.setSpawningInstance(overworld);
                            var data = players.read(player.getUuid());
                            var position = data == null ? null : PlayerStorage.position(data);
                            if (position == null) {
                                player.setRespawnPoint(spawn);
                            } else {
                                saved.put(player.getUuid(), data);
                                player.setRespawnPoint(position);
                            }
                        })
                .addListener(
                        PlayerSpawnEvent.class,
                        event -> {
                            if (!event.isFirstSpawn()) {
                                return;
                            }
                            var player = event.getPlayer();
                            var data = saved.remove(player.getUuid());
                            if (data == null) {
                                player.setGameMode(GameMode.SURVIVAL);
                                return;
                            }
                            PlayerStorage.restore(player, data);
                            var respawn = PlayerStorage.respawnPoint(data);
                            player.setRespawnPoint(respawn == null ? spawn : respawn);
                        })
                .addListener(
                        PlayerDisconnectEvent.class,
                        event -> {
                            saved.remove(event.getPlayer().getUuid());
                            players.save(event.getPlayer());
                        });

        Runnable savePlayers =
                () ->
                        MinecraftServer.getConnectionManager()
                                .getOnlinePlayers()
                                .forEach(players::save);
        var scheduler = MinecraftServer.getSchedulerManager();
        scheduler
                .buildTask(
                        () -> {
                            savePlayers.run();
                            world.save(false);
                        })
                .repeat(TaskSchedule.minutes(5))
                .schedule();
        scheduler.buildShutdownTask(
                () -> {
                    savePlayers.run();
                    world.save(true);
                    MinecraftServer.LOGGER.info("World saved");
                });

        MinecraftServer.getCommandManager().register(new GameModeCommand());

        server.start("0.0.0.0", port);
        MinecraftServer.LOGGER.info("Listening on port {} with seed {}", port, world.seed());
    }
}
