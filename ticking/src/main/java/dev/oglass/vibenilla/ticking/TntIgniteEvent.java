package dev.oglass.vibenilla.ticking;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.Instance;

/**
 * Called when fire burns a TNT block, after the block has been replaced. Listeners are expected to
 * prime the TNT; without one the TNT is simply gone.
 */
public final class TntIgniteEvent implements InstanceEvent {
    private final Instance instance;
    private final BlockVec position;

    public TntIgniteEvent(Instance instance, BlockVec position) {
        this.instance = instance;
        this.position = position;
    }

    @Override
    public Instance getInstance() {
        return this.instance;
    }

    public BlockVec getPosition() {
        return this.position;
    }
}
