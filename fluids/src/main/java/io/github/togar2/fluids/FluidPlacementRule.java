package io.github.togar2.fluids;

import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Schedules fluid ticks for blocks holding a fluid and waterlogs waterloggable blocks placed into a
 * water source. All placement behavior of the block is delegated to the rule that was registered
 * for it beforehand, if any.
 */
public class FluidPlacementRule extends BlockPlacementRule {
    private final @Nullable BlockPlacementRule delegate;

    public FluidPlacementRule(@NotNull Block block) {
        this(block, null);
    }

    public FluidPlacementRule(@NotNull Block block, @Nullable BlockPlacementRule delegate) {
        super(block);
        this.delegate = delegate;
    }

    @Override
    public @NotNull Block blockUpdate(@NotNull UpdateState updateState) {
        var updated =
                delegate == null
                        ? super.blockUpdate(updateState)
                        : delegate.blockUpdate(updateState);
        if (holdsFluid(updated)) {
            MinestomFluids.scheduleTick(
                    (Instance) updateState.instance(),
                    updateState.blockPosition().asBlockVec(),
                    FluidState.of(updated));
        }
        return updated;
    }

    @Override
    public @Nullable Block blockPlace(@NotNull PlacementState placementState) {
        var block = delegate == null ? placementState.block() : delegate.blockPlace(placementState);
        if (block == null) return null;

        var instance = (Instance) placementState.instance();
        var position = placementState.placePosition().asBlockVec();

        if (FluidState.canBeWaterlogged(block)) {
            var replaced = instance.getBlock(position);
            if (replaced.liquid() && FluidState.of(replaced).isWater()) {
                block = FluidState.setWaterlogged(block, FluidState.isSource(replaced));
            }
        }

        if (holdsFluid(block)) {
            MinestomFluids.scheduleTick(instance, position, FluidState.of(block));
        }
        return block;
    }

    @Override
    public boolean isSelfReplaceable(@NotNull Replacement replacement) {
        return delegate == null
                ? super.isSelfReplaceable(replacement)
                : delegate.isSelfReplaceable(replacement);
    }

    @Override
    public int maxUpdateDistance() {
        return delegate == null ? super.maxUpdateDistance() : delegate.maxUpdateDistance();
    }

    private static boolean holdsFluid(Block block) {
        var waterlogged = block.getProperty("waterlogged");
        return waterlogged == null || waterlogged.equals("true");
    }
}
