package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.state.PlayerStateFeature;
import io.github.togar2.pvp.utils.CombatVersion;
import io.github.togar2.pvp.utils.FluidUtil;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.potion.PotionEffect;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link CriticalFeature}
 */
public class VanillaCriticalFeature implements CriticalFeature {
	public static final DefinedFeature<VanillaCriticalFeature> DEFINED = new DefinedFeature<>(
			FeatureType.CRITICAL, VanillaCriticalFeature::new,
			FeatureType.PLAYER_STATE, FeatureType.FALL, FeatureType.VERSION
	);

	private final FeatureConfiguration configuration;

	private PlayerStateFeature playerStateFeature;
	private FallFeature fallFeature;
	private CombatVersion version;

	public VanillaCriticalFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.playerStateFeature = this.configuration.get(FeatureType.PLAYER_STATE);
		this.fallFeature = this.configuration.get(FeatureType.FALL);
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	@Override
	public boolean shouldCrit(LivingEntity attacker, AttackValues.PreCritical values) {
		boolean critical = values.strong() && !this.playerStateFeature.isClimbing(attacker)
				&& this.fallFeature.getFallDistance(attacker) > 0.0 && !attacker.isOnGround()
				&& !FluidUtil.isTouchingWater(attacker)
				&& !attacker.hasEffect(PotionEffect.BLINDNESS)
				&& attacker.getVehicle() == null;
		if (this.version.legacy()) return critical;

		// Not sprinting required for critical in 1.9+
		return critical && !attacker.isSprinting();
	}

	@Override
	public float applyToDamage(float damage) {
		if (this.version.legacy()) {
			return damage + ThreadLocalRandom.current().nextInt((int) (damage / 2 + 2));
		} else {
			return damage * 1.5f;
		}
	}
}
