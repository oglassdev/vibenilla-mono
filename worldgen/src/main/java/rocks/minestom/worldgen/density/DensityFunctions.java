package rocks.minestom.worldgen.density;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.noise.BlendedNoise;
import rocks.minestom.worldgen.noise.NormalNoise;
import rocks.minestom.worldgen.noise.SimplexNoise;

/**
 * Collection of density function nodes used to compose terrain, cave, and climate fields. Values
 * are rounded to {@code float} after every node, matching vanilla's float-valued sampler pipeline.
 */
public final class DensityFunctions {
    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();

    private DensityFunctions() {}

    /** Number of per-chunk state slots handed out to {@link Cache} and {@link Interpolated}. */
    public static int slotCount() {
        return NEXT_SLOT.get();
    }

    private static double f(double value) {
        return (float) value;
    }

    /** Evaluates a function at another position without disturbing the caches of the context. */
    static double computeAt(
            DensityFunction function, DensityFunction.Context context, int x, int y, int z) {
        if (context instanceof ChunkContext chunkContext) {
            return chunkContext.sample(function, x, y, z);
        }
        return function.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    public enum Axis {
        X(DensityFunction.AXIS_X),
        Y(DensityFunction.AXIS_Y),
        Z(DensityFunction.AXIS_Z);

        private final int bit;

        Axis(int bit) {
            this.bit = bit;
        }

        public int bit() {
            return this.bit;
        }

        public int choose(int x, int y, int z) {
            return switch (this) {
                case X -> x;
                case Y -> y;
                case Z -> z;
            };
        }
    }

    public enum Tiling {
        CLAMP_TO_EDGE,
        REPEAT,
        MIRRORED_REPEAT
    }

    public enum Metric {
        EUCLIDEAN,
        EUCLIDEAN_SQUARED,
        MANHATTAN,
        CHEBYSHEV;

        double distance(double dx, double dy, double dz) {
            return switch (this) {
                case EUCLIDEAN -> (float) Math.sqrt((float) (dx * dx + dy * dy + dz * dz));
                case EUCLIDEAN_SQUARED -> (float) (dx * dx + dy * dy + dz * dz);
                case MANHATTAN -> Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                case CHEBYSHEV -> Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
            };
        }
    }

    public record Constant(double value) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.value;
        }

        @Override
        public int domainAxes() {
            return 0;
        }
    }

    public record Binary(Op op, DensityFunction left, DensityFunction right)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.op.apply(this.left.compute(context), this.right.compute(context));
        }

        @Override
        public int domainAxes() {
            return this.left.domainAxes() | this.right.domainAxes();
        }

        public enum Op {
            ADD {
                @Override
                double apply(double left, double right) {
                    return (float) (left + right);
                }
            },
            SUB {
                @Override
                double apply(double left, double right) {
                    return (float) (left - right);
                }
            },
            MUL {
                @Override
                double apply(double left, double right) {
                    return (float) (left * right);
                }
            },
            DIV {
                @Override
                double apply(double left, double right) {
                    return (float) (left / right);
                }
            },
            MIN {
                @Override
                double apply(double left, double right) {
                    return Math.min(left, right);
                }
            },
            MAX {
                @Override
                double apply(double left, double right) {
                    return Math.max(left, right);
                }
            };

            abstract double apply(double left, double right);
        }
    }

    public record Pow(DensityFunction base, DensityFunction exponent) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return (float) Math.pow(this.base.compute(context), this.exponent.compute(context));
        }

        @Override
        public int domainAxes() {
            return this.base.domainAxes() | this.exponent.domainAxes();
        }
    }

    public record Clamp(DensityFunction input, double min, double max) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return VMath.clamp(this.input.compute(context), this.min, this.max);
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes();
        }
    }

    public record Lerp(DensityFunction alpha, DensityFunction first, DensityFunction second)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var alphaValue = this.alpha.compute(context);
            if (alphaValue == 0.0) {
                return this.first.compute(context);
            }
            if (alphaValue == 1.0) {
                return this.second.compute(context);
            }
            var firstValue = this.first.compute(context);
            var secondValue = this.second.compute(context);
            return f(firstValue + alphaValue * f(secondValue - firstValue));
        }

        @Override
        public int domainAxes() {
            return this.alpha.domainAxes() | this.first.domainAxes() | this.second.domainAxes();
        }
    }

    public record Round(Type type, DensityFunction input, DensityFunction multiple)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var value = this.input.compute(context);
            var step = this.multiple.compute(context);
            if (step == 0.0) {
                return value;
            }
            return f(this.type.round(f(value / step)) * step);
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes() | this.multiple.domainAxes();
        }

        public enum Type {
            FLOOR,
            ROUND,
            CEIL,
            TRUNCATE;

            double round(double value) {
                return switch (this) {
                    case FLOOR -> Math.floor(value);
                    case ROUND -> Math.round((float) value);
                    case CEIL -> Math.ceil(value);
                    case TRUNCATE -> value > 0.0 ? Math.floor(value) : Math.ceil(value);
                };
            }
        }
    }

    public record Mapped(Type type, DensityFunction input) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return f(this.type.transform(this.input.compute(context)));
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes();
        }

        public enum Type {
            ABS {
                @Override
                public double transform(double value) {
                    return Math.abs(value);
                }
            },
            SQUARE {
                @Override
                public double transform(double value) {
                    return f(value * value);
                }
            },
            CUBE {
                @Override
                public double transform(double value) {
                    return f(f(value * value) * value);
                }
            },
            SQRT {
                @Override
                public double transform(double value) {
                    return Math.sqrt(value);
                }
            },
            HALF_NEGATIVE {
                @Override
                public double transform(double value) {
                    return value > 0.0 ? value : f(value * 0.5F);
                }
            },
            QUARTER_NEGATIVE {
                @Override
                public double transform(double value) {
                    return value > 0.0 ? value : f(value * 0.25F);
                }
            },
            RECIPROCAL {
                @Override
                public double transform(double value) {
                    return 1.0 / value;
                }
            },
            NEGATE {
                @Override
                public double transform(double value) {
                    return -value;
                }
            },
            SQUEEZE {
                @Override
                public double transform(double value) {
                    var clamped = VMath.clamp(value, -1.0, 1.0);
                    return f(f(clamped / 2.0) - f(f(f(clamped * clamped) * clamped) / 24.0));
                }
            },
            LOG {
                @Override
                public double transform(double value) {
                    return Math.log(value);
                }
            },
            SIGN {
                @Override
                public double transform(double value) {
                    return Math.signum(value);
                }
            };

            public abstract double transform(double value);
        }
    }

    public record Gradient(
            Axis axis,
            Tiling tiling,
            int fromCoordinate,
            int toCoordinate,
            double fromValue,
            double toValue)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var coordinate = this.axis.choose(context.blockX(), context.blockY(), context.blockZ());
            var range = this.toCoordinate - this.fromCoordinate;
            var factor = f((this.toValue - this.fromValue) / range);
            return switch (this.tiling) {
                case CLAMP_TO_EDGE -> {
                    var clamped =
                            Math.max(
                                    Math.min(this.fromCoordinate, this.toCoordinate),
                                    Math.min(
                                            coordinate,
                                            Math.max(this.fromCoordinate, this.toCoordinate)));
                    yield f(this.fromValue + f((clamped - this.fromCoordinate) * factor));
                }
                case REPEAT ->
                        f(
                                this.fromValue
                                        + f(
                                                Math.floorMod(
                                                                coordinate - this.fromCoordinate,
                                                                range)
                                                        * factor));
                case MIRRORED_REPEAT -> {
                    var relative = coordinate - this.fromCoordinate;
                    var tile = Math.floorDiv(relative, range);
                    var local = relative - tile * range;
                    yield (tile & 1) == 0
                            ? f(this.fromValue + f(local * factor))
                            : f(this.fromValue + f((range - local) * factor));
                }
            };
        }

        @Override
        public int domainAxes() {
            return this.axis.bit();
        }
    }

    public record Slice(Axis axis, int coordinate, DensityFunction input)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var x = this.axis == Axis.X ? this.coordinate : context.blockX();
            var y = this.axis == Axis.Y ? this.coordinate : context.blockY();
            var z = this.axis == Axis.Z ? this.coordinate : context.blockZ();
            return computeAt(this.input, context, x, y, z);
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes() & ~this.axis.bit();
        }
    }

    public record RangeChoice(
            DensityFunction input,
            double minInclusive,
            double maxExclusive,
            DensityFunction whenInRange,
            DensityFunction whenOutOfRange)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var value = this.input.compute(context);
            if (value >= this.minInclusive && value < this.maxExclusive) {
                return this.whenInRange.compute(context);
            }
            return this.whenOutOfRange.compute(context);
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes()
                    | this.whenInRange.domainAxes()
                    | this.whenOutOfRange.domainAxes();
        }
    }

    public record IntervalSelect(
            DensityFunction input, double[] thresholds, List<DensityFunction> functions)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var value = this.input.compute(context);
            for (var i = 0; i < this.thresholds.length; i++) {
                if (value < this.thresholds[i]) {
                    return this.functions.get(i).compute(context);
                }
            }
            return this.functions.getLast().compute(context);
        }

        @Override
        public int domainAxes() {
            var axes = this.input.domainAxes();
            for (var function : this.functions) {
                axes |= function.domainAxes();
            }
            return axes;
        }
    }

    /** Sampled noise; a {@code null} shift means zero. */
    public record Noise(
            NormalNoise noise,
            double xzScale,
            double yScale,
            DensityFunction shiftX,
            DensityFunction shiftY,
            DensityFunction shiftZ)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var x = (double) context.blockX() * this.xzScale;
            var y = (double) context.blockY() * this.yScale;
            var z = (double) context.blockZ() * this.xzScale;
            if (this.shiftX != null) {
                x += this.shiftX.compute(context);
            }
            if (this.shiftY != null) {
                y += this.shiftY.compute(context);
            }
            if (this.shiftZ != null) {
                z += this.shiftZ.compute(context);
            }
            return f(this.noise.getValue(x, y, z));
        }

        @Override
        public int domainAxes() {
            var axes = AXES_XYZ;
            if (this.yScale == 0.0) {
                axes &= ~AXIS_Y;
            }
            if (this.xzScale == 0.0) {
                axes &= ~(AXIS_X | AXIS_Z);
            }
            if (this.shiftX != null) {
                axes |= this.shiftX.domainAxes();
            }
            if (this.shiftY != null) {
                axes |= this.shiftY.domainAxes();
            }
            if (this.shiftZ != null) {
                axes |= this.shiftZ.domainAxes();
            }
            return axes;
        }
    }

    public record Shift(NormalNoise offsetNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return f(
                    f(
                                    this.offsetNoise.getValue(
                                            (double) context.blockX() * 0.25,
                                            (double) context.blockY() * 0.25,
                                            (double) context.blockZ() * 0.25))
                            * 4.0);
        }
    }

    public record ShiftA(NormalNoise offsetNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return f(
                    f(
                                    this.offsetNoise.getValue(
                                            (double) context.blockX() * 0.25,
                                            0.0,
                                            (double) context.blockZ() * 0.25))
                            * 4.0);
        }

        @Override
        public int domainAxes() {
            return AXIS_X | AXIS_Z;
        }
    }

    public record ShiftB(NormalNoise offsetNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return f(
                    f(
                                    this.offsetNoise.getValue(
                                            (double) context.blockZ() * 0.25,
                                            (double) context.blockX() * 0.25,
                                            0.0))
                            * 4.0);
        }

        @Override
        public int domainAxes() {
            return AXIS_X | AXIS_Z;
        }
    }

    public record BlendAlpha() implements DensityFunction {
        @Override
        public double compute(Context context) {
            return 1.0;
        }

        @Override
        public int domainAxes() {
            return 0;
        }
    }

    public record BlendOffset() implements DensityFunction {
        @Override
        public double compute(Context context) {
            return 0.0;
        }

        @Override
        public int domainAxes() {
            return 0;
        }
    }

    public record BlendDensity(DensityFunction input) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.input.compute(context);
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes();
        }
    }

    /** Structure terrain adaptation; zero unless the context carries a beardifier. */
    public record BeardifierNode() implements DensityFunction {
        @Override
        public double compute(Context context) {
            if (context instanceof ChunkContext chunkContext) {
                return f(
                        chunkContext
                                .beardifier()
                                .compute(context.blockX(), context.blockY(), context.blockZ()));
            }
            return 0.0;
        }
    }

    /**
     * Interpolates {@code input} trilinearly between values sampled at the corners of {@code
     * cellSizeXz}x{@code cellSizeY}x{@code cellSizeXz} cells aligned to the world origin.
     */
    public static final class Interpolated implements DensityFunction {
        private final DensityFunction input;
        private final int cellSizeXz;
        private final int cellSizeY;
        private final int slot;

        public Interpolated(DensityFunction input, int cellSizeXz, int cellSizeY) {
            this.input = input;
            this.cellSizeXz = cellSizeXz;
            this.cellSizeY = cellSizeY;
            this.slot = NEXT_SLOT.getAndIncrement();
        }

        public DensityFunction input() {
            return this.input;
        }

        public int cellSizeXz() {
            return this.cellSizeXz;
        }

        public int cellSizeY() {
            return this.cellSizeY;
        }

        public int slot() {
            return this.slot;
        }

        @Override
        public double compute(Context context) {
            if (context instanceof ChunkContext chunkContext) {
                return chunkContext.interpolatedValue(this);
            }
            var x = context.blockX();
            var y = context.blockY();
            var z = context.blockZ();
            var xInCell = Math.floorMod(x, this.cellSizeXz);
            var yInCell = Math.floorMod(y, this.cellSizeY);
            var zInCell = Math.floorMod(z, this.cellSizeXz);
            var corners = new double[8];
            for (var corner = 0; corner < 8; corner++) {
                corners[corner] =
                        this.input.compute(
                                new SinglePointContext(
                                        x - xInCell + (corner & 1) * this.cellSizeXz,
                                        y - yInCell + ((corner >> 1) & 1) * this.cellSizeY,
                                        z - zInCell + ((corner >> 2) & 1) * this.cellSizeXz));
            }
            return trilinear(
                    (float) xInCell / this.cellSizeXz,
                    (float) yInCell / this.cellSizeY,
                    (float) zInCell / this.cellSizeXz,
                    corners);
        }

        @Override
        public int domainAxes() {
            return this.input.domainAxes();
        }

        /** Corner order: bit 0 = x, bit 1 = y, bit 2 = z. */
        static double trilinear(double alphaX, double alphaY, double alphaZ, double[] corners) {
            var v00 = f(corners[0] + alphaZ * f(corners[4] - corners[0]));
            var v01 = f(corners[2] + alphaZ * f(corners[6] - corners[2]));
            var v10 = f(corners[1] + alphaZ * f(corners[5] - corners[1]));
            var v11 = f(corners[3] + alphaZ * f(corners[7] - corners[3]));
            var v0 = f(v00 + alphaX * f(v10 - v00));
            var v1 = f(v01 + alphaX * f(v11 - v01));
            return f(v0 + alphaY * f(v1 - v0));
        }
    }

    /**
     * Memoization hint: within a chunk, repeated evaluations at positions that agree on the input's
     * domain axes reuse the last value.
     */
    public static final class Cache implements DensityFunction {
        private final DensityFunction input;
        private final int axes;
        private final int slot;

        public Cache(DensityFunction input) {
            this.input = input;
            this.axes = input.domainAxes();
            this.slot = NEXT_SLOT.getAndIncrement();
        }

        public DensityFunction input() {
            return this.input;
        }

        public int slot() {
            return this.slot;
        }

        @Override
        public double compute(Context context) {
            if (context instanceof ChunkContext chunkContext) {
                return chunkContext.cachedValue(this);
            }
            if ((this.axes & AXIS_Y) == 0 && context instanceof ColumnCacheContext column) {
                return column.columnValue(this, this.input);
            }
            return this.input.compute(context);
        }

        int axes() {
            return this.axes;
        }

        @Override
        public int domainAxes() {
            return this.axes;
        }
    }

    public record FindTopSurface(
            DensityFunction density, DensityFunction upperBound, int lowerBound, int cellHeight)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var x = context.blockX();
            var z = context.blockZ();
            var upper = computeAt(this.upperBound, context, x, 0, z);
            var topY = (int) Math.floor(upper / this.cellHeight) * this.cellHeight;
            if (topY <= this.lowerBound) {
                return this.lowerBound;
            }

            for (var blockY = topY; blockY >= this.lowerBound; blockY -= this.cellHeight) {
                if (computeAt(this.density, context, x, blockY, z) > 0.0) {
                    return blockY;
                }
            }
            return this.lowerBound;
        }

        @Override
        public int domainAxes() {
            return (this.density.domainAxes() | this.upperBound.domainAxes()) & ~AXIS_Y;
        }
    }

    public record OldBlendedNoise(BlendedNoise blendedNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.blendedNoise.compute(context.blockX(), context.blockY(), context.blockZ());
        }
    }

    public record DistanceToPoint(int x, int y, int z, Metric metric) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return f(
                    this.metric.distance(
                            this.x - context.blockX(),
                            this.y - context.blockY(),
                            this.z - context.blockZ()));
        }
    }

    /** The End's outer island ring, {@code -100} baseline raised by simplex-noise islands. */
    public record EndOuterIslands(SimplexNoise islandNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            var height =
                    getHeightValue(this.islandNoise, context.blockX() / 8, context.blockZ() / 8);
            return ((double) height - 8.0D) / 128.0D;
        }

        @Override
        public int domainAxes() {
            return AXIS_X | AXIS_Z;
        }

        private static float getHeightValue(SimplexNoise simplexNoise, int blockX, int blockZ) {
            var halfX = blockX / 2;
            var halfZ = blockZ / 2;
            var offsetX = blockX % 2;
            var offsetZ = blockZ % 2;
            var height = -100.0F;

            for (var localX = -12; localX <= 12; localX++) {
                for (var localZ = -12; localZ <= 12; localZ++) {
                    var centerX = (long) (halfX + localX);
                    var centerZ = (long) (halfZ + localZ);
                    if (centerX * centerX + centerZ * centerZ > 4096L
                            && simplexNoise.getValue((double) centerX, (double) centerZ) < -0.9D) {
                        var scale =
                                (Math.abs((float) centerX) * 3439.0F
                                                        + Math.abs((float) centerZ) * 147.0F)
                                                % 13.0F
                                        + 9.0F;
                        var deltaX = (float) (offsetX - localX * 2);
                        var deltaZ = (float) (offsetZ - localZ * 2);
                        var candidate =
                                100.0F
                                        - (float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)
                                                * scale;
                        candidate = (float) VMath.clamp((double) candidate, -100.0D, 80.0D);
                        height = Math.max(height, candidate);
                    }
                }
            }

            return height;
        }
    }

    public sealed interface SplineNode permits SplineConstant, SplineMultipoint {
        float compute(DensityFunction.Context context);

        int domainAxes();
    }

    public record SplineConstant(float value) implements SplineNode {
        @Override
        public float compute(DensityFunction.Context context) {
            return this.value;
        }

        @Override
        public int domainAxes() {
            return 0;
        }
    }

    public record SplineMultipoint(
            DensityFunction coordinate,
            float[] locations,
            List<SplineNode> values,
            float[] derivatives)
            implements SplineNode {
        @Override
        public float compute(DensityFunction.Context context) {
            var coordinateValue = (float) this.coordinate.compute(context);
            var intervalStart = findIntervalStart(this.locations, coordinateValue);
            var lastIndex = this.locations.length - 1;
            if (intervalStart < 0) {
                return linearExtend(
                        coordinateValue,
                        this.locations,
                        this.values.getFirst().compute(context),
                        this.derivatives,
                        0);
            }
            if (intervalStart == lastIndex) {
                return linearExtend(
                        coordinateValue,
                        this.locations,
                        this.values.get(lastIndex).compute(context),
                        this.derivatives,
                        lastIndex);
            }

            var startLocation = this.locations[intervalStart];
            var endLocation = this.locations[intervalStart + 1];
            var delta = (coordinateValue - startLocation) / (endLocation - startLocation);
            var startValue = this.values.get(intervalStart).compute(context);
            var endValue = this.values.get(intervalStart + 1).compute(context);
            var startDerivative = this.derivatives[intervalStart];
            var endDerivative = this.derivatives[intervalStart + 1];
            var p = startDerivative * (endLocation - startLocation) - (endValue - startValue);
            var q = -endDerivative * (endLocation - startLocation) + (endValue - startValue);
            // Vanilla evaluates splines in float; double-lerp-then-cast drifts by ULPs
            return lerp(delta, startValue, endValue) + delta * (1.0F - delta) * lerp(delta, p, q);
        }

        @Override
        public int domainAxes() {
            var axes = this.coordinate.domainAxes();
            for (var value : this.values) {
                axes |= value.domainAxes();
            }
            return axes;
        }

        private static float lerp(float delta, float start, float end) {
            return start + delta * (end - start);
        }

        private static float linearExtend(
                float coordinate, float[] locations, float value, float[] derivatives, int index) {
            var derivative = derivatives[index];
            if (derivative == 0.0F) {
                return value;
            }
            return value + derivative * (coordinate - locations[index]);
        }

        private static int findIntervalStart(float[] locations, float coordinate) {
            return VMath.binarySearch(0, locations.length, index -> coordinate < locations[index])
                    - 1;
        }
    }

    public record Spline(SplineNode spline) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.spline.compute(context);
        }

        @Override
        public int domainAxes() {
            return this.spline.domainAxes();
        }
    }
}
