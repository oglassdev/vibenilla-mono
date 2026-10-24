package rocks.minestom.worldgen.structure.netherfossil;

import java.util.List;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.TerrainAdjustment;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;
import rocks.minestom.worldgen.surface.VerticalAnchor;

/**
 * Vanilla {@code NetherFossilStructure}: a single template placed at a position found by sampling a
 * height provider and scanning the raw noise terrain column downward for the first floor (air
 * directly above a solid or soul sand block).
 *
 * @see NetherFossilPlacer for the placement algorithm
 */
public record NetherFossilStructure(
        StructureBiomes biomes,
        VerticalAnchor minHeight,
        VerticalAnchor maxHeight,
        List<Key> templates,
        TerrainAdjustment terrainAdaptation)
        implements Structure {
    @Override
    public TerrainAdjustment terrainAdaptation() {
        return this.terrainAdaptation;
    }

    @Override
    public void place(StructurePlaceContext context) {
        // Nether fossils are assembled and placed by NetherFossilPlacer, which
        // resolves the position and rotation and clips per chunk.
    }
}
