package rocks.minestom.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientPlaceRecipePacket;
import net.minestom.server.network.packet.client.play.ClientRecipeBookSeenRecipePacket;
import net.minestom.server.network.packet.client.play.ClientSetRecipeBookStatePacket;
import net.minestom.server.network.packet.client.play.ClientSetRecipeBookStatePacket.BookType;
import net.minestom.server.network.packet.server.play.PlaceGhostRecipePacket;
import net.minestom.server.network.packet.server.play.RecipeBookAddPacket;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;

@EnvTest
final class RecipeBookTest {
    private static final Path DATA_PATH = Path.of("data", "minecraft");
    private static final Key OAK_PLANKS = Key.key("minecraft:oak_planks");
    private static final Key CRAFTING_TABLE = Key.key("minecraft:crafting_table");

    private static CraftingManager manager;

    private record Joined(Player player, TestConnection connection) {}

    private static Joined join(Env env) {
        if (manager == null) {
            manager = new CraftingManager(DATA_PATH);
        }
        env.process().eventHandler().addChild(manager.events());
        var connection = env.createConnection();
        var player = connection.connect(env.createFlatInstance(), new Pos(0, 40, 0));
        env.tickWhile(
                () -> manager.workspace(player.getInventory()) == null, Duration.ofSeconds(5));
        return new Joined(player, connection);
    }

    private static void place(Env env, Player player, Key recipe, boolean all) {
        var displayId = manager.recipeCatalog().byId(recipe).displayId();
        env.process()
                .packetListener()
                .processClientPacket(
                        new ClientPlaceRecipePacket((byte) 0, displayId, all),
                        player.getPlayerConnection());
    }

    @Test
    void pickingUpAnIngredientUnlocksItsRecipeOnce(Env env) {
        var joined = join(env);
        var inventory = joined.player().getInventory();
        var adds = joined.connection().trackIncoming(RecipeBookAddPacket.class);
        var planks = manager.recipeCatalog().byId(OAK_PLANKS).displayId();

        inventory.addItemStack(ItemStack.of(Material.OAK_LOG));

        var added = adds.collect();
        assertEquals(1, added.size());
        assertFalse(added.getFirst().replace());
        var entry =
                added.getFirst().entries().stream()
                        .filter(e -> e.displayId() == planks)
                        .findFirst()
                        .orElseThrow();
        assertTrue(entry.notification());
        assertTrue(manager.recipeBook(joined.player()).contains(OAK_PLANKS));

        var again = joined.connection().trackIncoming(RecipeBookAddPacket.class);
        inventory.addItemStack(ItemStack.of(Material.OAK_LOG));
        again.assertEmpty();
    }

    @Test
    void unlockHonoursShowNotificationButStillHighlights(Env env) {
        var joined = join(env);
        var adds = joined.connection().trackIncoming(RecipeBookAddPacket.class);
        var table = manager.recipeCatalog().byId(CRAFTING_TABLE).displayId();

        manager.restoreRecipeBook(joined.player(), CompoundBinaryTag.empty());

        var entry =
                adds.collect().stream()
                        .filter(add -> !add.replace())
                        .flatMap(add -> add.entries().stream())
                        .filter(e -> e.displayId() == table)
                        .findFirst()
                        .orElseThrow();
        assertFalse(entry.notification());
        assertTrue(entry.highlight());
    }

    @Test
    void seenRecipePacketRemovesHighlight(Env env) {
        var joined = join(env);
        var player = joined.player();
        player.getInventory().addItemStack(ItemStack.of(Material.OAK_LOG));
        var book = manager.recipeBook(player);
        assertTrue(book.isHighlighted(OAK_PLANKS));

        var displayId = manager.recipeCatalog().byId(OAK_PLANKS).displayId();
        env.process()
                .packetListener()
                .processClientPacket(
                        new ClientRecipeBookSeenRecipePacket(displayId),
                        player.getPlayerConnection());

        assertFalse(book.isHighlighted(OAK_PLANKS));
    }

    @Test
    void dyeAndImbueRecipesAreInTheBook(Env env) {
        var joined = join(env);
        var catalog = manager.recipeCatalog();
        var boots = catalog.byId(Key.key("minecraft:leather_boots_dyed"));
        var arrows = catalog.byId(Key.key("minecraft:tipped_arrow"));

        assertNotNull(boots);
        assertNotNull(arrows);
        assertNotNull(boots.crafting());
        assertSame(boots, catalog.byCrafting(boots.crafting()));
        assertSame(arrows, catalog.byCrafting(arrows.crafting()));

        joined.player().getInventory().addItemStack(ItemStack.of(Material.LEATHER_BOOTS));
        assertTrue(manager.recipeBook(joined.player()).contains(boots.id()));
    }

    @Test
    void placeRecipeFillsTheInventoryGrid(Env env) {
        var joined = join(env);
        var player = joined.player();
        player.getInventory().setItemStack(0, ItemStack.of(Material.OAK_PLANKS, 8));

        place(env, player, CRAFTING_TABLE, false);

        for (var slot :
                new int[] {
                    PlayerInventoryUtils.CRAFT_SLOT_1,
                    PlayerInventoryUtils.CRAFT_SLOT_2,
                    PlayerInventoryUtils.CRAFT_SLOT_3,
                    PlayerInventoryUtils.CRAFT_SLOT_4
                }) {
            assertEquals(
                    ItemStack.of(Material.OAK_PLANKS), player.getInventory().getItemStack(slot));
        }
        assertEquals(ItemStack.of(Material.OAK_PLANKS, 4), player.getInventory().getItemStack(0));
        assertEquals(
                Material.CRAFTING_TABLE,
                player.getInventory().getItemStack(PlayerInventoryUtils.CRAFT_RESULT).material());

        place(env, player, CRAFTING_TABLE, true);

        assertEquals(
                ItemStack.of(Material.OAK_PLANKS, 2),
                player.getInventory().getItemStack(PlayerInventoryUtils.CRAFT_SLOT_1));
        assertTrue(player.getInventory().getItemStack(0).isAir());
    }

    @Test
    void placeRecipeWithoutItemsSendsGhostRecipe(Env env) {
        var joined = join(env);
        var ghosts = joined.connection().trackIncoming(PlaceGhostRecipePacket.class);

        place(env, joined.player(), CRAFTING_TABLE, false);

        ghosts.assertSingle();
    }

    @Test
    void recipeBookSurvivesSaveAndRestore(Env env) {
        var joined = join(env);
        var player = joined.player();
        player.getInventory().addItemStack(ItemStack.of(Material.OAK_LOG));
        env.process()
                .packetListener()
                .processClientPacket(
                        new ClientSetRecipeBookStatePacket(BookType.CRAFTING, true, true),
                        player.getPlayerConnection());
        var saved = CraftingManager.saveRecipeBook(player);

        var restored = join(env);
        var adds = restored.connection().trackIncoming(RecipeBookAddPacket.class);
        manager.restoreRecipeBook(restored.player(), saved);

        assertEquals(
                manager.recipeBook(player).unlocked(),
                manager.recipeBook(restored.player()).unlocked());
        assertTrue(manager.recipeBook(restored.player()).contains(OAK_PLANKS));
        assertEquals(saved, CraftingManager.saveRecipeBook(restored.player()));
        assertTrue(saved.getBoolean("isGuiOpen"));
        assertTrue(saved.getBoolean("isFilteringCraftable"));

        var replace = adds.collect().getFirst();
        assertTrue(replace.replace());
        assertTrue(replace.entries().stream().noneMatch(RecipeBookAddPacket.Entry::notification));
    }
}
