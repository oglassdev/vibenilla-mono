package net.goldenstack.loot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.Test;

class EnchantmentUtilsTest {

    @Test
    void entityLevelsOnlyCountSlotsWhereTheEnchantmentIsActive() {
        MinecraftServer.init();

        var entity = new LivingEntity(EntityType.ZOMBIE);
        var enchanted =
                ItemStack.of(Material.DIAMOND_SWORD)
                        .with(
                                DataComponents.ENCHANTMENTS,
                                new EnchantmentList(Map.of(Enchantment.LOOTING, 3)));

        entity.setEquipment(EquipmentSlot.BOOTS, enchanted);
        assertEquals(0, EnchantmentUtils.level(entity, Enchantment.LOOTING));

        entity.setEquipment(EquipmentSlot.MAIN_HAND, enchanted);
        assertEquals(3, EnchantmentUtils.level(entity, Enchantment.LOOTING));
    }
}
