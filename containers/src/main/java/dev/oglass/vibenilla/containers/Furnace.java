package dev.oglass.vibenilla.containers;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryProperty;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.crafting.CookingRecipes;
import rocks.minestom.crafting.CookingRecipes.Recipe;

/**
 * The state of one furnace, smoker or blast furnace block while it is open or has work to do. The
 * inventory holds the slots, and the block entity is rewritten whenever a slot changes, the furnace
 * lights or goes out, and once a second while it burns.
 */
final class Furnace {
    static final int INPUT = 0;
    static final int FUEL = 1;
    static final int OUTPUT = 2;
    static final int SLOTS = 3;

    private static final int SAVE_INTERVAL = 20;
    private static final String LIT_TIME_REMAINING = "lit_time_remaining";
    private static final String LIT_TOTAL_TIME = "lit_total_time";
    private static final String COOKING_TIME_SPENT = "cooking_time_spent";
    private static final String COOKING_TOTAL_TIME = "cooking_total_time";
    private static final String RECIPES_USED = "RecipesUsed";
    private static final InventoryProperty[] PROPERTIES = {
        InventoryProperty.FURNACE_FIRE_ICON,
        InventoryProperty.FURNACE_MAXIMUM_FUEL_BURN_TIME,
        InventoryProperty.FURNACE_PROGRESS_ARROW,
        InventoryProperty.FURNACE_MAXIMUM_PROGRESS
    };

    record Cooking(
            CookingRecipes recipes, CookingFuels fuels, Furnaces.ExperienceSpawner experience) {}

    /** The furnace window, which sends the burn and cook progress to its viewers. */
    final class View extends Inventory {
        private final BiConsumer<Furnace, Player> released;
        private final short[] progress = new short[4];

        View(Component title, BiConsumer<Furnace, Player> released) {
            super(kind.inventoryType(), title);
            this.released = released;
        }

        Furnace furnace() {
            return Furnace.this;
        }

        @Override
        public boolean addViewer(Player player) {
            var added = super.addViewer(player);

            if (added) {
                for (var property = 0; property < progress.length; property++) {
                    sendProperty(PROPERTIES[property], progress[property]);
                }
            }
            return added;
        }

        @Override
        public boolean removeViewer(Player player) {
            var removed = super.removeViewer(player);

            if (removed) {
                released.accept(Furnace.this, player);
            }
            return removed;
        }

        void setProgress(int litTimeRemaining, int litTotalTime, int spent, int total) {
            var values = new int[] {litTimeRemaining, litTotalTime, spent, total};

            for (var property = 0; property < progress.length; property++) {
                var value = (short) Math.min(values[property], Short.MAX_VALUE);

                if (progress[property] != value) {
                    progress[property] = value;
                    sendProperty(PROPERTIES[property], value);
                }
            }
        }
    }

    final FurnaceKind kind;
    final Instance instance;
    final BlockVec position;
    final View view;

    private final Cooking cooking;
    private final Map<Key, Integer> recipesUsed = new LinkedHashMap<>();
    private int litTimeRemaining;
    private int litTotalTime;
    private int cookingTimeSpent;
    private int cookingTotalTime;
    private int ticksSinceSave;
    private boolean ticking;
    private @Nullable Player taker;

    Furnace(
            FurnaceKind kind,
            Instance instance,
            BlockVec position,
            Block block,
            Cooking cooking,
            BiConsumer<Furnace, Player> released) {
        this.kind = kind;
        this.instance = instance;
        this.position = position;
        this.cooking = cooking;

        var name = ContainerItems.customName(block);
        this.view =
                new View(name != null ? name : Component.translatable(kind.titleKey()), released);

        var items = ContainerItems.read(block, SLOTS);

        for (var slot = 0; slot < SLOTS; slot++) {
            view.setItemStack(slot, items[slot]);
        }

        var nbt = block.nbtOrEmpty();
        litTimeRemaining = nbt.getInt(LIT_TIME_REMAINING);
        litTotalTime = nbt.getInt(LIT_TOTAL_TIME);
        cookingTimeSpent = nbt.getInt(COOKING_TIME_SPENT);
        cookingTotalTime = nbt.getInt(COOKING_TOTAL_TIME);

        var used = nbt.getCompound(RECIPES_USED);

        for (var id : used.keySet()) {
            recipesUsed.put(Key.key(id), used.getInt(id));
        }

        syncProgress();
        view.eventNode().addListener(InventoryItemChangeEvent.class, this::itemChanged);
    }

    boolean isLit() {
        return litTimeRemaining > 0;
    }

    /** Remembers who is clicking the result slot, so experience is awarded at their position. */
    void taking(@Nullable Player player) {
        taker = player;
    }

    /** Whether the furnace has no fuel burning, no progress to lose and nothing it can start. */
    boolean isIdle() {
        return !isLit()
                && cookingTimeSpent == 0
                && !(cooking.fuels().isFuel(view.getItemStack(FUEL)) && canBurn(recipe()));
    }

    /** Advances the furnace by one game tick. */
    void tick() {
        ticking = true;
        taker = null;

        try {
            var wasLit = isLit();
            var spent = cookingTimeSpent;
            var changed = false;

            if (wasLit) {
                litTimeRemaining--;
            }

            var input = view.getItemStack(INPUT);
            var fuel = view.getItemStack(FUEL);

            if (isLit() || (!fuel.isAir() && !input.isAir())) {
                var recipe = recipe();

                if (!isLit() && canBurn(recipe)) {
                    litTimeRemaining = cooking.fuels().burnTime(fuel, kind.fast());
                    litTotalTime = litTimeRemaining;

                    if (isLit()) {
                        consumeFuel(fuel);
                        changed = true;
                    }
                }

                if (isLit() && canBurn(recipe)) {
                    cookingTotalTime = recipe.cookingTime();
                    cookingTimeSpent += Math.max(1, Math.round(cooking.fuels().speed(kind.fast())));

                    if (cookingTimeSpent >= cookingTotalTime) {
                        cookingTimeSpent = 0;
                        burn(recipe);
                        changed = true;
                    }
                } else {
                    cookingTimeSpent = 0;
                }
            } else if (cookingTimeSpent > 0) {
                cookingTimeSpent = Math.clamp(cookingTimeSpent - 2, 0, cookingTotalTime);
            }

            syncProgress();
            ticksSinceSave++;

            if (isLit() != wasLit) {
                changed = true;
            }

            if (changed
                    || (ticksSinceSave >= SAVE_INTERVAL
                            && (wasLit || isLit() || spent != cookingTimeSpent))) {
                save();
            }
        } finally {
            ticking = false;
        }
    }

    /** Writes the slots, burn and cook state and the {@code lit} property into the block. */
    void save() {
        ticksSinceSave = 0;

        if (!instance.isChunkLoaded(position)) {
            return;
        }

        var block = instance.getBlock(position);

        if (!block.compare(kind.block())) {
            return;
        }

        var written = ContainerItems.write(block, Arrays.asList(view.getItemStacks()));
        var nbt =
                written.nbtOrEmpty()
                        .putShort(LIT_TIME_REMAINING, (short) litTimeRemaining)
                        .putShort(LIT_TOTAL_TIME, (short) litTotalTime)
                        .putShort(COOKING_TIME_SPENT, (short) cookingTimeSpent)
                        .putShort(COOKING_TOTAL_TIME, (short) cookingTotalTime);

        if (recipesUsed.isEmpty()) {
            nbt = nbt.remove(RECIPES_USED);
        } else {
            var used = CompoundBinaryTag.builder();
            recipesUsed.forEach((id, count) -> used.putInt(id.asString(), count));
            nbt = nbt.put(RECIPES_USED, used.build());
        }

        var updated = written.withNbt(nbt).withProperty("lit", String.valueOf(isLit()));

        if (!updated.equals(block)) {
            instance.setBlock(position, updated, false);
        }
    }

    /** Clears the stored recipe uses and returns the experience they are worth. */
    int takeExperience() {
        var points = 0;

        for (var entry : recipesUsed.entrySet()) {
            var recipe = cooking.recipes().get(entry.getKey());

            if (recipe == null) {
                continue;
            }

            var value = entry.getValue() * recipe.experience();
            var whole = (int) Math.floor(value);
            points += whole;

            if (ThreadLocalRandom.current().nextFloat() < value - whole) {
                points++;
            }
        }

        recipesUsed.clear();
        return points;
    }

    private void itemChanged(InventoryItemChangeEvent event) {
        if (ticking) {
            return;
        }

        var previous = event.getPreviousItem();
        var current = event.getNewItem();

        if (event.getSlot() == INPUT && !previous.isSimilar(current)) {
            var recipe = recipe();
            cookingTimeSpent = 0;
            cookingTotalTime = recipe == null ? cookingTotalTime : recipe.cookingTime();
        }

        if (event.getSlot() == OUTPUT
                && !previous.isAir()
                && (current.isAir()
                        || !current.isSimilar(previous)
                        || current.amount() < previous.amount())) {
            var points = takeExperience();

            if (points > 0) {
                var point = taker != null ? taker.getPosition() : position.add(0.5, 0.5, 0.5);
                cooking.experience().spawn(instance, point, points);
            }
        }

        taker = null;
        syncProgress();
        save();
    }

    private void syncProgress() {
        view.setProgress(litTimeRemaining, litTotalTime, cookingTimeSpent, cookingTotalTime);
    }

    private @Nullable Recipe recipe() {
        return cooking.recipes().find(kind.recipeType(), view.getItemStack(INPUT));
    }

    private boolean canBurn(@Nullable Recipe recipe) {
        if (recipe == null) {
            return false;
        }

        var output = view.getItemStack(OUTPUT);
        var result = recipe.result();
        return output.isAir()
                || (output.isSimilar(result)
                        && output.amount() + result.amount() <= output.maxStackSize());
    }

    private void consumeFuel(ItemStack fuel) {
        if (fuel.amount() > 1) {
            view.setItemStack(FUEL, fuel.withAmount(fuel.amount() - 1));
        } else {
            view.setItemStack(
                    FUEL,
                    fuel.material() == Material.LAVA_BUCKET
                            ? ItemStack.of(Material.BUCKET)
                            : ItemStack.AIR);
        }
    }

    private void burn(Recipe recipe) {
        var input = view.getItemStack(INPUT);
        var output = view.getItemStack(OUTPUT);
        var result = recipe.result();

        if (input.material() == Material.WET_SPONGE
                && view.getItemStack(FUEL).material() == Material.BUCKET) {
            view.setItemStack(FUEL, ItemStack.of(Material.WATER_BUCKET));
        }

        view.setItemStack(
                OUTPUT,
                output.isAir() ? result : output.withAmount(output.amount() + result.amount()));
        view.setItemStack(
                INPUT, input.amount() > 1 ? input.withAmount(input.amount() - 1) : ItemStack.AIR);
        recipesUsed.merge(recipe.id(), 1, Integer::sum);
    }
}
