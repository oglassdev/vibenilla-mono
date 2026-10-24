package dev.oglass.vibenilla.server;

import io.github.togar2.fluids.FluidState;
import io.github.togar2.fluids.MinestomFluids;
import io.github.togar2.fluids.WaterFluid;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.item.Material;

/** Bucket filling and emptying, plus keeping waterlogged state on break and place. */
final class Buckets {
    private Buckets() {}

    static EventNode<Event> events() {
        return EventNode.all("buckets")
                .addListener(PlayerBlockInteractEvent.class, Buckets::onInteract)
                .addListener(
                        PlayerBlockBreakEvent.class,
                        event -> {
                            if (FluidState.isWaterlogged(event.getBlock())) {
                                event.setResultBlock(Block.WATER);
                            }
                        })
                .addListener(PlayerBlockPlaceEvent.class, Buckets::onPlace);
    }

    private static void onInteract(PlayerBlockInteractEvent event) {
        var instance = event.getInstance();
        var position = event.getBlockPosition();
        var block = event.getBlock();
        var material = event.getPlayer().getItemInHand(event.getHand()).material();
        var waterlog = MinestomFluids.getWaterlog(block);

        if (material == Material.WATER_BUCKET) {
            var target = waterlog != null ? position : position.relative(event.getBlockFace());
            if (WaterFluid.evaporates(instance)) {
                WaterFluid.evaporate(instance, target);
            } else if (waterlog != null) {
                waterlog.placeFluid(instance, target, MinestomFluids.WATER.getDefaultState());
            } else {
                place(instance, target, Block.WATER);
            }
        } else if (material == Material.LAVA_BUCKET) {
            place(instance, position.relative(event.getBlockFace()), Block.LAVA);
        } else if (material == Material.BUCKET) {
            if (waterlog != null
                    && waterlog.canRemoveFluid(instance, position, FluidState.of(block))) {
                instance.setBlock(position, FluidState.setWaterlogged(block, false));
            } else if (block.liquid() && FluidState.isSource(block)) {
                event.getPlayer()
                        .setItemInHand(event.getHand(), FluidState.of(block).fluid().getBucket());
                instance.setBlock(position, Block.AIR);
            }
        }
    }

    private static void onPlace(PlayerBlockPlaceEvent event) {
        var original = event.getInstance().getBlock(event.getBlockPosition());
        if (MinestomFluids.get(original) != MinestomFluids.EMPTY
                && FluidState.isSource(original)
                && FluidState.canBeWaterlogged(event.getBlock())) {
            event.setBlock(FluidState.setWaterlogged(event.getBlock(), true));
        }
    }

    private static void place(Instance instance, BlockVec position, Block fluid) {
        instance.placeBlock(
                new BlockHandler.Placement(fluid, instance.getBlock(position), instance, position));
    }
}
