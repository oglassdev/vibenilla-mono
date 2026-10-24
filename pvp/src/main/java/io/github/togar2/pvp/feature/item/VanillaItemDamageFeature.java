package io.github.togar2.pvp.feature.item;

import io.github.togar2.pvp.events.EquipmentDamageEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Vanilla implementation of {@link ItemDamageFeature}
 */
public class VanillaItemDamageFeature implements ItemDamageFeature {
	public static final DefinedFeature<VanillaItemDamageFeature> DEFINED = new DefinedFeature<>(
			FeatureType.ITEM_DAMAGE, VanillaItemDamageFeature::new,
			FeatureType.ENCHANTMENT
	);

	private final FeatureConfiguration configuration;

	private EnchantmentFeature enchantmentFeature;

	public VanillaItemDamageFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
	}

	protected ItemStack damage(ItemStack stack, int amount) {
		if (amount == 0 || stack.has(DataComponents.UNBREAKABLE) || stack.get(DataComponents.MAX_DAMAGE, 0) <= 0)
			return stack;

		int preventAmount = 0;
		int newAmount = amount;

		for (int i = 0; i < newAmount; i++) {
			if (this.enchantmentFeature.shouldUnbreakingPreventDamage(stack)) {
				preventAmount++;
			}
		}

		newAmount -= preventAmount;
		if (newAmount <= 0) return stack;

		int finalNewAmount = newAmount;
		return stack.with(DataComponents.DAMAGE, (UnaryOperator<Integer>) d -> d + finalNewAmount);
	}

	protected <T extends LivingEntity> ItemStack damage(ItemStack stack, int amount,
	                                                    T entity, Consumer<T> breakCallback) {
		if (amount == 0 || stack.get(DataComponents.MAX_DAMAGE, 0) <= 0)
			return stack;

		ItemStack newStack = this.damage(stack, amount);
		if (newStack.get(DataComponents.DAMAGE, 0) >= stack.get(DataComponents.MAX_DAMAGE, 0)) {
			breakCallback.accept(entity);
			newStack = newStack.withAmount(i -> i - 1).with(DataComponents.DAMAGE, 0);
		}

		return newStack;
	}

	@Override
	public void damageEquipment(LivingEntity entity, EquipmentSlot slot, int amount) {
        if (entity instanceof Player player) {
            if (player.getGameMode() == GameMode.CREATIVE) {
                return;
            }
        }

		EquipmentDamageEvent event = new EquipmentDamageEvent(entity, slot, amount);

		EventDispatcher.callCancellable(event, () -> {
            Consumer<LivingEntity> breakCallback = _ -> triggerEquipmentBreak(entity, slot);
            var newEquipment = this.damage(entity.getEquipment(slot), amount, entity, breakCallback);
            entity.setEquipment(slot, newEquipment);
        });
	}

	@Override
	public void damageArmor(LivingEntity entity, DamageType damageType, float damage, EquipmentSlot... slots) {
		if (damage <= 0) return;

		damage /= 4;
		if (damage < 1) {
			damage = 1;
		}

		for (EquipmentSlot slot : slots) {
			ItemStack stack = entity.getEquipment(slot);

			if (this.canDamageEquipment(stack, damageType)) {
                this.damageEquipment(entity, slot, (int) damage);
			}
		}
	}

    private boolean canDamageEquipment(ItemStack stack, DamageType damageType) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || !equippable.damageOnHurt() || stack.get(DataComponents.MAX_DAMAGE, 0) <= 0) {
            return false;
        }

        var damageResistant = stack.get(DataComponents.DAMAGE_RESISTANT);
        if (damageResistant == null) {
            return true;
        }

        var damageResistantTypes = damageResistant.types();
        var damageTypeKey = MinecraftServer.getDamageTypeRegistry().getKey(damageType);

        return damageTypeKey == null || !damageResistantTypes.contains(damageTypeKey);
    }

	private static void triggerEquipmentBreak(LivingEntity entity, EquipmentSlot slot) {
		entity.triggerStatus(getEquipmentBreakStatus(slot));
	}

	private static byte getEquipmentBreakStatus(EquipmentSlot slot) {
		return switch (slot) {
			case OFF_HAND -> (byte) 48;
			case HELMET -> (byte) 49;
			case CHESTPLATE -> (byte) 50;
			case LEGGINGS -> (byte) 51;
			case BOOTS -> (byte) 52;
			default -> (byte) 47;
		};
	}
}
