package rocks.minestom.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.crafting.recipe.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class CraftingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingManager.class);

    private final ItemTagManager tagManager;
    private final List<CraftingRecipe> recipes = new ArrayList<>();
    private final Map<AbstractInventory, CraftingWorkspace> workspaces = new IdentityHashMap<>();

    public CraftingManager(Path dataDirectory) {
        var tagDirectory = dataDirectory.resolve("tags").resolve("item");
        this.tagManager = new ItemTagManager(tagDirectory);
        this.loadRecipes(dataDirectory.resolve("recipe"));
        this.registerSpecialRecipes();
        LOGGER.info("Registered {} crafting recipes.", this.recipes.size());
    }

    private void registerSpecialRecipes() {
        this.recipes.add(new DecoratedPotCraftingRecipe(this.tagManager));
        this.recipes.add(new ArmorDyeCraftingRecipe(this.tagManager));
        this.recipes.add(new BannerDuplicateCraftingRecipe());
        this.recipes.add(new BookCloningCraftingRecipe(this.tagManager));
        this.recipes.add(new FireworkRocketCraftingRecipe());
        this.recipes.add(new FireworkStarCraftingRecipe());
        this.recipes.add(new FireworkStarFadeCraftingRecipe());
        this.recipes.add(new MapCloningCraftingRecipe());
        this.recipes.add(new MapExtendingCraftingRecipe());
        this.recipes.add(new RepairItemCraftingRecipe());
        this.recipes.add(new ShieldDecorationCraftingRecipe());
        this.recipes.add(new TippedArrowCraftingRecipe());
    }

    private void loadRecipes(Path recipeDirectory) {
        if (!Files.exists(recipeDirectory)) {
            LOGGER.warn("Recipe directory {} is missing.", recipeDirectory);
            return;
        }

        try {
            Files.walk(recipeDirectory)
                    .filter(Files::isRegularFile)
                    .filter(filePath -> filePath.toString().endsWith(".json"))
                    .sorted()
                    .forEach(this::readRecipe);
        } catch (IOException exception) {
            LOGGER.error("Failed to enumerate recipes in {}", recipeDirectory, exception);
        }
    }

    private void readRecipe(Path recipePath) {
        try (var reader = Files.newBufferedReader(recipePath)) {
            var recipeObject = JsonParser.parseReader(reader).getAsJsonObject();
            var recipeType = recipeObject.get("type").getAsString();

            switch (recipeType) {
                case "minecraft:crafting_shaped" -> this.loadShaped(recipeObject);
                case "minecraft:crafting_shapeless" -> this.loadShapeless(recipeObject);
                case "minecraft:crafting_transmute" -> this.loadTransmute(recipeObject);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to parse recipe {}", recipePath, exception);
        }
    }

    private void loadShaped(JsonObject recipeObject) {
        var keyObject = recipeObject.getAsJsonObject("key");
        var patternArray = recipeObject.getAsJsonArray("pattern");
        var resultElement = recipeObject.get("result");

        if (keyObject == null || patternArray == null || resultElement == null) {
            return;
        }

        var symbolIngredients = new LinkedHashMap<Character, Ingredient>();
        for (var entry : keyObject.entrySet()) {
            if (entry.getKey().length() != 1) {
                continue;
            }

            var ingredient = this.parseIngredient(entry.getValue());

            if (ingredient != null) {
                symbolIngredients.put(entry.getKey().charAt(0), ingredient);
            }
        }

        var patternRows = new ArrayList<String>();
        var patternWidth = -1;

        for (var element : patternArray) {
            var patternRow = element.getAsString();

            if (patternWidth == -1) {
                patternWidth = patternRow.length();
            } else if (patternWidth != patternRow.length()) {
                return;
            }

            patternRows.add(patternRow);
        }

        var patternIngredients = new Ingredient[patternWidth * patternRows.size()];

        for (var rowIndex = 0; rowIndex < patternRows.size(); rowIndex++) {
            var patternRow = patternRows.get(rowIndex);

            for (var columnIndex = 0; columnIndex < patternRow.length(); columnIndex++) {
                var patternSymbol = patternRow.charAt(columnIndex);
                var ingredient = symbolIngredients.get(patternSymbol);

                if (patternSymbol != ' ' && ingredient == null) {
                    return;
                }

                patternIngredients[columnIndex + rowIndex * patternWidth] = ingredient;
            }
        }

        var resultStack = this.readResult(resultElement, "shaped");

        if (resultStack.isAir()) {
            return;
        }

        this.recipes.add(new ShapedCraftingRecipe(patternWidth, patternRows.size(), patternIngredients, resultStack));
    }

    private void loadShapeless(JsonObject recipeObject) {
        var ingredientArray = recipeObject.getAsJsonArray("ingredients");
        var resultElement = recipeObject.get("result");

        if (ingredientArray == null || resultElement == null) {
            return;
        }

        var ingredients = new ArrayList<Ingredient>();

        for (var element : ingredientArray) {
            var ingredient = this.parseIngredient(element);

            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }

        if (ingredients.size() != ingredientArray.size()) {
            return;
        }

        var resultStack = this.readResult(resultElement, "shapeless");

        if (resultStack.isAir()) {
            return;
        }

        this.recipes.add(new ShapelessCraftingRecipe(ingredients, resultStack));
    }

    private void loadTransmute(JsonObject recipeObject) {
        var inputElement = recipeObject.get("input");
        var materialElement = recipeObject.get("material");
        var resultElement = recipeObject.get("result");

        if (inputElement == null || materialElement == null || resultElement == null) {
            return;
        }

        var baseIngredient = this.parseIngredient(inputElement);
        var catalystIngredient = this.parseIngredient(materialElement);

        if (baseIngredient == null || catalystIngredient == null) {
            return;
        }

        var resultStack = this.readResult(resultElement, "transmute");

        if (resultStack.isAir()) {
            return;
        }

        this.recipes.add(new TransmuteCraftingRecipe(
                baseIngredient,
                catalystIngredient,
                resultStack.material(),
                resultStack.amount(),
                resultStack.componentPatch()));
    }

    private Ingredient parseIngredient(JsonElement ingredientElement) {
        if (ingredientElement == null) {
            return null;
        }
        if (ingredientElement.isJsonPrimitive()) {
            return this.parseIngredientString(ingredientElement.getAsString());
        } else if (ingredientElement.isJsonObject()) {
            var ingredientObject = ingredientElement.getAsJsonObject();
            if (ingredientObject.has("item")) {
                return this.parseIngredientString(ingredientObject.get("item").getAsString());
            } else if (ingredientObject.has("tag")) {
                return this.parseIngredientString("#" + ingredientObject.get("tag").getAsString());
            }
        } else if (ingredientElement.isJsonArray()) {
            var combinedMaterials = new LinkedHashSet<Material>();

            for (var child : ingredientElement.getAsJsonArray()) {
                var nestedIngredient = this.parseIngredient(child);

                if (nestedIngredient != null) {
                    combinedMaterials.addAll(nestedIngredient.getMaterials());
                }
            }

            if (!combinedMaterials.isEmpty()) {
                return Ingredient.ofTag(combinedMaterials);
            }
        }

        return null;
    }

    private Ingredient parseIngredientString(String ingredientValue) {
        if (ingredientValue == null || ingredientValue.isEmpty()) {
            return null;
        }

        if (ingredientValue.charAt(0) == '#') {
            var tagKey = Key.key(ingredientValue.substring(1));
            return Ingredient.ofTag(this.tagManager.materials(tagKey));
        }

        var material = Material.fromKey(ingredientValue);

        if (material == null) {
            LOGGER.warn("Unknown ingredient {}, skipping recipe entry.", ingredientValue);
            return null;
        }

        return Ingredient.ofMaterial(material);
    }

    private ItemStack readResult(JsonElement resultElement, String recipeType) {
        try {
            JsonObject resultObject;

            if (resultElement.isJsonPrimitive()) {
                resultObject = new JsonObject();
                resultObject.addProperty("id", resultElement.getAsString());
            } else {
                resultObject = resultElement.getAsJsonObject();
            }

            return ItemStack.CODEC.decode(Transcoder.JSON, resultObject).orElse(ItemStack.AIR);
        } catch (Exception exception) {
            LOGGER.warn("Failed to decode {} recipe result {}", recipeType, resultElement, exception);
            return ItemStack.AIR;
        }
    }

    public CraftingRecipeMatch match(CraftingGrid grid) {
        for (var recipe : this.recipes) {
            var match = recipe.match(grid);

            if (match != null) {
                return match;
            }
        }

        return null;
    }

    public void registerPlayer(Player player) {
        var playerInventory = player.getInventory();
        var workspace = new CraftingWorkspace(this, player, playerInventory, 36, new int[] { 37, 38, 39, 40 }, 2, 2);
        this.workspaces.put(playerInventory, workspace);
        workspace.refresh();
    }

    public void unregisterPlayer(Player player) {
        this.workspaces.remove(player.getInventory());
    }

    public Inventory openCraftingTable(Player player) {
        var craftingInventory = new Inventory(InventoryType.CRAFTING, Component.translatable("container.crafting"));
        var workspace = new CraftingWorkspace(this, player, craftingInventory, 0, new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 }, 3, 3);
        this.workspaces.put(craftingInventory, workspace);
        workspace.refresh();
        return craftingInventory;
    }

    public void unregisterInventory(AbstractInventory inventory) {
        this.workspaces.remove(inventory);
    }

    public @Nullable CraftingWorkspace workspace(AbstractInventory inventory) {
        return this.workspaces.get(inventory);
    }
}
