package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import rocks.minestom.crafting.CraftingGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BannerDuplicateCraftingRecipe implements CraftingRecipe {
    private static final Set<Material> BANNER_MATERIALS = Set.of(
            Material.WHITE_BANNER, Material.ORANGE_BANNER, Material.MAGENTA_BANNER, Material.LIGHT_BLUE_BANNER,
            Material.YELLOW_BANNER, Material.LIME_BANNER, Material.PINK_BANNER, Material.GRAY_BANNER,
            Material.LIGHT_GRAY_BANNER, Material.CYAN_BANNER, Material.PURPLE_BANNER, Material.BLUE_BANNER,
            Material.BROWN_BANNER, Material.GREEN_BANNER, Material.RED_BANNER, Material.BLACK_BANNER
    );

    @Override
    public CraftingRecipeMatch match(CraftingGrid craftingGrid) {
        var nonEmptySlots = craftingGrid.nonEmptySlots();

        if (nonEmptySlots.size() != 2) {
            return null;
        }

        Material bannerColor = null;
        CraftingGrid.CraftingGridSlot patternedBannerSlot = null;
        CraftingGrid.CraftingGridSlot blankBannerSlot = null;

        for (var gridSlot : nonEmptySlots) {
            var item = gridSlot.item();

            if (!BANNER_MATERIALS.contains(item.material())) {
                return null;
            }

            if (bannerColor == null) {
                bannerColor = item.material();
            } else if (bannerColor != item.material()) {
                return null;
            }

            var patterns = item.get(DataComponents.BANNER_PATTERNS);
            var layerCount = patterns != null ? patterns.layers().size() : 0;

            if (layerCount > 6) {
                return null;
            }

            if (layerCount > 0) {
                if (patternedBannerSlot != null) {
                    return null;
                }
                patternedBannerSlot = gridSlot;
            } else {
                if (blankBannerSlot != null) {
                    return null;
                }
                blankBannerSlot = gridSlot;
            }
        }

        if (patternedBannerSlot == null || blankBannerSlot == null) {
            return null;
        }

        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);
        usage.addTo(patternedBannerSlot.slotIndex(), 1);
        usage.addTo(blankBannerSlot.slotIndex(), 1);

        var result = patternedBannerSlot.item().withAmount(1);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }

    @Override
    public List<ItemStack> getRemainingItems(CraftingGrid craftingGrid) {
        var remainders = new ArrayList<ItemStack>(craftingGrid.size());

        for (var gridSlot : craftingGrid.slots()) {
            var slotItem = gridSlot.item();

            if (!slotItem.isAir() && BANNER_MATERIALS.contains(slotItem.material())) {
                var patterns = slotItem.get(DataComponents.BANNER_PATTERNS);
                if (patterns != null && !patterns.layers().isEmpty()) {
                    remainders.add(slotItem.withAmount(1));
                    continue;
                }
            }

            var useRemainder = slotItem.get(DataComponents.USE_REMAINDER);
            remainders.add(useRemainder == null ? ItemStack.AIR : useRemainder);
        }

        return remainders;
    }
}
