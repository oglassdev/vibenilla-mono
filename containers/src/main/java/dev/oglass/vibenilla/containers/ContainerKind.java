package dev.oglass.vibenilla.containers;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.instance.block.Block;
import net.minestom.server.sound.SoundEvent;

enum ContainerKind {
    CHEST(Block.CHEST, true, SoundEvent.BLOCK_CHEST_OPEN, SoundEvent.BLOCK_CHEST_CLOSE),
    TRAPPED_CHEST(
            Block.TRAPPED_CHEST, true, SoundEvent.BLOCK_CHEST_OPEN, SoundEvent.BLOCK_CHEST_CLOSE),
    BARREL(Block.BARREL, false, SoundEvent.BLOCK_BARREL_OPEN, SoundEvent.BLOCK_BARREL_CLOSE);

    static final int SLOTS_PER_BLOCK = 27;

    private final Block block;
    private final boolean chest;
    private final SoundEvent openSound;
    private final SoundEvent closeSound;

    ContainerKind(Block block, boolean chest, SoundEvent openSound, SoundEvent closeSound) {
        this.block = block;
        this.chest = chest;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    Block block() {
        return block;
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
