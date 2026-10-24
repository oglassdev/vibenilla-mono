package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.feature.CombatFeature;

/**
 * Combat feature which handles the spear charged stab attack mechanic.
 */
public interface SpearFeature extends CombatFeature {
	SpearFeature NO_OP = new SpearFeature() {};
}
