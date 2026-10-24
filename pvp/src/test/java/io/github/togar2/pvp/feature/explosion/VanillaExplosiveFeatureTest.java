package io.github.togar2.pvp.feature.explosion;

import io.github.togar2.pvp.feature.CombatFeatures;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class VanillaExplosiveFeatureTest {
    @Test
    public void flintAndSteelPlacesFireOnSolidBlock(Env env) {
        var node = this.addExplosiveFeature();

        try {
            var instance = env.createFlatInstance();
            var player = this.createPlayer(env, instance);
            var flintAndSteel = ItemStack.of(Material.FLINT_AND_STEEL);
            player.setItemInMainHand(flintAndSteel);
            instance.setBlock(0, 40, 0, Block.STONE);

            this.useItemOnBlock(player, flintAndSteel, new BlockVec(0, 40, 0), BlockFace.TOP);

            assertEquals(Block.FIRE, instance.getBlock(0, 41, 0));
            assertEquals(1, player.getItemInMainHand().get(DataComponents.DAMAGE, 0));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void fireChargePlacesSoulFireOnSoulSoil(Env env) {
        var node = this.addExplosiveFeature();

        try {
            var instance = env.createFlatInstance();
            var player = this.createPlayer(env, instance);
            var fireCharge = ItemStack.of(Material.FIRE_CHARGE, 3);
            player.setItemInMainHand(fireCharge);
            instance.setBlock(0, 40, 0, Block.SOUL_SOIL);

            this.useItemOnBlock(player, fireCharge, new BlockVec(0, 40, 0), BlockFace.TOP);

            assertEquals(Block.SOUL_FIRE, instance.getBlock(0, 41, 0));
            assertEquals(2, player.getItemInMainHand().amount());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void flintAndSteelLightsUnlitCampfire(Env env) {
        var node = this.addExplosiveFeature();

        try {
            var instance = env.createFlatInstance();
            var player = this.createPlayer(env, instance);
            var flintAndSteel = ItemStack.of(Material.FLINT_AND_STEEL);
            player.setItemInMainHand(flintAndSteel);
            instance.setBlock(0, 40, 0, Block.CAMPFIRE
                    .withProperty("lit", "false")
                    .withProperty("waterlogged", "false"));

            this.useItemOnBlock(player, flintAndSteel, new BlockVec(0, 40, 0), BlockFace.TOP);

            assertEquals("true", instance.getBlock(0, 40, 0).getProperty("lit"));
            assertEquals(Block.AIR, instance.getBlock(0, 41, 0));
            assertEquals(1, player.getItemInMainHand().get(DataComponents.DAMAGE, 0));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private EventNode<?> addExplosiveFeature() {
        var node = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .add(CombatFeatures.VANILLA_EXPLOSION)
                .add(CombatFeatures.VANILLA_ITEM_DAMAGE)
                .add(CombatFeatures.VANILLA_EXPLOSIVE)
                .build()
                .createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);
        return node;
    }

    private Player createPlayer(Env env, Instance instance) {
        var player = env.createPlayer(instance, new Pos(0.0, 42.0, 0.0));
        player.setGameMode(GameMode.SURVIVAL);
        return player;
    }

    private void useItemOnBlock(Player player, ItemStack stack, BlockVec position, BlockFace blockFace) {
        var event = new PlayerUseItemOnBlockEvent(player, PlayerHand.MAIN, stack, position, Vec.ZERO, blockFace);
        EventDispatcher.call(event);
    }
}
