package dev.oglass.vibenilla.ticking;

import org.jetbrains.annotations.Nullable;

/** The vanilla random tick handlers. */
public final class VanillaRandomTicks {
    private VanillaRandomTicks() {}

    /** Registers the vanilla handlers; saplings only advance their stage. */
    public static void register(RandomTicks ticks) {
        register(ticks, null);
    }

    /**
     * Registers the vanilla handlers. Saplings grow into trees from the given world generation
     * data.
     *
     * <ul>
     *   <li>crops: wheat, carrots, potatoes, beetroots
     *   <li>farmland moisture
     *   <li>saplings
     *   <li>leaf decay, announced by {@link LeavesDecayEvent}
     *   <li>grass and mycelium spread and decay
     *   <li>sugar cane and cactus growth
     *   <li>fire spread and burn-out, and lava starting fires
     * </ul>
     */
    public static void register(RandomTicks ticks, @Nullable WorldgenTrees trees) {
        Crops.register(ticks);
        Farmland.register(ticks);
        Saplings.register(ticks, trees);
        Leaves.register(ticks);
        SpreadingGrass.register(ticks);
        GrowingPlants.register(ticks);
        Fire.register(ticks);
        Lava.register(ticks);
    }
}
