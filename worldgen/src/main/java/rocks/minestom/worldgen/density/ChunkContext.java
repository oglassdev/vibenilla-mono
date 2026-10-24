package rocks.minestom.worldgen.density;

import java.util.Arrays;
import rocks.minestom.worldgen.terrain.Beardifier;

/**
 * Density-function context for evaluating many positions of one chunk. Holds the per-chunk state of
 * {@link DensityFunctions.Cache} memoization and {@link DensityFunctions.Interpolated} corner
 * values, and the {@link Beardifier} that structure terrain adaptation reads. Not thread-safe.
 */
public final class ChunkContext implements DensityFunction.Context {
    private InterpolationState[] interpolations = new InterpolationState[0];
    private CacheState[] caches = new CacheState[0];
    private Beardifier beardifier = Beardifier.EMPTY;

    private int blockX;
    private int blockY;
    private int blockZ;

    public void setBlock(int blockX, int blockY, int blockZ) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    public Beardifier beardifier() {
        return this.beardifier;
    }

    public void beardifier(Beardifier beardifier) {
        this.beardifier = beardifier;
    }

    @Override
    public int blockX() {
        return this.blockX;
    }

    @Override
    public int blockY() {
        return this.blockY;
    }

    @Override
    public int blockZ() {
        return this.blockZ;
    }

    /** Evaluates {@code function} at the given position, then restores the current position. */
    public double sample(DensityFunction function, int x, int y, int z) {
        var previousX = this.blockX;
        var previousY = this.blockY;
        var previousZ = this.blockZ;
        this.setBlock(x, y, z);
        try {
            return function.compute(this);
        } finally {
            this.setBlock(previousX, previousY, previousZ);
        }
    }

    double cachedValue(DensityFunctions.Cache cache) {
        var slot = cache.slot();
        if (slot >= this.caches.length) {
            this.caches = Arrays.copyOf(this.caches, DensityFunctions.slotCount());
        }
        var state = this.caches[slot];
        if (state == null) {
            state = new CacheState();
            this.caches[slot] = state;
        }

        var axes = cache.axes();
        var x = (axes & DensityFunction.AXIS_X) != 0 ? this.blockX : 0;
        var y = (axes & DensityFunction.AXIS_Y) != 0 ? this.blockY : 0;
        var z = (axes & DensityFunction.AXIS_Z) != 0 ? this.blockZ : 0;
        if (state.hasValue && state.x == x && state.y == y && state.z == z) {
            return state.value;
        }

        var value = cache.input().compute(this);
        state.hasValue = true;
        state.x = x;
        state.y = y;
        state.z = z;
        state.value = value;
        return value;
    }

    double interpolatedValue(DensityFunctions.Interpolated interpolated) {
        var slot = interpolated.slot();
        if (slot >= this.interpolations.length) {
            this.interpolations = Arrays.copyOf(this.interpolations, DensityFunctions.slotCount());
        }
        var state = this.interpolations[slot];
        if (state == null) {
            state = new InterpolationState();
            this.interpolations[slot] = state;
        }

        var sizeXz = interpolated.cellSizeXz();
        var sizeY = interpolated.cellSizeY();
        var cellX = Math.floorDiv(this.blockX, sizeXz);
        var cellY = Math.floorDiv(this.blockY, sizeY);
        var cellZ = Math.floorDiv(this.blockZ, sizeXz);
        var inCellX = this.blockX - cellX * sizeXz;
        var inCellY = this.blockY - cellY * sizeY;
        var inCellZ = this.blockZ - cellZ * sizeXz;

        var cellKey = pack(cellX, cellY, cellZ);
        if (state.cellKey != cellKey || !state.hasCell) {
            state.cellKey = cellKey;
            state.hasCell = true;
            state.filled = 0;
        }

        var needX = inCellX != 0;
        var needY = inCellY != 0;
        var needZ = inCellZ != 0;
        var corners = state.corners;
        var work = state.work;
        for (var corner = 0; corner < 8; corner++) {
            var effective =
                    ((corner & 1) != 0 && needX ? 1 : 0)
                            | ((corner & 2) != 0 && needY ? 2 : 0)
                            | ((corner & 4) != 0 && needZ ? 4 : 0);
            if ((state.filled & (1 << effective)) == 0) {
                corners[effective] =
                        this.cornerValue(interpolated, state, cellX, cellY, cellZ, effective);
                state.filled |= 1 << effective;
            }
            work[corner] = corners[effective];
        }

        return DensityFunctions.Interpolated.trilinear(
                inCellX * (1.0F / sizeXz),
                inCellY * (1.0F / sizeY),
                inCellZ * (1.0F / sizeXz),
                work);
    }

    private double cornerValue(
            DensityFunctions.Interpolated interpolated,
            InterpolationState state,
            int cellX,
            int cellY,
            int cellZ,
            int corner) {
        var cornerX = cellX + (corner & 1);
        var cornerY = cellY + ((corner >> 1) & 1);
        var cornerZ = cellZ + ((corner >> 2) & 1);
        var key = pack(cornerX, cornerY, cornerZ);
        var cached = state.cornerCache.get(key);
        if (!Double.isNaN(cached)) {
            return cached;
        }

        var value =
                this.sample(
                        interpolated.input(),
                        cornerX * interpolated.cellSizeXz(),
                        cornerY * interpolated.cellSizeY(),
                        cornerZ * interpolated.cellSizeXz());
        state.cornerCache.put(key, value);
        return value;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x3FFFFF) << 20) | (long) (z & 0xFFFFF);
    }

    private static final class CacheState {
        private boolean hasValue;
        private int x;
        private int y;
        private int z;
        private double value;
    }

    private static final class InterpolationState {
        private final double[] corners = new double[8];
        private final double[] work = new double[8];
        private final CornerCache cornerCache = new CornerCache();
        private boolean hasCell;
        private long cellKey;
        private int filled;
    }

    /** Open-addressing {@code long -> double} map; absent keys read as {@link Double#NaN}. */
    private static final class CornerCache {
        private long[] keys = new long[64];
        private double[] values = new double[64];
        private boolean[] occupied = new boolean[64];
        private int size;

        double get(long key) {
            var mask = this.keys.length - 1;
            var index = mix(key) & mask;
            while (this.occupied[index]) {
                if (this.keys[index] == key) {
                    return this.values[index];
                }
                index = (index + 1) & mask;
            }
            return Double.NaN;
        }

        void put(long key, double value) {
            if (this.size * 10 >= this.keys.length * 7) {
                this.grow();
            }
            this.insert(key, value);
        }

        private void insert(long key, double value) {
            var mask = this.keys.length - 1;
            var index = mix(key) & mask;
            while (this.occupied[index]) {
                if (this.keys[index] == key) {
                    this.values[index] = value;
                    return;
                }
                index = (index + 1) & mask;
            }
            this.occupied[index] = true;
            this.keys[index] = key;
            this.values[index] = value;
            this.size++;
        }

        private void grow() {
            var oldKeys = this.keys;
            var oldValues = this.values;
            var oldOccupied = this.occupied;
            this.keys = new long[oldKeys.length * 2];
            this.values = new double[oldKeys.length * 2];
            this.occupied = new boolean[oldKeys.length * 2];
            this.size = 0;
            for (var index = 0; index < oldKeys.length; index++) {
                if (oldOccupied[index]) {
                    this.insert(oldKeys[index], oldValues[index]);
                }
            }
        }

        private static int mix(long value) {
            value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
            value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
            return (int) (value ^ (value >>> 33));
        }
    }
}
