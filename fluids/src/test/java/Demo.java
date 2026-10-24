import io.github.togar2.fluids.MinestomFluids;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;

void main() {
    var server = MinecraftServer.init();
    var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
    instance.setChunkSupplier(LightingChunk::new);
    instance.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.STONE));

    MinecraftServer.getGlobalEventHandler()
            .addChild(MinestomFluids.events())
            .addChild(MinestomFluids.bucketEvents())
            .addListener(
                    AsyncPlayerConfigurationEvent.class,
                    event -> {
                        var spawnPosition = new Pos(0.0, 40.0, 0.0);
                        event.setSpawningInstance(instance);
                        event.getPlayer().setRespawnPoint(spawnPosition);
                    })
            .addListener(
                    PlayerSpawnEvent.class,
                    event -> event.getPlayer().setGameMode(GameMode.CREATIVE));

    MinestomFluids.init();
    server.start("0.0.0.0", 25565);
}
