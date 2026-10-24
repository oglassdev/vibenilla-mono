package io.github.togar2.pvp.feature.cooldown;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

/**
 * Combat feature to manage a players item cooldown animation.
 */
public interface ItemCooldownFeature extends CombatFeature {
	ItemCooldownFeature NO_OP = new ItemCooldownFeature() {
		@Override
		public boolean hasCooldown(Player player, String cooldownGroup) {
			return false;
		}

		@Override
		public void setCooldown(Player player, String cooldownGroup, int ticks) {}
	};

	boolean hasCooldown(Player player, String cooldownGroup);

	default boolean hasCooldown(Player player, Material material) {
		return this.hasCooldown(player, material.key().asString());
	}

	default boolean hasCooldown(Player player, ItemStack itemStack) {
		return this.hasCooldown(player, this.getCooldownGroup(itemStack));
	}

	void setCooldown(Player player, String cooldownGroup, int ticks);

	default void setCooldown(Player player, Material material, int ticks) {
		this.setCooldown(player, material.key().asString(), ticks);
	}

	default void setCooldown(Player player, ItemStack itemStack, int ticks) {
		this.setCooldown(player, this.getCooldownGroup(itemStack), ticks);
	}

	default void setCooldown(Player player, ItemStack itemStack) {
		var useCooldown = itemStack.get(DataComponents.USE_COOLDOWN);
		var ticks = useCooldown == null ? 0 : (int) (useCooldown.seconds() * ServerFlag.SERVER_TICKS_PER_SECOND);
		this.setCooldown(player, itemStack, ticks);
	}

	default String getCooldownGroup(ItemStack itemStack) {
		var useCooldown = itemStack.get(DataComponents.USE_COOLDOWN);

		return useCooldown != null && useCooldown.cooldownGroup() != null
				? useCooldown.cooldownGroup()
				: itemStack.material().key().asString();
	}
}
