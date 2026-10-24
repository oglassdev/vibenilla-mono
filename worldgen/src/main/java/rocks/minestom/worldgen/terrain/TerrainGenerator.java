package rocks.minestom.worldgen.terrain;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.density.ChunkContext;

/**
 * Handles the base terrain generation phase (The "Noise Phase"). This step evaluates the final
 * density for every block and fills the chunk with the default block (solid) or the aquifer's
 * water, lava and air.
 */
public final class TerrainGenerator {
    private final NoiseGeneratorSettingsRuntime settings;
    private Aquifer aquifer;

    public TerrainGenerator(NoiseGeneratorSettingsRuntime settings) {
        this.settings = settings;
    }

    /**
     * The aquifer built for the last {@link #generate(int, int)} call. Carving reuses it (with its
     * position caches) the same way vanilla shares the chunk's NoiseChunk aquifer between the noise
     * fill and the carvers stage.
     */
    public Aquifer aquifer() {
        return this.aquifer;
    }

    /**
     * {@link #generate(int, int, Beardifier)} without structure terrain adaptation - the un-bearded
     * terrain vanilla exposes before the noise stage (structure start height probes, tests).
     */
    public TerrainData generate(int chunkX, int chunkZ) {
        return this.generate(chunkX, chunkZ, Beardifier.EMPTY);
    }

    /**
     * Pure function of the chunk position: computes base terrain (density, aquifers) into a {@link
     * TerrainData} without touching any generation unit, so results can be memoized and neighbor
     * chunks queried during decoration. The beardifier is read by the {@code beardifier} node of
     * the final density.
     */
    public TerrainData generate(int chunkX, int chunkZ, Beardifier beardifier) {
        var startX = chunkX * 16;
        var startZ = chunkZ * 16;
        var sizeZ = 16;

        var minY = this.settings.minY();
        var maxY = this.settings.maxYInclusive();
        var height = maxY - minY + 1;

        var defaultBlock = this.settings.defaultBlock();
        var finalDensity = this.settings.finalDensity();

        var data = TerrainData.create(16, sizeZ, height);
        var surfaceHeights = data.surfaceHeights();
        var waterHeights = data.waterHeights();
        var stoneMask = data.stoneMask();
        var blocks = data.blocks();

        var context = new ChunkContext();
        context.beardifier(beardifier);

        var fluidPicker =
                Aquifer.createGlobalFluidPicker(
                        this.settings.seaLevel(), this.settings.defaultFluid());
        var aquiferDensities = this.settings.aquifers();
        var aquifer =
                aquiferDensities == null
                        ? Aquifer.createDisabled(fluidPicker)
                        : Aquifer.create(
                                context,
                                startX,
                                startZ,
                                aquiferDensities.barrier(),
                                aquiferDensities.fluidLevelFloodedness(),
                                aquiferDensities.fluidLevelSpread(),
                                aquiferDensities.lava(),
                                aquiferDensities.exclusion(),
                                aquiferDensities.surfaceLevel(),
                                this.settings.randomState().aquiferRandom(),
                                minY,
                                height,
                                fluidPicker);
        this.aquifer = aquifer;

        for (var localX = 0; localX < 16; localX++) {
            var blockX = startX + localX;
            for (var localZ = 0; localZ < sizeZ; localZ++) {
                var blockZ = startZ + localZ;
                var surfaceIndex = localX * sizeZ + localZ;
                var maskIndex = surfaceIndex * height;

                for (var blockY = maxY; blockY >= minY; blockY--) {
                    context.setBlock(blockX, blockY, blockZ);
                    var density = finalDensity.compute(context);
                    var yIndex = blockY - minY;

                    // The aquifer decides the substance for non-solid density (air/water/lava,
                    // or null for a pressure barrier turned solid).
                    var state = aquifer.computeSubstance(context, density);
                    if (state == null) {
                        stoneMask[maskIndex + yIndex] = TerrainData.SOLID;
                        blocks[maskIndex + yIndex] = defaultBlock;
                        if (surfaceHeights[surfaceIndex] == Integer.MIN_VALUE) {
                            surfaceHeights[surfaceIndex] = blockY;
                        }
                    } else if (!state.air()) {
                        stoneMask[maskIndex + yIndex] = TerrainData.FLUID;
                        blocks[maskIndex + yIndex] = state;
                        if (aquifer.shouldScheduleFluidUpdate()) {
                            data.fluidTicks().add(new BlockVec(blockX, blockY, blockZ));
                        }
                        if (waterHeights[surfaceIndex] == Integer.MIN_VALUE) {
                            waterHeights[surfaceIndex] = blockY + 1;
                        }
                    }
                }
            }
        }

        return data;
    }
}
