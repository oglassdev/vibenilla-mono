package io.github.togar2.fluids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

@EnvTest
final class FluidBucketsTest {
    private static final BlockVec POSITION = new BlockVec(8, 39, 8);

    private static void click(
            net.minestom.server.entity.Player player, BlockVec position, BlockFace face) {
        var instance = player.getInstance();
        MinestomFluids.bucketEvents()
                .call(
                        new PlayerBlockInteractEvent(
                                player,
                                PlayerHand.MAIN,
                                instance,
                                instance.getBlock(position),
                                position,
                                new Vec(0.5, 1, 0.5),
                                face));
    }

    @Test
    void survivalWaterBucketBecomesEmptyBucket(Env env) {
        MinestomFluids.init(env.process().block());
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(8, 40, 8));
        player.setItemInHand(PlayerHand.MAIN, ItemStack.of(Material.WATER_BUCKET));

        click(player, POSITION, BlockFace.TOP);

        assertTrue(instance.getBlock(POSITION.add(0, 1, 0)).compare(Block.WATER));
        assertEquals(Material.BUCKET, player.getItemInHand(PlayerHand.MAIN).material());
    }

    @Test
    void creativeBucketIsNotConsumed(Env env) {
        MinestomFluids.init(env.process().block());
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(8, 40, 8));
        player.setGameMode(GameMode.CREATIVE);
        player.setItemInHand(PlayerHand.MAIN, ItemStack.of(Material.LAVA_BUCKET));

        click(player, POSITION, BlockFace.TOP);

        assertTrue(instance.getBlock(POSITION.add(0, 1, 0)).compare(Block.LAVA));
        assertEquals(Material.LAVA_BUCKET, player.getItemInHand(PlayerHand.MAIN).material());
    }

    @Test
    void emptyBucketStackFillsOneBucketFromWaterlogged(Env env) {
        MinestomFluids.init(env.process().block());
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(8, 40, 8));
        instance.setBlock(POSITION, FluidState.setWaterlogged(Block.OAK_SLAB, true));
        player.setItemInHand(PlayerHand.MAIN, ItemStack.of(Material.BUCKET, 2));

        click(player, POSITION, BlockFace.TOP);

        assertFalse(FluidState.isWaterlogged(instance.getBlock(POSITION)));
        assertEquals(1, player.getItemInHand(PlayerHand.MAIN).amount());
        assertEquals(Material.BUCKET, player.getItemInHand(PlayerHand.MAIN).material());
        assertTrue(
                java.util.Arrays.stream(player.getInventory().getItemStacks())
                        .anyMatch(stack -> stack.material() == Material.WATER_BUCKET));
    }

    @Test
    void placedBlockIsOnlyWaterloggedByWater(Env env) {
        MinestomFluids.init(env.process().block());
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(8, 40, 8));
        var node = MinestomFluids.bucketEvents();

        instance.setBlock(POSITION, Block.LAVA);
        var inLava = place(player, node);
        assertFalse(FluidState.isWaterlogged(inLava.getBlock()));

        instance.setBlock(POSITION, Block.WATER);
        var inWater = place(player, node);
        assertTrue(FluidState.isWaterlogged(inWater.getBlock()));
    }

    private static PlayerBlockPlaceEvent place(
            net.minestom.server.entity.Player player,
            net.minestom.server.event.EventNode<net.minestom.server.event.Event> node) {
        var event =
                new PlayerBlockPlaceEvent(
                        player,
                        player.getInstance(),
                        Block.OAK_SLAB,
                        BlockFace.TOP,
                        POSITION,
                        new Vec(0.5, 0.5, 0.5),
                        PlayerHand.MAIN);
        node.call(event);
        return event;
    }
}
