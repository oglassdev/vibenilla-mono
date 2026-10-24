package rocks.minestom.worldgen.density;

/**
 * A scalar field sampled across the world to drive terrain and climate decisions. Density functions
 * are the fundamental building blocks for landmasses, caves, biome climate parameters, and the
 * final solid/air decision.
 */
public interface DensityFunction {
    int AXIS_X = 1;
    int AXIS_Y = 2;
    int AXIS_Z = 4;
    int AXES_XYZ = AXIS_X | AXIS_Y | AXIS_Z;

    double compute(Context context);

    /** Bit set of the coordinates the value depends on. */
    default int domainAxes() {
        return AXES_XYZ;
    }

    interface Context {
        int blockX();

        int blockY();

        int blockZ();
    }

    record SinglePointContext(int blockX, int blockY, int blockZ) implements Context {}
}
