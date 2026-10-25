package dev.oglass.vibenilla.containers;

import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Reads and writes container contents and names in the vanilla block entity format. */
final class ContainerItems {
    private static final String ITEMS = "Items";

    private ContainerItems() {}

    static @Nullable Component customName(Block block) {
        var name = block.nbtOrEmpty().get("CustomName");
        return name == null ? null : Codec.COMPONENT.decode(Transcoder.NBT, name).orElse(null);
    }

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
