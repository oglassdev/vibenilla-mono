package rocks.minestom.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.client.play.ClientSetRecipeBookStatePacket;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.crafting.book.BookRecipe;
import rocks.minestom.crafting.book.RecipeBook;
import rocks.minestom.crafting.book.RecipeCatalog;
import rocks.minestom.crafting.recipe.CraftingRecipe;

/** Unlocks recipes for players and keeps their clients' recipe books in sync. */
final class PlayerRecipeBooks {
    private final RecipeCatalog catalog;

    PlayerRecipeBooks(RecipeCatalog catalog) {
        this.catalog = catalog;
    }

    RecipeBook of(Player player) {
        var book = player.getTag(RecipeBook.TAG);
        if (book == null) {
            book = new RecipeBook();
            player.setTag(RecipeBook.TAG, book);
        }
        return book;
    }

    void join(Player player) {
        this.of(player);
        this.send(player);
        this.unlock(player, this.catalog.unlockedOnJoin());
    }

    /** Replaces the player's book with saved data and unlocks what their inventory implies. */
    void restore(Player player, @Nullable CompoundBinaryTag data) {
        player.setTag(RecipeBook.TAG, RecipeBook.load(data, id -> this.catalog.byId(id) != null));
        this.send(player);

        var unlocks = new ArrayList<>(this.catalog.unlockedOnJoin());
        for (var stack : player.getInventory().getItemStacks()) {
            unlocks.addAll(this.catalog.unlockedBy(stack.material()));
        }
        this.unlock(player, unlocks);
    }

    void itemObtained(Player player, ItemStack stack) {
        if (!stack.isAir()) {
            this.unlock(player, this.catalog.unlockedBy(stack.material()));
        }
    }

    void crafted(Player player, CraftingRecipe recipe) {
        var bookRecipe = this.catalog.byCrafting(recipe);
        if (bookRecipe != null) {
            this.unlock(player, List.of(bookRecipe));
        }
    }

    void settings(Player player, ClientSetRecipeBookStatePacket packet) {
        this.of(player).setState(packet.bookType(), packet.bookOpen(), packet.filterActive());
    }

    void seen(Player player, int displayId) {
        var recipe = this.catalog.byDisplayId(displayId);
        if (recipe != null) {
            this.of(player).removeHighlight(recipe.id());
        }
    }

    /** Unlocks recipes the player does not know yet and shows them a notification. */
    private void unlock(Player player, Collection<BookRecipe> recipes) {
        var book = this.of(player);
        var entries = new ArrayList<RecipeBookAddPacket.Entry>();
        for (var recipe : recipes) {
            if (book.unlock(recipe.id())) {
                entries.add(recipe.entry(true, true));
            }
        }
        if (!entries.isEmpty()) {
            player.sendPacket(new RecipeBookAddPacket(entries, false));
        }
    }

    private void send(Player player) {
        var book = this.of(player);
        var entries = new ArrayList<RecipeBookAddPacket.Entry>();
        for (var id : book.unlocked()) {
            var recipe = this.catalog.byId(id);
            if (recipe != null) {
                entries.add(recipe.entry(false, book.isHighlighted(id)));
            }
        }
        player.sendPacket(book.settingsPacket());
        player.sendPacket(new RecipeBookAddPacket(entries, true));
    }
}
