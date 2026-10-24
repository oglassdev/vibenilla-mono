package dev.oglass.vibenilla.containers;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.Nullable;

/** Gives placed containers their handler while keeping the rule registered before it. */
final class ContainerPlacementRule extends BlockPlacementRule {
    private final @Nullable BlockPlacementRule delegate;
    private final ContainerHandler handler;

    ContainerPlacementRule(
            Block block, @Nullable BlockPlacementRule delegate, ContainerHandler handler) {
        super(block);
        this.delegate = delegate;
        this.handler = handler;
    }

    @Override
    public @Nullable Block blockPlace(PlacementState placementState) {
        var placed =
                delegate == null ? placementState.block() : delegate.blockPlace(placementState);
        return placed == null ? null : placed.withHandler(handler);
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        return delegate == null
                ? super.blockUpdate(updateState)
                : delegate.blockUpdate(updateState);
    }

    @Override
    public boolean isSelfReplaceable(Replacement replacement) {
        return delegate == null
                ? super.isSelfReplaceable(replacement)
                : delegate.isSelfReplaceable(replacement);
    }

    @Override
    public int maxUpdateDistance() {
        return delegate == null ? super.maxUpdateDistance() : delegate.maxUpdateDistance();
    }
}
