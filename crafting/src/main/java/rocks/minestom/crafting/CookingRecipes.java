package rocks.minestom.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The vanilla smelting, blasting and smoking recipes, indexed by ingredient material. */
public final class CookingRecipes {
    private static final Logger LOGGER = LoggerFactory.getLogger(CookingRecipes.class);

    public enum Type {
        SMELTING("minecraft:smelting"),
        BLASTING("minecraft:blasting"),
        SMOKING("minecraft:smoking");

        private final String id;

        Type(String id) {
            this.id = id;
        }
    }

    /**
     * @param cookingTime ticks of progress needed at normal speed
     */
    public record Recipe(
            Key id,
            Type type,
            Ingredient ingredient,
            ItemStack result,
            float experience,
            int cookingTime) {}

    private final Map<Key, Recipe> byId = new LinkedHashMap<>();
    private final Map<Type, Map<Material, Recipe>> byIngredient = new EnumMap<>(Type.class);

    /**
     * Loads the recipes under {@code dataDirectory/recipe}, resolving tags from {@code tags/item}.
     */
    public CookingRecipes(Path dataDirectory) {
        var tagManager = new ItemTagManager(dataDirectory.resolve("tags").resolve("item"));
        var recipeDirectory = dataDirectory.resolve("recipe");

        if (!Files.exists(recipeDirectory)) {
            LOGGER.warn("Recipe directory {} is missing.", recipeDirectory);
            return;
        }

        try (var paths = Files.walk(recipeDirectory)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(
                            path -> {
                                var recipe = read(recipeDirectory, path, tagManager);

                                if (recipe != null) {
                                    add(recipe);
                                }
                            });
        } catch (IOException exception) {
            LOGGER.error("Failed to enumerate recipes in {}", recipeDirectory, exception);
        }
        LOGGER.info("Registered {} cooking recipes.", byId.size());
    }

    public CookingRecipes(Collection<Recipe> recipes) {
        recipes.forEach(this::add);
    }

    public @Nullable Recipe find(Type type, ItemStack input) {
        var recipes = byIngredient.get(type);
        return input.isAir() || recipes == null ? null : recipes.get(input.material());
    }

    public @Nullable Recipe get(Key id) {
        return byId.get(id);
    }

    private void add(Recipe recipe) {
        byId.put(recipe.id(), recipe);
        var index = byIngredient.computeIfAbsent(recipe.type(), type -> new HashMap<>());

        for (var material : recipe.ingredient().getMaterials()) {
            index.putIfAbsent(material, recipe);
        }
    }

    private static @Nullable Recipe read(Path directory, Path path, ItemTagManager tagManager) {
        try (var reader = Files.newBufferedReader(path)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var type = type(json.get("type").getAsString());

            if (type == null) {
                return null;
            }

            var ingredient = Ingredient.parse(json.get("ingredient"), tagManager);
            var result = result(json.get("result"));

            if (ingredient == null || result.isAir()) {
                return null;
            }

            var name = directory.relativize(path).toString().replace('\\', '/');
            return new Recipe(
                    Key.key("minecraft", name.substring(0, name.length() - ".json".length())),
                    type,
                    ingredient,
                    result,
                    json.has("experience") ? json.get("experience").getAsFloat() : 0,
                    json.has("cookingtime") ? json.get("cookingtime").getAsInt() : 200);
        } catch (Exception exception) {
            LOGGER.error("Failed to parse recipe {}", path, exception);
            return null;
        }
    }

    private static @Nullable Type type(String id) {
        for (var type : Type.values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    private static ItemStack result(@Nullable JsonElement element) {
        if (element == null) {
            return ItemStack.AIR;
        }

        var object = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();

        if (element.isJsonPrimitive()) {
            object.addProperty("id", element.getAsString());
        }
        return ItemStack.CODEC.decode(Transcoder.JSON, object).orElse(ItemStack.AIR);
    }
}
