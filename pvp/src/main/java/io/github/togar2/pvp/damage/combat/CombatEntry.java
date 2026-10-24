package io.github.togar2.pvp.damage.combat;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import io.github.togar2.pvp.utils.EntityUtil;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import org.jetbrains.annotations.Nullable;

public record CombatEntry(Damage damage, @Nullable String fallLocation, double fallDistance) {

	public String getMessageFallLocation() {
		return this.fallLocation == null ? "generic" : this.fallLocation;
	}

	public double getFallDistance() {
		DamageTypeInfo info = DamageTypeInfo.of(this.damage.getType());
		return info.outOfWorld() ? Double.MAX_VALUE : this.fallDistance;
	}

	public boolean isCombat() {
		return this.damage.getAttacker() instanceof LivingEntity;
	}

	public @Nullable Entity getAttacker() {
		return this.damage.getAttacker();
	}

	public @Nullable Component getAttackerName() {
		return this.getAttacker() == null ? null : EntityUtil.getName(this.getAttacker());
	}
}
