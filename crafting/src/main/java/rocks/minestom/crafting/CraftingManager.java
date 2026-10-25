package rocks.minestom.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientPlaceRecipePacket;
import net.minestom.server.network.packet.client.play.ClientRecipeBookSeenRecipePacket;
import net.minestom.server.network.packet.client.play.ClientSetRecipeBookStatePacket;
import net.minestom.server.network.packet.server.play.PlaceGhostRecipePacket;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.crafting.book.RecipeBook;
import rocks.minestom.crafting.book.RecipeCatalog;
import rocks.minestom.crafting.listener.*;
import rocks.minestom.crafting.recipe.*;

public final class CraftingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftingManager.class);

    private final ItemTagManager tagManager;
    private final List<CraftingRecipe> recipes = new ArrayList<>();
    private final Map<AbstractInventory, CraftingWorkspace> workspaces = new IdentityHashMap<>();
    private final RecipeCatalog catalog = new RecipeCatalog();
    private final PlayerRecipeBooks books = new PlayerRecipeBooks(this.catalog);

    public CraftingManager(Path dataDirectory) {
        var tagDirectory = dataDirectory.resolve("tags").resolve("item");
        this.tagManager = new ItemTagManager(tagDirectory);
        this.loadRecipes(dataDirectory.resolve("recipe"));
        this.registerSpecialRecipes();
        this.catalog.indexTriggers(
                dataDirectory.resolve("advancement").resolve("recipes"), this.tagManager);
        LOGGER.info("Registered {} crafting recipes.", this.recipes.size());
    }

    private void registerSpecialRecipes() {
        this.recipes.add(new DecoratedPotCraftingRecipe(this.tagManager));
        this.recipes.add(new BannerDuplicateCraftingRecipe());
        this.recipes.add(new BookCloningCraftingRecipe(this.tagManager));
        this.recipes.add(new FireworkRocketCraftingRecipe());
        this.recipes.add(new FireworkStarCraftingRecipe());
        this.recipes.add(new FireworkStarFadeCraftingRecipe());
        this.recipes.add(new MapCloningCraftingRecipe());
        this.recipes.add(new MapExtendingCraftingRecipe());
        this.recipes.add(new RepairItemCraftingRecipe());
        this.recipes.add(new ShieldDecorationCraftingRecipe());
    }

    private void loadRecipes(Path recipeDirectory) {
        if (!Files.exists(recipeDirectory)) {
            LOGGER.warn("Recipe directory {} is missing.", recipeDirectory);
            return;
        }

        try (var recipePaths = Files.walk(recipeDirectory)) {
            recipePaths
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
            var fileName = recipePath.getFileName().toString();
            var recipeId =
                    Key.key(
                            Key.MINECRAFT_NAMESPACE,
                            fileName.substring(0, fileName.length() - ".json".length()));

            switch (recipeType) {
                case "minecraft:crafting_shaped" -> this.loadShaped(recipeId, recipeObject);
                case "minecraft:crafting_shapeless" -> this.loadShapeless(recipeId, recipeObject);
                case "minecraft:crafting_transmute" -> this.loadTransmute(recipeId, recipeObject);
                case "minecraft:crafting_dye" -> this.loadDye(recipeId, recipeObject);
                case "minecraft:crafting_imbue" -> this.loadImbue(recipeId, recipeObject);
                case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking" ->
                        this.loadCooking(recipeId, recipeType, recipeObject);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to parse recipe {}", recipePath, exception);
        }
    }

    private void loadShaped(Key recipeId, JsonObject recipeObject) {
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

        var recipe =
                new ShapedCraftingRecipe(
                        patternWidth, patternRows.size(), patternIngredients, resultStack);
        this.recipes.add(recipe);
        this.catalog.addShaped(
                recipeId,
                recipeObject,
                recipe,
                patternWidth,
                patternRows.size(),
                patternIngredients,
                resultStack);
    }

    private void loadShapeless(Key recipeId, JsonObject recipeObject) {
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

        var recipe = new ShapelessCraftingRecipe(ingredients, resultStack);
        this.recipes.add(recipe);
        this.catalog.addShapeless(recipeId, recipeObject, recipe, ingredients, resultStack);
    }

    private void loadTransmute(Key recipeId, JsonObject recipeObject) {
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

        var recipe =
                new TransmuteCraftingRecipe(
                        baseIngredient,
                        catalystIngredient,
                        resultStack.material(),
                        resultStack.amount(),
                        resultStack.componentPatch());
        this.recipes.add(recipe);
        this.catalog.addShapeless(
                recipeId,
                recipeObject,
                recipe,
                List.of(baseIngredient, catalystIngredient),
                resultStack);
    }

    private void loadCooking(Key recipeId, String recipeType, JsonObject recipeObject) {
        var input = this.parseIngredient(recipeObject.get("ingredient"));
        var resultElement = recipeObject.get("result");

        if (input == null || resultElement == null) {
            return;
        }

        var resultStack = this.readResult(resultElement, "cooking");

        if (!resultStack.isAir()) {
            this.catalog.addCooking(recipeId, recipeObject, recipeType, input, resultStack);
        }
    }

    private void loadDye(Key recipeId, JsonObject recipeObject) {
        var targetIngredient = this.parseIngredient(recipeObject.get("target"));
        var dyeIngredient = this.parseIngredient(recipeObject.get("dye"));

        if (targetIngredient != null && dyeIngredient != null) {
            var recipe = new ArmorDyeCraftingRecipe(targetIngredient);
            this.recipes.add(recipe);
            this.catalog.addDye(recipeId, recipeObject, recipe, targetIngredient, dyeIngredient);
        }
    }

    private void loadImbue(Key recipeId, JsonObject recipeObject) {
        var source = this.parseIngredient(recipeObject.get("source"));
        var material = this.parseIngredient(recipeObject.get("material"));
        var resultStack = this.readResult(recipeObject.get("result"), "imbue");

        if (source != null && material != null && !resultStack.isAir()) {
            var recipe = new TippedArrowCraftingRecipe();
            this.recipes.add(recipe);
            this.catalog.addImbue(recipeId, recipeObject, recipe, source, material, resultStack);
        }
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
            LOGGER.warn(
                    "Failed to decode {} recipe result {}", recipeType, resultElement, exception);
            return ItemStack.AIR;
        }
    }

    public EventNode<Event> events() {
        // Minestom has no handler for this packet, so PlayerPacketEvent never fires for it.
        MinecraftServer.getPacketListenerManager()
                .setPlayListener(
                        ClientRecipeBookSeenRecipePacket.class,
                        (packet, player) -> this.recipeSeen(player, packet.index()));
        return EventNode.all("crafting")
                .addListener(new CraftingPlayerSpawnListener(this))
                .addListener(new CraftingPlayerDisconnectListener(this))
                .addListener(new CraftingTableListener(this))
                .addListener(new CraftingGridListener(this))
                .addListener(new CraftingShiftClickListener(this))
                .addListener(new RecipeBookItemListener(this))
                .addListener(new RecipeBookPacketListener(this))
                .addListener(new CraftingCloseListener(this));
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
        var workspace =
                new CraftingWorkspace(
                        this, player, playerInventory, 36, new int[] {37, 38, 39, 40}, 2, 2);
        this.workspaces.put(playerInventory, workspace);
        workspace.refresh();
        this.books.join(player);
    }

    public void unregisterPlayer(Player player) {
        this.workspaces.remove(player.getInventory());
    }

    public Inventory openCraftingTable(Player player) {
        var craftingInventory =
                new Inventory(InventoryType.CRAFTING, Component.translatable("container.crafting"));
        var workspace =
                new CraftingWorkspace(
                        this,
                        player,
                        craftingInventory,
                        0,
                        new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9},
                        3,
                        3);
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

    /** Every recipe the recipe book can show, including smelting-family displays. */
    public RecipeCatalog recipeCatalog() {
        return this.catalog;
    }

    public RecipeBook recipeBook(Player player) {
        return this.books.of(player);
    }

    /** The player's recipe book in vanilla's {@code recipeBook} NBT format. */
    public static CompoundBinaryTag saveRecipeBook(Player player) {
        return RecipeBook.save(player);
    }

    /** Replaces the player's recipe book with saved data and sends it to their client. */
    public void restoreRecipeBook(Player player, CompoundBinaryTag data) {
        this.books.restore(player, data);
    }

    public void itemObtained(Player player, ItemStack stack) {
        this.books.itemObtained(player, stack);
    }

    public void recipeCrafted(Player player, CraftingRecipe recipe) {
        this.books.crafted(player, recipe);
    }

    public void recipeBookSettings(Player player, ClientSetRecipeBookStatePacket packet) {
        this.books.settings(player, packet);
    }

    public void recipeSeen(Player player, int displayId) {
        this.books.seen(player, displayId);
    }

    /** Handles a place-recipe request. Returns false if the recipe is not a crafting recipe. */
    public boolean placeRecipe(Player player, ClientPlaceRecipePacket packet) {
        var recipe = this.catalog.byDisplayId(packet.recipeDisplayId());

        if (recipe == null || recipe.layout() == null) {
            return false;
        }

        var open = player.getOpenInventory();
        var inventory = open == null ? player.getInventory() : open;
        var windowId = open instanceof Inventory table ? table.getWindowId() : 0;
        var workspace = this.workspaces.get(inventory);

        if (workspace == null
                || packet.windowId() != windowId
                || player.getGameMode() == GameMode.SPECTATOR
                || !this.books.of(player).contains(recipe.id())) {
            return true;
        }

        if (workspace.place(recipe, packet.makeAll()) == RecipePlacer.Result.MISSING_ITEMS) {
            player.sendPacket(new PlaceGhostRecipePacket(windowId, recipe.display()));
        }

        return true;
    }
}
