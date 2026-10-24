package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.server.play.EntityPositionAndRotationPacket;
import net.minestom.server.network.packet.server.play.EntityPositionSyncPacket;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class ThrownTridentTest {
    @Test
    public void loyaltyReturnStartsWithAnAbsolutePositionAndNoRelativeMoves(Env environment) {
        CombatEnchantments.registerAll();
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .build();

        var instance = environment.createFlatInstance();
        var connection = environment.createConnection();
        var player = connection.connect(instance, new Pos(8.0, 40.0, 2.0));
        player.setGameMode(GameMode.SURVIVAL);

        for (var y = 40; y <= 44; y++) {
            for (var x = 6; x <= 10; x++) {
                instance.setBlock(x, y, 20, Block.STONE);
            }
        }

        var tridentItem = ItemStack.of(Material.TRIDENT)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.LOYALTY, 3)));
        var trident = new ThrownTrident(player, tridentItem, featureSet.get(FeatureType.ENCHANTMENT));
        trident.setInstance(instance, new Pos(8.5, 42.0, 3.5)).join();
        trident.addViewer(player);
        trident.shootFromRotation(0.0F, 0.0F, 0.0F, 2.5, 0.0);

        for (var tick = 0; tick < 20 && !trident.isStuck(); tick++) {
            environment.tick();
        }

        assertTrue(trident.isStuck());

        var synchronizations = connection.trackIncoming(EntityPositionSyncPacket.class);
        var moves = connection.trackIncoming(EntityPositionAndRotationPacket.class);

        for (var tick = 0; tick < 20 && !trident.isNoClip(); tick++) {
            environment.tick();
        }

        assertTrue(trident.isNoClip());
        var returnStartZ = trident.getPosition().z();
        assertTrue(synchronizations.collect().stream().anyMatch(packet -> packet.entityId() == trident.getEntityId()));

        for (var tick = 0; tick < 5; tick++) {
            environment.tick();
        }

        assertTrue(trident.getPosition().z() < returnStartZ, "position " + trident.getPosition());
        assertFalse(moves.collect().stream().anyMatch(packet -> packet.entityId() == trident.getEntityId()));
        assertTrue(Math.abs(trident.getPosition().yaw()) < 10.0F, "yaw " + trident.getPosition().yaw());
    }
}
