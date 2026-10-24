package rocks.minestom.worldgen.carver;

/**
 * Receives the positions a carver cuts out. Carvers only decide which blocks are carved; what a
 * carved block turns into is the output consumer's business.
 */
public interface CarverOutput {
    /** Lowest Y a carver may reach; blocks at or below it are never carved. */
    int minY();

    /** Highest Y a carver may reach. */
    int maxY();

    /** Marks the block at the chunk-local column (xIndex, zIndex) and world Y as carved. */
    void carve(int xIndex, int worldY, int zIndex);
}
