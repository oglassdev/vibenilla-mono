package io.github.togar2.fluids;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.block.BlockTags;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Bucket gameplay on the clicked block, and the waterlogging of blocks that players place into or
 * break out of water. Buckets only act on blocks the player clicks, never on the fluid in front of
 * them.
 */
final class FluidBuckets {
    private static final RegistryTag<Block> REPLACEABLE =
            Block.staticRegistry().getOrCreateTag(BlockTags.REPLACEABLE);

    private FluidBuckets() {}

    static EventNode<Event> events() {
        return EventNode.all("fluid-buckets")
                .addListener(PlayerBlockInteractEvent.class, FluidBuckets::onInteract)
                .addListener(PlayerBlockPlaceEvent.class, FluidBuckets::onPlace)
                .addListener(
                        PlayerBlockBreakEvent.class,
                        event -> {
                            if (FluidState.isWaterlogged(event.getBlock())) {
                                event.setResultBlock(Block.WATER);
                            }
                        });
    }

    private static void onInteract(PlayerBlockInteractEvent event) {
        var player = event.getPlayer();
        var gameMode = player.getGameMode();
        if (gameMode == GameMode.ADVENTURE || gameMode == GameMode.SPECTATOR) return;

        var instance = event.getInstance();
        var position = event.getBlockPosition();
        var held = player.getItemInHand(event.getHand());
        var material = held.material();

        if (material == Material.BUCKET) {
            var filled = fill(instance, position, event.getBlock());
            if (filled != null) exchange(player, event.getHand(), held, filled);
        } else if (material == MinestomFluids.WATER.getBucket().material()
                || material == MinestomFluids.LAVA.getBucket().material()) {
            var fluid =
                    material == MinestomFluids.WATER.getBucket().material()
                            ? MinestomFluids.WATER
                            : MinestomFluids.LAVA;
            if (empty(instance, position, event.getBlockFace(), fluid)) {
                exchange(player, event.getHand(), held, ItemStack.of(Material.BUCKET));
            }
        }
    }

    private static void onPlace(PlayerBlockPlaceEvent event) {
        var original = event.getInstance().getBlock(event.getBlockPosition());
        if (MinestomFluids.get(original) == MinestomFluids.WATER
                && original.liquid()
                && FluidState.isSource(original)
                && FluidState.canBeWaterlogged(event.getBlock())) {
            event.setBlock(FluidState.setWaterlogged(event.getBlock(), true));
        }
    }

    /**
     * Takes the fluid out of the block.
     *
     * @return the filled bucket, or null when the block holds no fluid that can be taken
     */
    private static @Nullable ItemStack fill(Instance instance, BlockVec position, Block block) {
        var handler = MinestomFluids.getWaterlog(block);
        Fluid fluid;
        if (handler != null && handler.canRemoveFluid(instance, position, FluidState.of(block))) {
            fluid = MinestomFluids.WATER;
            instance.setBlock(position, FluidState.setWaterlogged(block, false));
        } else if (block.liquid() && FluidState.isSource(block)) {
            fluid = FluidState.of(block).fluid();
            instance.setBlock(position, Block.AIR);
        } else {
            return null;
        }

        playSound(
                instance,
                position,
                fluid == MinestomFluids.LAVA
                        ? SoundEvent.ITEM_BUCKET_FILL_LAVA
                        : SoundEvent.ITEM_BUCKET_FILL);
        return fluid.getBucket();
    }

    /**
     * Places the fluid into the clicked block when it can be waterlogged, and next to it otherwise.
     *
     * @return whether the fluid was placed, or evaporated
     */
    private static boolean empty(
            Instance instance, BlockVec position, BlockFace face, Fluid fluid) {
        var clicked = instance.getBlock(position);
        var target =
                canWaterlog(instance, position, clicked, fluid)
                        ? position
                        : position.relative(face);
        var block = instance.getBlock(target);
        var waterlog = canWaterlog(instance, target, block, fluid);

        if (!waterlog && !block.liquid() && !REPLACEABLE.contains(block.registryKey()))
            return false;

        if (fluid == MinestomFluids.WATER && WaterFluid.evaporates(instance)) {
            WaterFluid.evaporate(instance, target);
            return true;
        }

        if (waterlog) {
            if (!MinestomFluids.getWaterlog(block)
                    .placeFluid(instance, target, fluid.getDefaultState())) {
                return false;
            }
        } else {
            instance.placeBlock(
                    new BlockHandler.Placement(
                            fluid.getDefaultState().block(), block, instance, target));
        }

        playSound(
                instance,
                target,
                fluid == MinestomFluids.LAVA
                        ? SoundEvent.ITEM_BUCKET_EMPTY_LAVA
                        : SoundEvent.ITEM_BUCKET_EMPTY);
        return true;
    }

    private static boolean canWaterlog(
            Instance instance, BlockVec position, Block block, Fluid fluid) {
        var handler = MinestomFluids.getWaterlog(block);
        return fluid == MinestomFluids.WATER
                && handler != null
                && !FluidState.isWaterlogged(block)
                && handler.canPlaceFluid(
                        instance, position, block, MinestomFluids.WATER.getDefaultState());
    }

    /** Turns one bucket of the used stack into {@code result}, unless the player is in creative. */
    private static void exchange(Player player, PlayerHand hand, ItemStack used, ItemStack result) {
        if (player.getGameMode() == GameMode.CREATIVE) return;

        if (used.amount() <= 1) {
            player.setItemInHand(hand, result);
            return;
        }

        player.setItemInHand(hand, used.consume(1));
        if (!player.getInventory().addItemStack(result)) {
            new ItemEntity(result)
                    .setInstance(
                            player.getInstance(),
                            player.getPosition().add(0, player.getEyeHeight(), 0));
        }
    }

    private static void playSound(Instance instance, BlockVec position, SoundEvent sound) {
        instance.playSound(
                Sound.sound(sound, Sound.Source.BLOCK, 1f, 1f),
                position.x() + 0.5,
                position.y() + 0.5,
                position.z() + 0.5);
    }
}
