package io.github.togar2.pvp.feature.attack;

public class AttackValues {
	public record PreCritical(float damage, float magicalDamage, double cooldownProgress,
	                          boolean strong, boolean sprint, double knockback, int fireAspect) {
		public PreSweeping withCritical(boolean critical) {
			return new PreSweeping(
                    this.damage, this.magicalDamage, this.cooldownProgress,
                    this.strong, this.sprint, critical, this.knockback, this.fireAspect
			);
		}
	}

	public record PreSweeping(float damage, float magicalDamage, double cooldownProgress,
	                          boolean strong, boolean sprint, boolean critical,
	                          double knockback, int fireAspect) {
		public PreSounds withSweeping(boolean sweeping) {
			return new PreSounds(
                    this.damage, this.magicalDamage, this.cooldownProgress,
                    this.strong, this.sprint, this.critical, sweeping,
                    this.knockback, this.fireAspect
			);
		}
	}

	public record PreSounds(float damage, float magicalDamage, double cooldownProgress,
	                  boolean strong, boolean sprint, boolean critical, boolean sweeping,
	                  double knockback, int fireAspect) {}

	public record Final(float damage, float baseDamage, double cooldownProgress, boolean strong, boolean sprint,
	                    double knockback, boolean critical, boolean magical,
	                    int fireAspect, boolean sweeping, boolean sounds,
	                    boolean playSoundsOnFail) {}
}
