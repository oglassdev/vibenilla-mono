package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.Material;
import rocks.minestom.crafting.CraftingGrid;

import java.util.Map;

public final class ShieldDecorationCraftingRecipe implements CraftingRecipe {
    private static final Map<Material, DyeColor> BANNER_COLORS = Map.ofEntries(
            Map.entry(Material.WHITE_BANNER, DyeColor.WHITE),
            Map.entry(Material.ORANGE_BANNER, DyeColor.ORANGE),
            Map.entry(Material.MAGENTA_BANNER, DyeColor.MAGENTA),
            Map.entry(Material.LIGHT_BLUE_BANNER, DyeColor.LIGHT_BLUE),
            Map.entry(Material.YELLOW_BANNER, DyeColor.YELLOW),
            Map.entry(Material.LIME_BANNER, DyeColor.LIME),
            Map.entry(Material.PINK_BANNER, DyeColor.PINK),
            Map.entry(Material.GRAY_BANNER, DyeColor.GRAY),
            Map.entry(Material.LIGHT_GRAY_BANNER, DyeColor.LIGHT_GRAY),
            Map.entry(Material.CYAN_BANNER, DyeColor.CYAN),
            Map.entry(Material.PURPLE_BANNER, DyeColor.PURPLE),
            Map.entry(Material.BLUE_BANNER, DyeColor.BLUE),
            Map.entry(Material.BROWN_BANNER, DyeColor.BROWN),
            Map.entry(Material.GREEN_BANNER, DyeColor.GREEN),
            Map.entry(Material.RED_BANNER, DyeColor.RED),
            Map.entry(Material.BLACK_BANNER, DyeColor.BLACK)
    );

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() != 2) {
            return null;
        }

        CraftingGrid.CraftingGridSlot shieldSlot = null;
        CraftingGrid.CraftingGridSlot bannerSlot = null;

        for (var gridSlot : nonEmptySlots) {
            var item = gridSlot.item();

            if (item.material() == Material.SHIELD) {
                if (shieldSlot != null) {
                    return null;
                }

                var existingPatterns = item.get(DataComponents.BANNER_PATTERNS);
                if (existingPatterns != null && !existingPatterns.layers().isEmpty()) {
                    return null;
                }

                shieldSlot = gridSlot;
            } else if (BANNER_COLORS.containsKey(item.material())) {
                if (bannerSlot != null) {
                    return null;
                }
                bannerSlot = gridSlot;
            } else {
                return null;
            }
        }

        if (shieldSlot == null || bannerSlot == null) {
            return null;
        }

        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);
        usage.addTo(shieldSlot.slotIndex(), 1);
        usage.addTo(bannerSlot.slotIndex(), 1);

        var bannerItem = bannerSlot.item();
        var bannerPatterns = bannerItem.get(DataComponents.BANNER_PATTERNS);
        var bannerColor = BANNER_COLORS.get(bannerItem.material());

        var result = shieldSlot.item().withAmount(1);

        if (bannerPatterns != null) {
            result = result.with(DataComponents.BANNER_PATTERNS, bannerPatterns);
        }

        if (bannerColor != null) {
            result = result.with(DataComponents.BASE_COLOR, bannerColor);
        }

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }
}
