package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import rocks.minestom.crafting.CraftingGrid;

import java.util.ArrayList;
import java.util.Map;

public final class FireworkStarFadeCraftingRecipe implements CraftingRecipe {
    private static final Map<Material, DyeColor> DYE_COLORS = Map.ofEntries(
            Map.entry(Material.WHITE_DYE, DyeColor.WHITE),
            Map.entry(Material.ORANGE_DYE, DyeColor.ORANGE),
            Map.entry(Material.MAGENTA_DYE, DyeColor.MAGENTA),
            Map.entry(Material.LIGHT_BLUE_DYE, DyeColor.LIGHT_BLUE),
            Map.entry(Material.YELLOW_DYE, DyeColor.YELLOW),
            Map.entry(Material.LIME_DYE, DyeColor.LIME),
            Map.entry(Material.PINK_DYE, DyeColor.PINK),
            Map.entry(Material.GRAY_DYE, DyeColor.GRAY),
            Map.entry(Material.LIGHT_GRAY_DYE, DyeColor.LIGHT_GRAY),
            Map.entry(Material.CYAN_DYE, DyeColor.CYAN),
            Map.entry(Material.PURPLE_DYE, DyeColor.PURPLE),
            Map.entry(Material.BLUE_DYE, DyeColor.BLUE),
            Map.entry(Material.BROWN_DYE, DyeColor.BROWN),
            Map.entry(Material.GREEN_DYE, DyeColor.GREEN),
            Map.entry(Material.RED_DYE, DyeColor.RED),
            Map.entry(Material.BLACK_DYE, DyeColor.BLACK)
    );

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() < 2) {
            return null;
        }

        CraftingGrid.CraftingGridSlot starSlot = null;
        var fadeColors = new ArrayList<RGBLike>();
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var gridSlot : nonEmptySlots) {
            var item = gridSlot.item();
            var material = item.material();

            if (material == Material.FIREWORK_STAR) {
                if (starSlot != null) {
                    return null;
                }
                starSlot = gridSlot;
            } else {
                var dyeColor = DYE_COLORS.get(material);
                if (dyeColor == null) {
                    return null;
                }
                fadeColors.add(dyeColor.fireworkColor());
                usage.addTo(gridSlot.slotIndex(), 1);
            }
        }

        if (starSlot == null || fadeColors.isEmpty()) {
            return null;
        }

        usage.addTo(starSlot.slotIndex(), 1);

        var starItem = starSlot.item();
        var existingExplosion = starItem.get(DataComponents.FIREWORK_EXPLOSION);

        if (existingExplosion == null) {
            return null;
        }

        var newExplosion = new FireworkExplosion(
                existingExplosion.shape(),
                existingExplosion.colors(),
                fadeColors,
                existingExplosion.hasTrail(),
                existingExplosion.hasTwinkle()
        );

        var result = starItem.with(DataComponents.FIREWORK_EXPLOSION, newExplosion);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }
}
