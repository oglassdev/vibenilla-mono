package rocks.minestom.crafting;

import com.google.gson.JsonElement;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple ingredient implementation that only matches materials. */
public final class Ingredient {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ingredient.class);

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

    /** Parses a recipe ingredient given as an item id, a {@code #tag}, an object, or a list. */
    public static @Nullable Ingredient parse(
            @Nullable JsonElement element, ItemTagManager tagManager) {
        if (element == null) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return parse(element.getAsString(), tagManager);
        } else if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has("item")) {
                return parse(object.get("item").getAsString(), tagManager);
            } else if (object.has("tag")) {
                return parse("#" + object.get("tag").getAsString(), tagManager);
            }
        } else if (element.isJsonArray()) {
            var combinedMaterials = new LinkedHashSet<Material>();

            for (var child : element.getAsJsonArray()) {
                var nested = parse(child, tagManager);

                if (nested != null) {
                    combinedMaterials.addAll(nested.getMaterials());
                }
            }

            if (!combinedMaterials.isEmpty()) {
                return ofTag(combinedMaterials);
            }
        }

        return null;
    }

    private static @Nullable Ingredient parse(String value, ItemTagManager tagManager) {
        if (value.isEmpty()) {
            return null;
        }

        if (value.charAt(0) == '#') {
            return ofTag(tagManager.materials(Key.key(value.substring(1))));
        }

        var material = Material.fromKey(value);

        if (material == null) {
            LOGGER.warn("Unknown ingredient {}, skipping recipe entry.", value);
            return null;
        }

        return ofMaterial(material);
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
