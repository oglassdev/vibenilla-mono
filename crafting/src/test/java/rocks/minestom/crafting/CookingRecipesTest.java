package rocks.minestom.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;
import rocks.minestom.crafting.CookingRecipes.Type;

final class CookingRecipesTest {
    private static final CookingRecipes RECIPES = new CookingRecipes(Path.of("data", "minecraft"));

    @Test
    void indexesRecipesByTypeAndIngredient() {
        var smelting = RECIPES.find(Type.SMELTING, ItemStack.of(Material.IRON_ORE));
        assertNotNull(smelting);
        assertEquals(Material.IRON_INGOT, smelting.result().material());
        assertEquals(0.7F, smelting.experience());
        assertEquals(200, smelting.cookingTime());
        assertEquals(smelting, RECIPES.get(smelting.id()));

        assertNotNull(RECIPES.find(Type.BLASTING, ItemStack.of(Material.IRON_ORE)));
        assertNotNull(RECIPES.find(Type.SMOKING, ItemStack.of(Material.BEEF)));
        assertNull(RECIPES.find(Type.SMOKING, ItemStack.of(Material.IRON_ORE)));
    }

    @Test
    void resolvesTagIngredients() {
        var recipe = RECIPES.find(Type.SMELTING, ItemStack.of(Material.OAK_LOG));
        assertNotNull(recipe);
        assertEquals(Material.CHARCOAL, recipe.result().material());
    }
}
