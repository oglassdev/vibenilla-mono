package io.github.togar2.pvp.feature.armor;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.DamageType;
import org.jetbrains.annotations.Nullable;

/**
 * Combat feature used for determining the resulting damage after armor usage.
 */
public interface ArmorFeature extends CombatFeature {
	ArmorFeature NO_OP = new ArmorFeature() {
		@Override
		public float getDamageWithProtection(LivingEntity entity, DamageType type, float amount) {
			return amount;
		}
	};

	float getDamageWithProtection(LivingEntity entity, DamageType type, float amount);

	default float getDamageWithProtection(LivingEntity entity, DamageType type, float amount, @Nullable LivingEntity attacker) {
		return this.getDamageWithProtection(entity, type, amount);
	}
}
