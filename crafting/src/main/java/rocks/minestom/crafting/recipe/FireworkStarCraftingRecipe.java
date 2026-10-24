package rocks.minestom.crafting.recipe;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import rocks.minestom.crafting.CraftingGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FireworkStarCraftingRecipe implements CraftingRecipe {
    private static final Map<Material, FireworkExplosion.Shape> SHAPE_BY_ITEM = Map.of(
            Material.FIRE_CHARGE, FireworkExplosion.Shape.LARGE_BALL,
            Material.FEATHER, FireworkExplosion.Shape.BURST,
            Material.GOLD_NUGGET, FireworkExplosion.Shape.STAR,
            Material.SKELETON_SKULL, FireworkExplosion.Shape.CREEPER,
            Material.WITHER_SKELETON_SKULL, FireworkExplosion.Shape.CREEPER,
            Material.CREEPER_HEAD, FireworkExplosion.Shape.CREEPER,
            Material.PLAYER_HEAD, FireworkExplosion.Shape.CREEPER,
            Material.DRAGON_HEAD, FireworkExplosion.Shape.CREEPER,
            Material.ZOMBIE_HEAD, FireworkExplosion.Shape.CREEPER,
            Material.PIGLIN_HEAD, FireworkExplosion.Shape.CREEPER
    );

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

        var hasGunpowder = false;
        var hasShape = false;
        var hasTrail = false;
        var hasTwinkle = false;
        var colors = new ArrayList<RGBLike>();
        var usage = new Int2IntOpenHashMap();
        usage.defaultReturnValue(0);

        for (var gridSlot : nonEmptySlots) {
            var item = gridSlot.item();
            var material = item.material();

            if (SHAPE_BY_ITEM.containsKey(material)) {
                if (hasShape) {
                    return null;
                }
                hasShape = true;
                usage.addTo(gridSlot.slotIndex(), 1);
            } else if (material == Material.GLOWSTONE_DUST) {
                if (hasTwinkle) {
                    return null;
                }
                hasTwinkle = true;
                usage.addTo(gridSlot.slotIndex(), 1);
            } else if (material == Material.DIAMOND) {
                if (hasTrail) {
                    return null;
                }
                hasTrail = true;
                usage.addTo(gridSlot.slotIndex(), 1);
            } else if (material == Material.GUNPOWDER) {
                if (hasGunpowder) {
                    return null;
                }
                hasGunpowder = true;
                usage.addTo(gridSlot.slotIndex(), 1);
            } else {
                var dyeColor = DYE_COLORS.get(material);
                if (dyeColor == null) {
                    return null;
                }
                colors.add(dyeColor.fireworkColor());
                usage.addTo(gridSlot.slotIndex(), 1);
            }
        }

        if (!hasGunpowder || colors.isEmpty()) {
            return null;
        }

        var shape = FireworkExplosion.Shape.SMALL_BALL;

        for (var gridSlot : nonEmptySlots) {
            var shapeFromItem = SHAPE_BY_ITEM.get(gridSlot.item().material());
            if (shapeFromItem != null) {
                shape = shapeFromItem;
                break;
            }
        }

        var explosion = new FireworkExplosion(shape, colors, List.of(), hasTrail, hasTwinkle);
        var result = ItemStack.of(Material.FIREWORK_STAR).with(DataComponents.FIREWORK_EXPLOSION, explosion);

        return new CraftingRecipeMatch(this, result, usage, craftingGrid);
    }
}
