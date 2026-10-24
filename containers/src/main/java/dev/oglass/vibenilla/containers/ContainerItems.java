package dev.oglass.vibenilla.containers;

import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;

/** Reads and writes container contents in the vanilla block entity {@code Items} format. */
final class ContainerItems {
    private static final String ITEMS = "Items";

    private ContainerItems() {}

    static ItemStack[] read(Block block, int size) {
        var items = new ItemStack[size];
        Arrays.fill(items, ItemStack.AIR);

        for (var tag : block.nbtOrEmpty().getList(ITEMS, BinaryTagTypes.COMPOUND)) {
            var entry = (CompoundBinaryTag) tag;
            var slot = entry.getByte("Slot") & 0xFF;

            if (slot < size) {
                items[slot] = ItemStack.fromItemNBT(entry);
            }
        }

        return items;
    }

    static Block write(Block block, List<ItemStack> items) {
        var list = ListBinaryTag.builder(BinaryTagTypes.COMPOUND);

        for (var slot = 0; slot < items.size(); slot++) {
            var item = items.get(slot);

            if (!item.isAir()) {
                list.add(item.toItemNBT().putByte("Slot", (byte) slot));
            }
        }

        var nbt = block.nbtOrEmpty();
        var written = list.build();
        return block.withNbt(written.size() == 0 ? nbt.remove(ITEMS) : nbt.put(ITEMS, written));
    }
}
