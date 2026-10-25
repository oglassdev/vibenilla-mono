package dev.oglass.vibenilla.containers;

import java.util.List;
import net.minestom.server.instance.block.Block;

/** The blocks of the {@code minecraft:copper_chests} tag. */
final class CopperChests {
    static final List<Block> BLOCKS =
            List.of(
                    Block.COPPER_CHEST,
                    Block.EXPOSED_COPPER_CHEST,
                    Block.WEATHERED_COPPER_CHEST,
                    Block.OXIDIZED_COPPER_CHEST,
                    Block.WAXED_COPPER_CHEST,
                    Block.WAXED_EXPOSED_COPPER_CHEST,
                    Block.WAXED_WEATHERED_COPPER_CHEST,
                    Block.WAXED_OXIDIZED_COPPER_CHEST);

    private CopperChests() {}

    static boolean is(Block block) {
        return BLOCKS.stream().anyMatch(block::compare);
    }
}
