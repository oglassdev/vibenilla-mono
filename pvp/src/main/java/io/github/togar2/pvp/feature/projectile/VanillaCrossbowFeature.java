package io.github.togar2.pvp.feature.projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.item.crossbow.CrossbowChargingSounds;
import net.minestom.server.item.enchant.EffectComponent;
import org.jetbrains.annotations.Nullable;

import io.github.togar2.pvp.entity.projectile.AbstractArrow;
import io.github.togar2.pvp.entity.projectile.Arrow;
import io.github.togar2.pvp.entity.projectile.CustomEntityProjectile;
import io.github.togar2.pvp.entity.projectile.FireworkRocket;
import io.github.togar2.pvp.entity.projectile.SpectralArrow;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.effect.EffectFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;

/**
 * Vanilla implementation of {@link CrossbowFeature}
 */
public class VanillaCrossbowFeature implements CrossbowFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaCrossbowFeature> DEFINED = new DefinedFeature<>(
			FeatureType.CROSSBOW, VanillaCrossbowFeature::new,
			FeatureType.ITEM_DAMAGE, FeatureType.EFFECT, FeatureType.ENCHANTMENT, FeatureType.PROJECTILE_ITEM
	);

	private static final Tag<Boolean> START_SOUND_PLAYED = Tag.Transient("StartSoundPlayed");
	private static final Tag<Boolean> MID_LOAD_SOUND_PLAYED = Tag.Transient("MidLoadSoundPlayed");
	private static final Tag<Boolean> LOADED_DURING_USE = Tag.Transient("LoadedDuringUse");
	private static final Tag<Boolean> INTANGIBLE_PROJECTILE = Tag.Boolean("pvp_intangible_projectile");
	private static final CrossbowChargingSounds DEFAULT_CHARGING_SOUNDS = new CrossbowChargingSounds(
			SoundEvent.ITEM_CROSSBOW_LOADING_START,
			SoundEvent.ITEM_CROSSBOW_LOADING_MIDDLE,
			SoundEvent.ITEM_CROSSBOW_LOADING_END
	);

	private final FeatureConfiguration configuration;

	private ItemDamageFeature itemDamageFeature;
	private EffectFeature effectFeature;
	private EnchantmentFeature enchantmentFeature;
	private ProjectileItemFeature projectileItemFeature;

	public VanillaCrossbowFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
		this.effectFeature = this.configuration.get(FeatureType.EFFECT);
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
		this.projectileItemFeature = this.configuration.get(FeatureType.PROJECTILE_ITEM);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerUseItemEvent.class, event -> {
			ItemStack stack = event.getItemStack();
			if (stack.material() != Material.CROSSBOW) return;
			Player player = event.getPlayer();

			if (this.isCrossbowCharged(stack)) {
				// Make sure the animation event is not called, because this is not an animation
				event.setCancelled(true);

				if (Boolean.TRUE.equals(player.getTag(LOADED_DURING_USE))) {
					return;
				}

				stack = this.performCrossbowShooting(player, event.getHand(), stack, this.getCrossbowPower(stack), 1.0);
				player.setItemInHand(event.getHand(), this.setCrossbowProjectile(stack, null));
			} else {
				if (this.projectileItemFeature.getCrossbowProjectile(player) == null) {
					event.setCancelled(true);
				} else {
					event.setItemUseTime(this.getCrossbowUseDuration(stack));
					player.setTag(START_SOUND_PLAYED, false);
					player.setTag(MID_LOAD_SOUND_PLAYED, false);
				}
			}
		});

		node.addListener(PlayerTickEvent.class, event -> {
			Player player = event.getPlayer();

			// If not charging crossbow, return
			LivingEntityMeta meta = (LivingEntityMeta) player.getEntityMeta();
			if (!meta.isHandActive()) {
				player.removeTag(LOADED_DURING_USE);
				return;
			}

			if (player.getItemInHand(meta.getActiveHand()).material() != Material.CROSSBOW) {
				player.removeTag(LOADED_DURING_USE);
				return;
			}

			PlayerHand hand = player.getPlayerMeta().getActiveHand();
			ItemStack stack = player.getItemInHand(hand);

			var chargingSounds = this.getCrossbowChargingSounds(stack);

			long useTicks = player.getCurrentItemUseTime();
			double progress = useTicks / (double) this.getCrossbowChargeDuration(stack);

			Boolean startSoundPlayed = player.getTag(START_SOUND_PLAYED);
			Boolean midLoadSoundPlayed = player.getTag(MID_LOAD_SOUND_PLAYED);
			if (startSoundPlayed == null) startSoundPlayed = false;
			if (midLoadSoundPlayed == null) midLoadSoundPlayed = false;

			if (progress >= 0.2 && !startSoundPlayed) {
				if (chargingSounds.start() != null) {
					ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
							chargingSounds.start(), Sound.Source.PLAYER,
							0.5f, 1.0f
					), player);
				}

				player.setTag(START_SOUND_PLAYED, true);
				player.setItemInHand(hand, stack);
			}

			if (progress >= 0.5F && !midLoadSoundPlayed) {
				if (chargingSounds.mid() != null) {
					ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
							chargingSounds.mid(), Sound.Source.PLAYER,
							0.5f, 1.0f
					), player);
				}

				player.setTag(MID_LOAD_SOUND_PLAYED, true);
				player.setItemInHand(hand, stack);
			}

			if (progress >= 1.0F && !this.isCrossbowCharged(stack)) {
				stack = this.loadCrossbowProjectiles(player, stack);
				if (stack == null || stack.isAir()) return;

				this.playCrossbowLoadingEndSound(player, stack);
				player.setItemInHand(hand, stack);
				player.setTag(LOADED_DURING_USE, true);
			}
		});

		node.addListener(PlayerFinishItemUseEvent.class, event -> {
			var player = event.getPlayer();
			var stack = event.getItemStack();
			if (stack.material() != Material.CROSSBOW) return;

			this.loadCrossbowOnRelease(player, event.getHand(), stack, player.getCurrentItemUseTime());
		});

		node.addListener(PlayerCancelItemUseEvent.class, event -> {
			var stack = event.getItemStack();
			if (stack.material() != Material.CROSSBOW) return;

			this.loadCrossbowOnRelease(event.getPlayer(), event.getHand(), stack, event.getUseDuration());
		});
	}

	protected void loadCrossbowOnRelease(Player player, PlayerHand hand, ItemStack stack, long useTicks) {
		var chargeDuration = this.getCrossbowChargeDuration(stack);

		if (chargeDuration > 0) {
			var power = this.getCrossbowPowerForTime(useTicks, chargeDuration);
			if (!(power >= 1.0F) || this.isCrossbowCharged(stack)) return;
		}

		stack = this.loadCrossbowProjectiles(player, stack);
		if (stack == null || stack.isAir()) return;

		this.playCrossbowLoadingEndSound(player, stack);

		player.setItemInHand(hand, stack);
	}

	protected void playCrossbowLoadingEndSound(Player player, ItemStack stack) {
		var chargingSounds = this.getCrossbowChargingSounds(stack);
		if (chargingSounds.end() == null) return;

		ThreadLocalRandom random = ThreadLocalRandom.current();
		ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
				chargingSounds.end(), Sound.Source.PLAYER,
				1.0f, 1.0f / (random.nextFloat() * 0.5f + 1.0f) + 0.2f
		), player);
	}

	protected AbstractArrow createArrow(ItemStack stack, @Nullable Entity shooter) {
		if (stack.material() == Material.SPECTRAL_ARROW) {
			return new SpectralArrow(shooter, this.enchantmentFeature);
		} else {
			Arrow arrow = new Arrow(shooter, this.effectFeature, this.enchantmentFeature);
			arrow.setItemStack(stack);
			return arrow;
		}
	}

	protected double getCrossbowPower(ItemStack stack) {
		return this.crossbowContainsProjectile(stack, Material.FIREWORK_ROCKET) ? 1.6 : 3.15;
	}

	protected double getCrossbowPowerForTime(long ticks, int chargeDuration) {
		double power = ticks / (double) chargeDuration;
		if (power > 1) {
			power = 1;
		}

		return power;
	}

	protected boolean isCrossbowCharged(ItemStack stack) {
		return stack.has(DataComponents.CHARGED_PROJECTILES) &&
				!Objects.requireNonNull(stack.get(DataComponents.CHARGED_PROJECTILES)).isEmpty();
	}

	protected ItemStack setCrossbowProjectile(ItemStack stack, @Nullable ItemStack projectile) {
		return stack.with(DataComponents.CHARGED_PROJECTILES, projectile == null ? List.of() : List.of(projectile));
	}

	protected ItemStack setCrossbowProjectiles(ItemStack stack, List<ItemStack> projectiles) {
		return stack.with(DataComponents.CHARGED_PROJECTILES, projectiles);
	}

	protected boolean crossbowContainsProjectile(ItemStack stack, Material projectile) {
		List<ItemStack> projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
		if (projectiles == null) return false;

		for (ItemStack itemStack : projectiles) {
			if (itemStack.material() == projectile) return true;
		}

		return false;
	}

	protected int getCrossbowUseDuration(ItemStack stack) {
		return 72000;
	}

	protected int getCrossbowChargeDuration(ItemStack stack) {
		var duration = this.enchantmentFeature.modifyValue(stack, EffectComponent.CROSSBOW_CHARGE_TIME, 1.25F);
		return (int) Math.floor(Math.max(0.0F, duration) * ServerFlag.SERVER_TICKS_PER_SECOND);
	}

	protected CrossbowChargingSounds getCrossbowChargingSounds(ItemStack stack) {
		return this.enchantmentFeature.pickHighestLevel(
				stack, EffectComponent.CROSSBOW_CHARGING_SOUNDS, DEFAULT_CHARGING_SOUNDS
		);
	}

	protected ItemStack loadCrossbowProjectiles(Player player, ItemStack stack) {
		var projectileCount = Math.max(0, (int) this.enchantmentFeature.modifyConditionalValue(
				stack, EffectComponent.PROJECTILE_COUNT, 1.0F
		));
		if (projectileCount == 0) return ItemStack.AIR;

		ItemStack projectileItem;
		int projectileSlot;

		ProjectileItemFeature.ProjectileItem projectile = this.projectileItemFeature.getCrossbowProjectile(player);
		if (projectile == null && player.getGameMode() == GameMode.CREATIVE) {
			projectileItem = Arrow.DEFAULT_ARROW;
			projectileSlot = -1;
		} else if (projectile != null) {
			projectileItem = projectile.stack();
			projectileSlot = projectile.slot();
		} else {
			// Should not happen
			return ItemStack.AIR;
		}

		var ammoUse = projectileItem.material() == Material.ARROW
				? (int) this.enchantmentFeature.modifyConditionalValue(
						stack, EffectComponent.AMMO_USE, 1.0F, true)
				: 1;
		if (player.getGameMode() != GameMode.CREATIVE && ammoUse > projectileItem.amount()) return ItemStack.AIR;

		var loadedProjectiles = new ArrayList<ItemStack>(projectileCount);

		for (var projectileIndex = 0; projectileIndex < projectileCount; projectileIndex++) {
			var loadedProjectile = projectileItem.withAmount(1);

			if (projectileIndex > 0 || player.getGameMode() == GameMode.CREATIVE || ammoUse <= 0) {
				loadedProjectile = this.markIntangibleProjectile(loadedProjectile);
			}

			loadedProjectiles.add(loadedProjectile);
		}

		stack = this.setCrossbowProjectiles(stack, loadedProjectiles);

		if (player.getGameMode() != GameMode.CREATIVE && projectileSlot >= 0) {
			player.getInventory().setItemStack(projectileSlot, projectileItem.withAmount(projectileItem.amount() - ammoUse));
		}

		return stack;
	}

	protected ItemStack performCrossbowShooting(Player player, PlayerHand hand, ItemStack stack,
	                                            double power, double spread) {
		List<ItemStack> projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
		if (projectiles == null || projectiles.isEmpty()) return ItemStack.AIR;

		var maxAngle = this.enchantmentFeature.modifyConditionalValue(stack, EffectComponent.PROJECTILE_SPREAD, 0.0F);
		maxAngle = Math.max(0.0F, maxAngle);
		var angleStep = projectiles.size() == 1 ? 0.0F : 2.0F * maxAngle / (projectiles.size() - 1);
		var angleOffset = (projectiles.size() - 1) % 2 * angleStep / 2.0F;
		var direction = 1.0F;
		var random = ThreadLocalRandom.current();

		for (var projectileIndex = 0; projectileIndex < projectiles.size(); projectileIndex++) {
			var projectile = projectiles.get(projectileIndex);

			if (projectile.isAir()) continue;

			var angle = angleOffset + direction * ((projectileIndex + 1) / 2) * angleStep;
			direction = -direction;
			var soundPitch = projectileIndex == 0 ? 1.0F : this.getRandomShotPitch((projectileIndex & 1) == 1, random);
			this.shootCrossbowProjectile(player, hand, stack, projectile, soundPitch, power, spread, angle);
		}

		return this.setCrossbowProjectile(stack, ItemStack.AIR);
	}

	protected void shootCrossbowProjectile(Player player, PlayerHand hand, ItemStack crossbowStack,
	                                       ItemStack projectile, float soundPitch,
	                                       double power, double spread, float yaw) {
		var intangibleProjectile = this.isIntangibleProjectile(projectile);
		projectile = this.removeIntangibleProjectile(projectile);
		var firework = projectile.material() == Material.FIREWORK_ROCKET;

		if (firework) {
			var projectileEntity = new FireworkRocket(player, projectile, true);
			var position = player.getPosition().add(0, player.getEyeHeight() - 0.15, 0);
			this.shootProjectileEntity(projectileEntity, player, position, yaw, power, spread);
		} else {
			var arrow = this.getCrossbowArrow(player, crossbowStack, projectile);
			if (intangibleProjectile) {
				arrow.setPickupMode(AbstractArrow.PickupMode.CREATIVE_ONLY);
			}

			var position = player.getPosition().add(0, player.getEyeHeight() - 0.1, 0);
			this.shootProjectileEntity(arrow, player, position, yaw, power, spread);
		}

        this.itemDamageFeature.damageEquipment(player, hand == PlayerHand.MAIN ?
				EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND, firework ? 3 : 1);

		ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
				SoundEvent.ITEM_CROSSBOW_SHOOT, Sound.Source.PLAYER,
				1.0f, soundPitch
		), player);
	}

	private void shootProjectileEntity(CustomEntityProjectile projectileEntity, Player player, Pos position,
	                                   float yaw, double power, double spread) {
		var shotVector = this.getProjectileShotVector(player.getPosition(), yaw);
		projectileEntity.shoot(shotVector.x(), shotVector.y(), shotVector.z(), power, spread);
		var spawnFuture = projectileEntity.setInstance(Objects.requireNonNull(player.getInstance()), position.withView(projectileEntity.getPosition()));

		if (spawnFuture != null) {
			spawnFuture.thenRun(() -> projectileEntity.setVelocity(projectileEntity.getVelocity()));
		}
	}

	private Vec getProjectileShotVector(Pos position, float angle) {
		var viewVector = position.direction();
		var upVector = this.getUpVector(position);

		return this.rotateAroundAxis(viewVector, upVector, angle);
	}

	private Vec getUpVector(Pos position) {
		var pitch = Math.toRadians(position.pitch());
		var yaw = Math.toRadians(position.yaw());

		return new Vec(
				-Math.sin(yaw) * Math.sin(pitch),
				Math.cos(pitch),
				Math.cos(yaw) * Math.sin(pitch)
		);
	}

	private ItemStack markIntangibleProjectile(ItemStack projectile) {
		return projectile.withTag(INTANGIBLE_PROJECTILE, true);
	}

	private boolean isIntangibleProjectile(ItemStack projectile) {
		return Boolean.TRUE.equals(projectile.getTag(INTANGIBLE_PROJECTILE));
	}

	private ItemStack removeIntangibleProjectile(ItemStack projectile) {
		if (!this.isIntangibleProjectile(projectile)) return projectile;

		return projectile.withTag(INTANGIBLE_PROJECTILE, null);
	}

	private Vec rotateAroundAxis(Vec vector, Vec axis, float angle) {
		var radians = Math.toRadians(angle);
		var cos = Math.cos(radians);
		var sin = Math.sin(radians);
		var normalizedAxis = axis.normalize();

		return vector.mul(cos)
				.add(normalizedAxis.cross(vector).mul(sin))
				.add(normalizedAxis.mul(normalizedAxis.dot(vector) * (1.0 - cos)));
	}

	protected AbstractArrow getCrossbowArrow(Player player, ItemStack crossbowStack, ItemStack projectile) {
		AbstractArrow arrow = this.createArrow(projectile.withAmount(1), player);
		arrow.setWeaponItem(crossbowStack);
		arrow.setCritical(true); // Player shooter is always critical
		arrow.setSound(SoundEvent.ITEM_CROSSBOW_HIT);

		var piercing = Math.max(0, (int) this.enchantmentFeature.modifyConditionalValue(
				crossbowStack, EffectComponent.PROJECTILE_PIERCING, 0.0F
		));
		if (piercing > 0) {
			arrow.setPiercingLevel((byte) piercing);
		}

		return arrow;
	}

	protected float getRandomShotPitch(boolean high, ThreadLocalRandom random) {
		float base = high ? 0.63F : 0.43F;
		return 1.0F / (random.nextFloat() * 0.5F + 1.8F) + base;
	}
}
