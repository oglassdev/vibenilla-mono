package rocks.minestom.crafting;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Simple ingredient implementation that only matches materials.
 */
public final class Ingredient {
    private final Set<Material> materials;

    private Ingredient(Set<Material> materials) {
        this.materials = Collections.unmodifiableSet(materials);
    }

    public static @Nullable Ingredient ofMaterial(@Nullable Material candidateMaterial) {
        if (candidateMaterial == null || candidateMaterial == Material.AIR) {
            return null;
        }

        return new Ingredient(Set.of(candidateMaterial));
    }

    public static @Nullable Ingredient ofTag(Set<Material> tagMaterials) {
        if (tagMaterials.isEmpty()) {
            return null;
        }

        return new Ingredient(new LinkedHashSet<>(tagMaterials));
    }

    public Set<Material> getMaterials() {
        return this.materials;
    }

    public boolean matches(ItemStack input) {
        if (input.isAir()) {
            return false;
        }

        return this.materials.contains(input.material());
    }

    public boolean isEmpty() {
        return this.materials.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Ingredient ingredient)) {
            return false;
        }
        return this.materials.equals(ingredient.materials);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.materials);
    }
}
