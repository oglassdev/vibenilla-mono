package dev.oglass.vibenilla.ticking;

import java.nio.file.Path;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** The vanilla random tick handlers. */
public final class VanillaRandomTicks {
    private VanillaRandomTicks() {}

    /**
     * Registers the vanilla handlers without vanilla data: saplings only advance their stage and no
     * biome burns out fire faster.
     */
    public static void register(RandomTicks ticks) {
        register(ticks, null, Set.of());
    }

    /**
     * Registers the vanilla handlers. Saplings grow into the trees of the world generation data and
     * the biomes that burn out fire faster are read from it.
     *
     * @param dataRoot the directory holding the vanilla {@code data} folder
     *     <ul>
     *       <li>crops: wheat, carrots, potatoes, beetroots
     *       <li>farmland moisture
     *       <li>saplings
     *       <li>leaf decay, announced by {@link LeavesDecayEvent}
     *       <li>grass and mycelium spread and decay
     *       <li>sugar cane and cactus growth
     *       <li>fire spread and burn-out, announced by {@link TntIgniteEvent} for TNT, and lava
     *           starting fires
     *     </ul>
     */
    public static void register(RandomTicks ticks, Path dataRoot) {
        register(ticks, new WorldgenTrees(dataRoot), Fire.humidBiomes(dataRoot));
    }

    private static void register(
            RandomTicks ticks, @Nullable WorldgenTrees trees, Set<String> humidBiomes) {
        Crops.register(ticks);
        Farmland.register(ticks);
        Saplings.register(ticks, trees);
        Leaves.register(ticks);
        SpreadingGrass.register(ticks);
        GrowingPlants.register(ticks);
        Lava.register(ticks, Fire.register(ticks, humidBiomes));
    }
}
