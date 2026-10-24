package dev.oglass.vibenilla.ticking;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/**
 * Called before decayed leaves are removed, while the leaves are still in the world. Listeners are
 * expected to drop the leaves' loot.
 */
public final class LeavesDecayEvent implements InstanceEvent, CancellableEvent {
    private final Instance instance;
    private final BlockVec position;
    private final Block block;
    private boolean cancelled;

    public LeavesDecayEvent(Instance instance, BlockVec position, Block block) {
        this.instance = instance;
        this.position = position;
        this.block = block;
    }

    @Override
    public Instance getInstance() {
        return this.instance;
    }

    public BlockVec getPosition() {
        return this.position;
    }

    public Block getBlock() {
        return this.block;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
