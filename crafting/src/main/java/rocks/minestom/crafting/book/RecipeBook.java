package rocks.minestom.crafting.book;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.play.ClientSetRecipeBookStatePacket.BookType;
import net.minestom.server.network.packet.server.play.RecipeBookSettingsPacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * A player's unlocked recipes, the ones still highlighted as new, and the open and filter settings
 * of each book type. Stored in vanilla's {@code recipeBook} format.
 */
public final class RecipeBook {
    public static final Tag<RecipeBook> TAG = Tag.Transient("vibenilla:recipe_book");

    private static final String[][] SETTING_KEYS = {
        {"isGuiOpen", "isFilteringCraftable"},
        {"isFurnaceGuiOpen", "isFurnaceFilteringCraftable"},
        {"isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable"},
        {"isSmokerGuiOpen", "isSmokerFilteringCraftable"}
    };

    private final Set<Key> unlocked = new LinkedHashSet<>();
    private final Set<Key> highlighted = new LinkedHashSet<>();
    private final boolean[] open = new boolean[SETTING_KEYS.length];
    private final boolean[] filtering = new boolean[SETTING_KEYS.length];

    public static CompoundBinaryTag save(Player player) {
        var book = player.getTag(TAG);
        return book == null ? CompoundBinaryTag.empty() : book.save();
    }

    public static RecipeBook load(@Nullable CompoundBinaryTag data, Predicate<Key> known) {
        var book = new RecipeBook();
        if (data == null) {
            return book;
        }
        readKeys(data, "recipes", known, book.unlocked);
        readKeys(data, "toBeDisplayed", known, book.highlighted);
        book.highlighted.retainAll(book.unlocked);
        for (var type = 0; type < SETTING_KEYS.length; type++) {
            book.open[type] = data.getBoolean(SETTING_KEYS[type][0]);
            book.filtering[type] = data.getBoolean(SETTING_KEYS[type][1]);
        }
        return book;
    }

    private static void readKeys(
            CompoundBinaryTag data, String name, Predicate<Key> known, Set<Key> into) {
        for (var tag : data.getList(name, BinaryTagTypes.STRING)) {
            var value = ((StringBinaryTag) tag).value();
            if (!Key.parseable(value)) {
                continue;
            }
            var key = Key.key(value);
            if (known.test(key)) {
                into.add(key);
            }
        }
    }

    public CompoundBinaryTag save() {
        var data = CompoundBinaryTag.builder();
        data.put("recipes", keys(this.unlocked));
        data.put("toBeDisplayed", keys(this.highlighted));
        for (var type = 0; type < SETTING_KEYS.length; type++) {
            data.putBoolean(SETTING_KEYS[type][0], this.open[type]);
            data.putBoolean(SETTING_KEYS[type][1], this.filtering[type]);
        }
        return data.build();
    }

    private static ListBinaryTag keys(Set<Key> keys) {
        var list = ListBinaryTag.builder(BinaryTagTypes.STRING);
        for (var key : keys) {
            list.add(StringBinaryTag.stringBinaryTag(key.asString()));
        }
        return list.build();
    }

    public boolean contains(Key recipe) {
        return this.unlocked.contains(recipe);
    }

    public Set<Key> unlocked() {
        return Collections.unmodifiableSet(this.unlocked);
    }

    public boolean isHighlighted(Key recipe) {
        return this.highlighted.contains(recipe);
    }

    /** Unlocks and highlights the recipe. Returns false if it was already unlocked. */
    public boolean unlock(Key recipe) {
        if (!this.unlocked.add(recipe)) {
            return false;
        }
        this.highlighted.add(recipe);
        return true;
    }

    public void removeHighlight(Key recipe) {
        this.highlighted.remove(recipe);
    }

    public void setState(BookType type, boolean open, boolean filtering) {
        this.open[type.ordinal()] = open;
        this.filtering[type.ordinal()] = filtering;
    }

    public RecipeBookSettingsPacket settingsPacket() {
        return new RecipeBookSettingsPacket(
                this.open[0],
                this.filtering[0],
                this.open[1],
                this.filtering[1],
                this.open[2],
                this.filtering[2],
                this.open[3],
                this.filtering[3]);
    }
}
