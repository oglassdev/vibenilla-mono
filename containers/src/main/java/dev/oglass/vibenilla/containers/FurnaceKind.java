package dev.oglass.vibenilla.containers;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.InventoryType;
import rocks.minestom.crafting.CookingRecipes;

enum FurnaceKind {
    FURNACE(
            Block.FURNACE,
            InventoryType.FURNACE,
            CookingRecipes.Type.SMELTING,
            "container.furnace",
            false),
    SMOKER(
            Block.SMOKER,
            InventoryType.SMOKER,
            CookingRecipes.Type.SMOKING,
            "container.smoker",
            true),
    BLAST_FURNACE(
            Block.BLAST_FURNACE,
            InventoryType.BLAST_FURNACE,
            CookingRecipes.Type.BLASTING,
            "container.blast_furnace",
            true);

    private final Block block;
    private final InventoryType inventoryType;
    private final CookingRecipes.Type recipeType;
    private final String titleKey;
    private final boolean fast;

    FurnaceKind(
            Block block,
            InventoryType inventoryType,
            CookingRecipes.Type recipeType,
            String titleKey,
            boolean fast) {
        this.block = block;
        this.inventoryType = inventoryType;
        this.recipeType = recipeType;
        this.titleKey = titleKey;
        this.fast = fast;
    }

    Block block() {
        return block;
    }

    Key key() {
        return block.key();
    }

    InventoryType inventoryType() {
        return inventoryType;
    }

    CookingRecipes.Type recipeType() {
        return recipeType;
    }

    String titleKey() {
        return titleKey;
    }

    /** Whether the furnace cooks twice as fast and burns fuel twice as fast. */
    boolean fast() {
        return fast;
    }
}
