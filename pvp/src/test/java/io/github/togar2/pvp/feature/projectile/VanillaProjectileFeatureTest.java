package io.github.togar2.pvp.feature.projectile;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnvTest
public final class VanillaProjectileFeatureTest {
    @Test
    public void creativeBowDefaultsToArrowWithoutInventoryAmmo(Env env) {
        var player = this.createPlayer(env, GameMode.CREATIVE);
        var feature = new VanillaProjectileItemFeature();
        var projectile = feature.getBowProjectile(player);

        assertNotNull(projectile);
        assertEquals(-1, projectile.slot());
        assertEquals(Material.ARROW, projectile.stack().material());
    }

    @Test
    public void creativeCrossbowLoadedProjectileSerializesToNetwork(Env env) {
        var player = this.createPlayer(env, GameMode.CREATIVE);
        var feature = this.createCrossbowFeature();
        var crossbow = feature.load(player, ItemStack.of(Material.CROSSBOW));
        var projectiles = crossbow.get(DataComponents.CHARGED_PROJECTILES);

        assertNotNull(projectiles);
        assertEquals(1, projectiles.size());
        assertEquals(Material.ARROW, projectiles.getFirst().material());
        assertFalse(projectiles.getFirst().has(DataComponents.INTANGIBLE_PROJECTILE));
        assertDoesNotThrow(() -> NetworkBuffer.makeArray(ItemStack.NETWORK_TYPE, crossbow));
    }

    private Player createPlayer(Env env, GameMode gameMode) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
        player.setGameMode(gameMode);
        return player;
    }

    private TestCrossbowFeature createCrossbowFeature() {
        var configuration = new FeatureConfiguration()
                .add(FeatureType.PROJECTILE_ITEM, new VanillaProjectileItemFeature());
        var feature = new TestCrossbowFeature(configuration);
        feature.initDependencies();
        return feature;
    }

    private static final class TestCrossbowFeature extends VanillaCrossbowFeature {
        private TestCrossbowFeature(FeatureConfiguration configuration) {
            super(configuration);
        }

        private ItemStack load(Player player, ItemStack stack) {
            return this.loadCrossbowProjectiles(player, stack);
        }
    }
}
