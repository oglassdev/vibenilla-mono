package io.github.togar2.pvp.potion.item;

import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionType;

import java.util.List;

public class CombatPotionType {
	private final PotionType potionType;
	private final List<Potion> effects;
	private List<Potion> legacyEffects;

	public CombatPotionType(PotionType potionType, Potion... effects) {
		this.potionType = potionType;
		this.effects = List.of(effects);
	}

	public CombatPotionType legacy(Potion... effects) {
        this.legacyEffects = List.of(effects);
		return this;
	}

	public PotionType getPotionType() {
		return this.potionType;
	}

	public List<Potion> getEffects(CombatVersion version) {
		if (this.legacyEffects == null) return this.effects;
		return version.legacy() ? this.legacyEffects : this.effects;
	}
}
