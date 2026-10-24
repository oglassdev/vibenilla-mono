import io.github.togar2.fluids.FluidState;
import io.github.togar2.fluids.MinestomFluids;
import io.github.togar2.fluids.WaterFluid;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.item.Material;

void main() {
    var server = MinecraftServer.init();
    var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
    instance.setChunkSupplier(LightingChunk::new);
    instance.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.STONE));

    MinecraftServer.getGlobalEventHandler()
            .addChild(MinestomFluids.events())
            .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                var spawnPosition = new Pos(0.0, 40.0, 0.0);
                event.setSpawningInstance(instance);
                event.getPlayer().setRespawnPoint(spawnPosition);
            })
            .addListener(PlayerSpawnEvent.class, event -> event.getPlayer().setGameMode(GameMode.CREATIVE))
            .addListener(PlayerBlockInteractEvent.class, event -> {
                var blockInstance = event.getInstance();
                var blockPosition = event.getBlockPosition();
                var block = event.getBlock();
                var material = event.getPlayer().getItemInHand(event.getHand()).material();
                var waterlogHandler = MinestomFluids.getWaterlog(block);

                if (material == Material.WATER_BUCKET) {
                    var target = waterlogHandler != null ? blockPosition : blockPosition.relative(event.getBlockFace());

                    if (WaterFluid.evaporates(blockInstance)) {
                        WaterFluid.evaporate(blockInstance, target);
                    } else if (waterlogHandler != null) {
                        waterlogHandler.placeFluid(blockInstance, target, MinestomFluids.WATER.getDefaultState());
                    } else {
                        placeFluid(blockInstance, target, Block.WATER);
                    }
                } else if (material == Material.LAVA_BUCKET) {
                    placeFluid(blockInstance, blockPosition.relative(event.getBlockFace()), Block.LAVA);
                } else if (material == Material.BUCKET) {
                    if (waterlogHandler != null && waterlogHandler.canRemoveFluid(blockInstance, blockPosition, FluidState.of(block))) {
                        blockInstance.setBlock(blockPosition, FluidState.setWaterlogged(block, false));
                    } else if (block.liquid() && FluidState.isSource(block)) {
                        event.getPlayer().setItemInHand(event.getHand(), FluidState.of(block).fluid().getBucket());
                        blockInstance.setBlock(blockPosition, Block.AIR);
                    }
                }
            })
            .addListener(PlayerBlockBreakEvent.class, event -> {
                if (FluidState.isWaterlogged(event.getBlock())) {
                    event.setResultBlock(Block.WATER);
                }
            })
            .addListener(PlayerBlockPlaceEvent.class, event -> {
                var originalBlock = event.getInstance().getBlock(event.getBlockPosition());

                if (MinestomFluids.get(originalBlock) != MinestomFluids.EMPTY
                        && FluidState.isSource(originalBlock)
                        && FluidState.canBeWaterlogged(event.getBlock())) {
                    event.setBlock(FluidState.setWaterlogged(event.getBlock(), true));
                }
            });

    MinestomFluids.init();
    server.start("0.0.0.0", 25565);
}

private static void placeFluid(Instance instance, BlockVec blockPosition, Block fluid) {
    instance.placeBlock(new BlockHandler.Placement(fluid, instance.getBlock(blockPosition), instance, blockPosition));
}
