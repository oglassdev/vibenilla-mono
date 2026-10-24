package io.github.togar2.pvp.utils;

import io.github.togar2.pvp.feature.CombatFeature;

public final class CombatVersion implements CombatFeature {
	public static final CombatVersion MODERN = new CombatVersion(false);
	public static final CombatVersion LEGACY = new CombatVersion(true);

	private final boolean legacy;

	CombatVersion(boolean legacy) {
		this.legacy = legacy;
	}

	public boolean modern() {
		return !this.legacy;
	}

	public boolean legacy() {
		return this.legacy;
	}

	public static CombatVersion fromLegacy(boolean legacy) {
		return legacy ? LEGACY : MODERN;
	}

	@Override
	public String toString() {
		return "CombatVersion[legacy=" + this.legacy + "]";
	}

}
