package rocks.minestom.crafting.book;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.crafting.Ingredient;
import rocks.minestom.crafting.ItemTagManager;
import rocks.minestom.crafting.recipe.CraftingRecipe;

/**
 * Every recipe that appears in the recipe book, with the items whose pickup unlocks it. Triggers
 * come from the {@code inventory_changed} criteria of vanilla's recipe advancements, falling back
 * to the recipe's own ingredients.
 */
public final class RecipeCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeCatalog.class);

    private final List<BookRecipe> recipes = new ArrayList<>();
    private final Map<Key, BookRecipe> byId = new HashMap<>();
    private final Int2ObjectOpenHashMap<BookRecipe> byDisplayId = new Int2ObjectOpenHashMap<>();
    private final Map<CraftingRecipe, BookRecipe> byCrafting = new IdentityHashMap<>();
    private final Map<String, Integer> groups = new HashMap<>();
    private final Map<Material, List<BookRecipe>> byTrigger = new HashMap<>();
    private final List<BookRecipe> unlockedOnJoin = new ArrayList<>();

    public Collection<BookRecipe> recipes() {
        return this.recipes;
    }

    public @Nullable BookRecipe byId(Key id) {
        return this.byId.get(id);
    }

    public @Nullable BookRecipe byDisplayId(int displayId) {
        return this.byDisplayId.get(displayId);
    }

    public @Nullable BookRecipe byCrafting(CraftingRecipe recipe) {
        return this.byCrafting.get(recipe);
    }

    public List<BookRecipe> unlockedBy(Material material) {
        return this.byTrigger.getOrDefault(material, List.of());
    }

    public List<BookRecipe> unlockedOnJoin() {
        return this.unlockedOnJoin;
    }

    public void addShaped(
            Key id,
            JsonObject json,
            CraftingRecipe recipe,
            int width,
            int height,
            Ingredient[] pattern,
            ItemStack result) {
        var display =
                new RecipeDisplay.CraftingShaped(
                        width,
                        height,
                        Arrays.stream(pattern).map(RecipeCatalog::slot).toList(),
                        new SlotDisplay.ItemStack(result),
                        new SlotDisplay.Item(Material.CRAFTING_TABLE));
        var layout = new BookRecipe.Layout(true, width, height, pattern);
        this.add(id, json, display, craftingCategory(json), pattern, recipe, layout);
    }

    public void addShapeless(
            Key id,
            JsonObject json,
            CraftingRecipe recipe,
            List<Ingredient> ingredients,
            ItemStack result) {
        var display =
                new RecipeDisplay.CraftingShapeless(
                        ingredients.stream().map(RecipeCatalog::slot).toList(),
                        new SlotDisplay.ItemStack(result),
                        new SlotDisplay.Item(Material.CRAFTING_TABLE));
        var slots = ingredients.toArray(new Ingredient[0]);
        var layout = new BookRecipe.Layout(false, 0, 0, slots);
        this.add(id, json, display, craftingCategory(json), slots, recipe, layout);
    }

    public void addDye(
            Key id, JsonObject json, CraftingRecipe recipe, Ingredient target, Ingredient dye) {
        var onlyDyes = new SlotDisplay.OnlyWithComponent(slot(dye), DataComponents.DYE);
        var targetSlot = slot(target);
        var display =
                new RecipeDisplay.CraftingShapeless(
                        List.of(targetSlot, onlyDyes),
                        new SlotDisplay.Dyed(onlyDyes, targetSlot),
                        new SlotDisplay.Item(Material.CRAFTING_TABLE));
        var slots = new Ingredient[] {target, dye};
        var layout = new BookRecipe.Layout(false, 0, 0, slots);
        this.add(id, json, display, craftingCategory(json), slots, recipe, layout);
    }

    public void addImbue(
            Key id,
            JsonObject json,
            CraftingRecipe recipe,
            Ingredient source,
            Ingredient material,
            ItemStack result) {
        var materialSlot = slot(material);
        var display =
                new RecipeDisplay.CraftingShaped(
                        3,
                        3,
                        List.of(
                                materialSlot,
                                materialSlot,
                                materialSlot,
                                materialSlot,
                                new SlotDisplay.WithAnyPotion(slot(source)),
                                materialSlot,
                                materialSlot,
                                materialSlot,
                                materialSlot),
                        new SlotDisplay.WithAnyPotion(new SlotDisplay.ItemStack(result)),
                        new SlotDisplay.Item(Material.CRAFTING_TABLE));
        var slots =
                new Ingredient[] {
                    material, material, material, material, source, material, material, material,
                    material
                };
        var layout = new BookRecipe.Layout(true, 3, 3, slots);
        this.add(id, json, display, craftingCategory(json), slots, recipe, layout);
    }

    /** Registers a smelting, blasting or smoking recipe; the entry is display-only. */
    public void addCooking(
            Key id, JsonObject json, String type, Ingredient input, ItemStack result) {
        var category = json.has("category") ? json.get("category").getAsString() : "misc";
        var blocks = category.equals("blocks");
        var station =
                switch (type) {
                    case "minecraft:blasting" -> Material.BLAST_FURNACE;
                    case "minecraft:smoking" -> Material.SMOKER;
                    default -> Material.FURNACE;
                };
        var bookCategory =
                switch (type) {
                    case "minecraft:blasting" ->
                            blocks
                                    ? RecipeBookCategory.BLAST_FURNACE_BLOCKS
                                    : RecipeBookCategory.BLAST_FURNACE_MISC;
                    case "minecraft:smoking" -> RecipeBookCategory.SMOKER_FOOD;
                    default ->
                            switch (category) {
                                case "food" -> RecipeBookCategory.FURNACE_FOOD;
                                case "blocks" -> RecipeBookCategory.FURNACE_BLOCKS;
                                default -> RecipeBookCategory.FURNACE_MISC;
                            };
                };
        var duration = json.has("cookingtime") ? json.get("cookingtime").getAsInt() : 200;
        var experience = json.has("experience") ? json.get("experience").getAsFloat() : 0f;
        var display =
                new RecipeDisplay.Furnace(
                        slot(input),
                        SlotDisplay.AnyFuel.INSTANCE,
                        new SlotDisplay.ItemStack(result),
                        new SlotDisplay.Item(station),
                        duration,
                        experience);
        this.add(id, json, display, bookCategory, new Ingredient[] {input}, null, null);
    }

    private void add(
            Key id,
            JsonObject json,
            RecipeDisplay display,
            RecipeBookCategory category,
            Ingredient[] inputs,
            @Nullable CraftingRecipe crafting,
            BookRecipe.@Nullable Layout layout) {
        var group =
                json.has("group") && !json.get("group").getAsString().isEmpty()
                        ? this.groups.computeIfAbsent(
                                json.get("group").getAsString(), name -> this.groups.size())
                        : null;
        var requirements =
                Arrays.stream(inputs)
                        .filter(Objects::nonNull)
                        .map(
                                i ->
                                        new net.minestom.server.recipe.Ingredient(
                                                List.copyOf(i.getMaterials())))
                        .toList();
        var entry =
                new RecipeBookAddPacket.Entry(
                        this.recipes.size(), display, group, category, requirements, (byte) 0);
        var materials = new LinkedHashSet<Material>();
        for (var input : inputs) {
            if (input != null) {
                materials.addAll(input.getMaterials());
            }
        }
        var recipe =
                new BookRecipe(
                        id,
                        entry,
                        crafting,
                        layout,
                        materials,
                        !json.has("show_notification")
                                || json.get("show_notification").getAsBoolean());
        this.recipes.add(recipe);
        this.byId.put(id, recipe);
        this.byDisplayId.put(entry.displayId(), recipe);
        if (crafting != null) {
            this.byCrafting.put(crafting, recipe);
        }
    }

    /**
     * Reads the recipe advancements under {@code advancementDirectory} and links each recipe to the
     * items that unlock it. Recipes without an item trigger are unlocked by their own ingredients.
     */
    public void indexTriggers(Path advancementDirectory, ItemTagManager tags) {
        var triggers = new HashMap<Key, Set<Material>>();
        var immediate = new HashSet<Key>();
        if (Files.isDirectory(advancementDirectory)) {
            try (var paths = Files.walk(advancementDirectory)) {
                paths.filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> readAdvancement(path, tags, triggers, immediate));
            } catch (IOException exception) {
                LOGGER.error("Failed to read recipe advancements", exception);
            }
        }

        this.byTrigger.clear();
        this.unlockedOnJoin.clear();
        for (var recipe : this.recipes) {
            if (immediate.contains(recipe.id())) {
                this.unlockedOnJoin.add(recipe);
                continue;
            }
            var materials = triggers.getOrDefault(recipe.id(), Set.of());
            if (materials.isEmpty()) {
                materials = recipe.ingredients();
            }
            for (var material : materials) {
                this.byTrigger.computeIfAbsent(material, key -> new ArrayList<>()).add(recipe);
            }
        }
    }

    private static void readAdvancement(
            Path path, ItemTagManager tags, Map<Key, Set<Material>> triggers, Set<Key> immediate) {
        try (var reader = Files.newBufferedReader(path)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var rewards = root.getAsJsonObject("rewards");
            var criteria = root.getAsJsonObject("criteria");
            if (rewards == null || criteria == null || !rewards.has("recipes")) {
                return;
            }

            var materials = new LinkedHashSet<Material>();
            var tick = false;
            for (var criterion : criteria.entrySet()) {
                var object = criterion.getValue().getAsJsonObject();
                switch (object.get("trigger").getAsString()) {
                    case "minecraft:tick" -> tick = true;
                    case "minecraft:inventory_changed" -> collectItems(object, tags, materials);
                    default -> {}
                }
            }

            for (var recipe : rewards.getAsJsonArray("recipes")) {
                var id = Key.key(recipe.getAsString());
                if (tick) {
                    immediate.add(id);
                } else {
                    triggers.computeIfAbsent(id, key -> new LinkedHashSet<>()).addAll(materials);
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse recipe advancement {}", path, exception);
        }
    }

    private static void collectItems(
            JsonObject criterion, ItemTagManager tags, Set<Material> materials) {
        var conditions = criterion.getAsJsonObject("conditions");
        if (conditions == null || !conditions.has("items")) {
            return;
        }
        for (var predicate : conditions.getAsJsonArray("items")) {
            var items = predicate.getAsJsonObject().get("items");
            if (items == null) {
                continue;
            }
            for (var item :
                    items.isJsonArray() ? items.getAsJsonArray().asList() : List.of(items)) {
                collectItem(item, tags, materials);
            }
        }
    }

    private static void collectItem(JsonElement item, ItemTagManager tags, Set<Material> into) {
        var value = item.getAsString();
        if (value.startsWith("#")) {
            into.addAll(tags.materials(Key.key(value.substring(1))));
            return;
        }
        var material = Material.fromKey(value);
        if (material != null) {
            into.add(material);
        }
    }

    private static SlotDisplay slot(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return SlotDisplay.Empty.INSTANCE;
        }
        var materials = ingredient.getMaterials();
        if (materials.size() == 1) {
            return new SlotDisplay.Item(materials.iterator().next());
        }
        return new SlotDisplay.Composite(
                materials.stream().<SlotDisplay>map(SlotDisplay.Item::new).toList());
    }

    private static RecipeBookCategory craftingCategory(JsonObject json) {
        var category = json.has("category") ? json.get("category").getAsString() : "misc";
        return switch (category) {
            case "building" -> RecipeBookCategory.CRAFTING_BUILDING_BLOCKS;
            case "redstone" -> RecipeBookCategory.CRAFTING_REDSTONE;
            case "equipment" -> RecipeBookCategory.CRAFTING_EQUIPMENT;
            default -> RecipeBookCategory.CRAFTING_MISC;
        };
    }
}
