package rocks.minestom.crafting.book;

import java.util.Set;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import net.minestom.server.recipe.display.RecipeDisplay;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.crafting.Ingredient;
import rocks.minestom.crafting.recipe.CraftingRecipe;

/**
 * A recipe as shown in the recipe book. {@code crafting} and {@code layout} are only set for
 * recipes that can be crafted in a crafting grid.
 */
public record BookRecipe(
        Key id,
        RecipeBookAddPacket.Entry entry,
        @Nullable CraftingRecipe crafting,
        @Nullable Layout layout,
        Set<Material> ingredients) {
    /**
     * Where ingredients go in a grid. Shaped layouts are {@code width * height} cells, shapeless
     * layouts fill the grid in order. Empty cells are null.
     */
    public record Layout(boolean shaped, int width, int height, Ingredient[] slots) {}

    public int displayId() {
        return this.entry.displayId();
    }

    public RecipeDisplay display() {
        return this.entry.display();
    }

    public RecipeBookAddPacket.Entry entry(boolean notification, boolean highlight) {
        return new RecipeBookAddPacket.Entry(
                this.entry.displayId(),
                this.entry.display(),
                this.entry.group(),
                this.entry.category(),
                this.entry.craftingRequirements(),
                notification,
                highlight);
    }
}
