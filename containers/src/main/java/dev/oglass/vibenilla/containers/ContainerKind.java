package dev.oglass.vibenilla.containers;

import java.util.List;
import java.util.stream.Stream;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.instance.block.Block;
import net.minestom.server.sound.SoundEvent;

enum ContainerKind {
    CHEST(
            Block.CHEST,
            CopperChests.BLOCKS,
            true,
            SoundEvent.BLOCK_CHEST_OPEN,
            SoundEvent.BLOCK_CHEST_CLOSE),
    TRAPPED_CHEST(
            Block.TRAPPED_CHEST,
            List.of(),
            true,
            SoundEvent.BLOCK_CHEST_OPEN,
            SoundEvent.BLOCK_CHEST_CLOSE),
    BARREL(
            Block.BARREL,
            List.of(),
            false,
            SoundEvent.BLOCK_BARREL_OPEN,
            SoundEvent.BLOCK_BARREL_CLOSE);

    static final int SLOTS_PER_BLOCK = 27;

    private final Block block;
    private final List<Block> variants;
    private final boolean chest;
    private final SoundEvent openSound;
    private final SoundEvent closeSound;

    ContainerKind(
            Block block,
            List<Block> variants,
            boolean chest,
            SoundEvent openSound,
            SoundEvent closeSound) {
        this.block = block;
        this.variants = variants;
        this.chest = chest;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    Block block() {
        return block;
    }

    /** Every block placed with this kind's handler, which is registered under {@link #key()}. */
    List<Block> blocks() {
        return Stream.concat(Stream.of(block), variants.stream()).toList();
    }

    boolean matches(Block candidate) {
        return candidate.compare(block) || variants.stream().anyMatch(candidate::compare);
    }

    Key key() {
        return block.key();
    }

    boolean isChest() {
        return chest;
    }

    String titleKey(boolean doubleChest) {
        if (!chest) {
            return "container.barrel";
        }
        return doubleChest ? "container.chestDouble" : "container.chest";
    }

    Sound openSound(float pitch) {
        return Sound.sound(openSound, Sound.Source.BLOCK, 0.5F, pitch);
    }

    Sound closeSound(float pitch) {
        return Sound.sound(closeSound, Sound.Source.BLOCK, 0.5F, pitch);
    }
}
