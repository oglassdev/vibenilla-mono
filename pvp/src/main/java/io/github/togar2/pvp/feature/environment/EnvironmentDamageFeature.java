package io.github.togar2.pvp.feature.environment;

import io.github.togar2.pvp.feature.CombatFeature;

/**
 * Combat feature which handles environmental damage sources such as
 * fire, lava, void, drowning, and contact damage from blocks.
 */
public interface EnvironmentDamageFeature extends CombatFeature {
    EnvironmentDamageFeature NO_OP = new EnvironmentDamageFeature() {};
}
