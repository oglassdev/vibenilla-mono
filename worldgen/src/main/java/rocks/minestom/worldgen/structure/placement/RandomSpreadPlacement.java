package rocks.minestom.worldgen.structure.placement;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.structure.StructurePlacement;

/**
 * Places structures on a grid with random offsets within each cell.
 *
 * <p>The world is divided into regions of {@code spacing} chunks. Within each region, exactly one
 * structure can generate, offset randomly from the region corner by up to {@code spacing -
 * separation} chunks.
 *
 * <p>Parameters:
 *
 * <ul>
 *   <li>{@code spacing} - Region size in chunks (distance between potential spawns)
 *   <li>{@code separation} - Minimum distance between structures in chunks
 *   <li>{@code salt} - Unique value to differentiate structure types
 *   <li>{@code spreadType} - Distribution of random offset (linear or triangular)
 *   <li>{@code frequency} - Chance for a placement chunk to actually generate
 *   <li>{@code frequencyReduction} - Random scheme used for the frequency roll
 *   <li>{@code exclusionZone} - Another set's placement that must not start within a chunk range,
 *       or null
 * </ul>
 *
 * @see RandomSpreadType for offset distribution options
 */
public record RandomSpreadPlacement(
        int spacing,
        int separation,
        int salt,
        RandomSpreadType spreadType,
        float frequency,
        FrequencyReduction frequencyReduction,
        @Nullable ExclusionZone exclusionZone)
        implements StructurePlacement {

    /**
     * Vanilla {@code StructurePlacement.ExclusionZone}: forbids a start when the other set has a
     * start chunk within {@code chunkCount} chunks. The other placement is resolved lazily; an
     * unavailable set (already logged by the loader) excludes nothing.
     */
    public record ExclusionZone(Supplier<StructurePlacement> otherPlacement, int chunkCount) {
        boolean forbids(int chunkX, int chunkZ, long seed, boolean legacyRandomSource) {
            var other = this.otherPlacement.get();
            if (other == null) {
                return false;
            }
            for (var x = chunkX - this.chunkCount; x <= chunkX + this.chunkCount; x++) {
                for (var z = chunkZ - this.chunkCount; z <= chunkZ + this.chunkCount; z++) {
                    if (other.isStartChunk(x, z, seed, legacyRandomSource)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public boolean isStartChunk(int chunkX, int chunkZ, long seed, boolean legacyRandomSource) {
        var spacingValue = this.spacing;
        var separationValue = this.separation;
        if (spacingValue <= separationValue) {
            return false;
        }

        var regionX = Math.floorDiv(chunkX, spacingValue);
        var regionZ = Math.floorDiv(chunkZ, spacingValue);
        // Vanilla getPotentialStructureChunk always uses a legacy random
        // (setLargeFeatureWithSalt), regardless of the world's random type.
        var random = new LegacyRandomSource(0L);
        var regionSeed =
                (long) regionX * 341873128712L
                        + (long) regionZ * 132897987541L
                        + seed
                        + (long) this.salt;
        random.setSeed(regionSeed);

        var offsetBound = spacingValue - separationValue;
        var offsetX = this.spreadType.sample(random, offsetBound);
        var offsetZ = this.spreadType.sample(random, offsetBound);
        var startChunkX = regionX * spacingValue + offsetX;
        var startChunkZ = regionZ * spacingValue + offsetZ;
        if (chunkX != startChunkX || chunkZ != startChunkZ) {
            return false;
        }

        // Vanilla applyAdditionalChunkRestrictions: frequency gate
        if (this.frequency < 1.0F
                && !this.frequencyReduction.shouldGenerate(
                        seed, this.salt, chunkX, chunkZ, this.frequency)) {
            return false;
        }

        return this.exclusionZone == null
                || !this.exclusionZone.forbids(chunkX, chunkZ, seed, legacyRandomSource);
    }
}
