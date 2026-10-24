package io.github.togar2.pvp.potion.effect;

import net.minestom.server.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class CombatPotionEffectsTest {
    @Test
    public void allMinestomPotionEffectsAreRegistered() {
        CombatPotionEffects.registerAll();

        for (var potionEffect : PotionEffect.values()) {
            assertNotNull(CombatPotionEffects.get(potionEffect), potionEffect.key().asString());
        }
    }
}
