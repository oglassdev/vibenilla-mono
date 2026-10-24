package dev.oglass.vibenilla.server;

import io.github.togar2.fluids.MinestomFluids;
import io.github.togar2.pvp.MinestomPvP;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.world.DimensionType;
import rocks.minestom.crafting.CraftingManager;
import rocks.minestom.placement.Registrations;
import rocks.minestom.worldgen.WorldGenerators;

public final class Server {
    public static void main(String[] args) {
        var dataPath = Path.of(System.getProperty("vibenilla.data", "vanilla"));
        var vanillaData = dataPath.resolve("data").resolve("minecraft");
        var seed = Long.getLong("vibenilla.seed", ThreadLocalRandom.current().nextLong());
        var port = Integer.getInteger("vibenilla.port", 25565);

        var server = MinecraftServer.init(new Auth.Online());
        var events = MinecraftServer.getGlobalEventHandler();

        // Placement rules first: fluids wraps whatever rule is already registered.
        Registrations.registerAllVanilla(MinecraftServer.getBlockManager());
        MinestomFluids.init();
        MinestomPvP.init();

        var combat = CombatFeatures.modernVanilla();
        var overworld = createOverworld(dataPath, seed);
        overworld.setExplosionSupplier(combat.get(FeatureType.EXPLOSION).getExplosionSupplier());
        var spawn = findSurface(overworld, 0, 0);

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
                            event.setSpawningInstance(overworld);
                            event.getPlayer().setRespawnPoint(spawn);
                        })
                .addListener(
                        PlayerSpawnEvent.class,
                        event -> {
                            if (event.isFirstSpawn()) {
                                event.getPlayer().setGameMode(GameMode.SURVIVAL);
                            }
                        });

        MinecraftServer.getCommandManager().register(new GameModeCommand());

        server.start("0.0.0.0", port);
        MinecraftServer.LOGGER.info("Listening on port {} with seed {}", port, seed);
    }

    private static Instance createOverworld(Path dataPath, long seed) {
        var generators = new WorldGenerators(dataPath, seed);
        var instance =
                MinecraftServer.getInstanceManager()
                        .createInstanceContainer(DimensionType.OVERWORLD);
        instance.setChunkSupplier(LightingChunk::new);
        instance.setGenerator(generators.overworld());
        return instance;
    }

    private static Pos findSurface(Instance instance, int x, int z) {
        instance.loadChunk(x >> 4, z >> 4).join();
        var dimension = instance.getCachedDimensionType();
        for (var y = dimension.maxY() - 1; y > dimension.minY(); y--) {
            if (!instance.getBlock(x, y, z).air()) {
                return new Pos(x + 0.5, y + 1, z + 0.5);
            }
        }
        return new Pos(x + 0.5, 64, z + 0.5);
    }
}
