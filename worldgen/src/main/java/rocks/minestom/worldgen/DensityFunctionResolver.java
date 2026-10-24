package rocks.minestom.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.density.DensityFunctions;
import rocks.minestom.worldgen.noise.BlendedNoise;
import rocks.minestom.worldgen.noise.SimplexNoise;
import rocks.minestom.worldgen.random.LegacyRandomSource;

public final class DensityFunctionResolver {
    private final DataPack dataPack;
    private final RandomState randomState;
    private final Map<Key, DensityFunction> densityFunctionCache;
    private final Codec<DensityFunction> densityCodec;
    private final ThreadLocal<ArrayList<Key>> resolutionStack;

    public DensityFunctionResolver(DataPack dataPack, RandomState randomState) {
        this.dataPack = dataPack;
        this.randomState = randomState;
        this.densityFunctionCache = new HashMap<>();
        this.densityCodec = this.createCodec();
        this.resolutionStack = ThreadLocal.withInitial(ArrayList::new);
    }

    public Codec<DensityFunction> codec() {
        return this.densityCodec;
    }

    private Codec<DensityFunction> createCodec() {
        return Codec.Recursive(
                self -> {
                    var constantCodec =
                            Codec.DOUBLE.transform(
                                    value ->
                                            (DensityFunction)
                                                    new DensityFunctions.Constant(
                                                            (float) value.doubleValue()),
                                    function -> {
                                        throw new UnsupportedOperationException(
                                                "Encoding is not supported");
                                    });

                    var referenceCodec =
                            Codec.KEY.transform(
                                    this::resolveReference,
                                    function -> {
                                        throw new UnsupportedOperationException(
                                                "Encoding is not supported");
                                    });

                    Codec<DensityFunction> unionCodec =
                            Codec.KEY.<DensityFunction>unionType(
                                    "type",
                                    type -> this.structForType(type, self),
                                    function -> {
                                        throw new UnsupportedOperationException(
                                                "Encoding is not supported");
                                    });

                    return new Codec<>() {
                        @Override
                        public <D> Result<DensityFunction> decode(Transcoder<D> coder, D value) {
                            var mapResult = coder.getMap(value);

                            if (mapResult instanceof Result.Ok) {
                                return unionCodec.decode(coder, value);
                            }

                            var doubleResult = coder.getDouble(value);

                            if (doubleResult instanceof Result.Ok) {
                                return constantCodec.decode(coder, value);
                            }

                            var stringResult = coder.getString(value);

                            if (stringResult instanceof Result.Ok) {
                                return referenceCodec.decode(coder, value);
                            }

                            return new Result.Error<>(
                                    "Unsupported density function value: " + value);
                        }

                        @Override
                        public <D> Result<D> encode(Transcoder<D> coder, DensityFunction value) {
                            throw new UnsupportedOperationException("Encoding is not supported");
                        }
                    };
                });
    }

    private DensityFunction resolveReference(Key key) {
        var cached = this.densityFunctionCache.get(key);
        if (cached != null) {
            return cached;
        }

        var stack = this.resolutionStack.get();
        if (stack.contains(key)) {
            return this.throwRecursiveReference(key, stack);
        }

        stack.add(key);
        try {
            var densityFunction = this.readDensityFunction(key);
            this.densityFunctionCache.put(key, densityFunction);
            return densityFunction;
        } finally {
            stack.removeLast();
        }
    }

    private DensityFunction throwRecursiveReference(Key key, ArrayList<Key> stack) {
        var chain = new StringBuilder();
        for (var index = 0; index < stack.size(); index++) {
            if (index != 0) {
                chain.append(" -> ");
            }
            chain.append(stack.get(index).asString());
        }
        if (!chain.isEmpty()) {
            chain.append(" -> ");
        }
        chain.append(key.asString());
        throw new IllegalStateException("Recursive density function reference: " + chain);
    }

    private DensityFunction readDensityFunction(Key key) {
        var json = this.dataPack.readDensityFunction(key);
        return this.densityCodec.decode(Transcoder.JSON, json).orElseThrow();
    }

    private StructCodec<? extends DensityFunction> structForType(
            Key type, Codec<DensityFunction> self) {
        var name = type.value();
        return switch (name) {
            case "constant" ->
                    StructCodec.struct("value", Codec.FLOAT, noEncode(), value -> constant(value));
            case "add", "sub", "mul", "div", "min", "max" -> {
                var op = DensityFunctions.Binary.Op.valueOf(name.toUpperCase(Locale.ROOT));
                yield StructCodec.struct(
                        "left",
                        self,
                        noEncode(),
                        "right",
                        self,
                        noEncode(),
                        (left, right) -> new DensityFunctions.Binary(op, left, right));
            }
            case "abs",
                    "square",
                    "cube",
                    "sqrt",
                    "half_negative",
                    "quarter_negative",
                    "reciprocal",
                    "negate",
                    "squeeze",
                    "log",
                    "sign" -> {
                var mapping = DensityFunctions.Mapped.Type.valueOf(name.toUpperCase(Locale.ROOT));
                yield StructCodec.struct(
                        "input",
                        self,
                        noEncode(),
                        input -> new DensityFunctions.Mapped(mapping, input));
            }
            case "floor", "round", "ceil", "truncate" -> {
                var rounding = DensityFunctions.Round.Type.valueOf(name.toUpperCase(Locale.ROOT));
                yield StructCodec.struct(
                        "input",
                        self,
                        noEncode(),
                        "multiple",
                        self.optional(),
                        noEncode(),
                        (input, multiple) ->
                                new DensityFunctions.Round(
                                        rounding,
                                        input,
                                        multiple == null ? constant(1.0F) : multiple));
            }
            case "pow" ->
                    StructCodec.struct(
                            "base",
                            self,
                            noEncode(),
                            "exponent",
                            self,
                            noEncode(),
                            DensityFunctions.Pow::new);
            case "clamp" ->
                    StructCodec.struct(
                            "input",
                            self,
                            noEncode(),
                            "min",
                            Codec.FLOAT,
                            noEncode(),
                            "max",
                            Codec.FLOAT,
                            noEncode(),
                            (input, min, max) -> new DensityFunctions.Clamp(input, min, max));
            case "lerp" ->
                    StructCodec.struct(
                            "alpha",
                            self,
                            noEncode(),
                            "first",
                            self,
                            noEncode(),
                            "second",
                            self,
                            noEncode(),
                            DensityFunctions.Lerp::new);
            case "range_choice" ->
                    StructCodec.struct(
                            "input",
                            self,
                            noEncode(),
                            "min_inclusive",
                            Codec.FLOAT,
                            noEncode(),
                            "max_exclusive",
                            Codec.FLOAT,
                            noEncode(),
                            "when_in_range",
                            self,
                            noEncode(),
                            "when_out_of_range",
                            self,
                            noEncode(),
                            (input, min, max, inRange, outOfRange) ->
                                    new DensityFunctions.RangeChoice(
                                            input, min, max, inRange, outOfRange));
            case "interval_select" ->
                    StructCodec.struct(
                            "input",
                            self,
                            noEncode(),
                            "thresholds",
                            Codec.FLOAT.list(),
                            noEncode(),
                            "functions",
                            self.list(),
                            noEncode(),
                            (input, thresholds, functions) ->
                                    new DensityFunctions.IntervalSelect(
                                            input,
                                            thresholds.stream()
                                                    .mapToDouble(Float::doubleValue)
                                                    .toArray(),
                                            functions));
            case "gradient" ->
                    StructCodec.struct(
                            "axis",
                            AXIS_CODEC,
                            noEncode(),
                            "tiling",
                            TILING_CODEC.optional(DensityFunctions.Tiling.CLAMP_TO_EDGE),
                            noEncode(),
                            "from_coordinate",
                            Codec.INT,
                            noEncode(),
                            "to_coordinate",
                            Codec.INT,
                            noEncode(),
                            "from_value",
                            Codec.FLOAT,
                            noEncode(),
                            "to_value",
                            Codec.FLOAT,
                            noEncode(),
                            (axis, tiling, fromCoordinate, toCoordinate, fromValue, toValue) ->
                                    new DensityFunctions.Gradient(
                                            axis,
                                            tiling,
                                            fromCoordinate,
                                            toCoordinate,
                                            fromValue,
                                            toValue));
            case "slice" ->
                    StructCodec.struct(
                            "axis",
                            AXIS_CODEC,
                            noEncode(),
                            "coordinate",
                            Codec.INT,
                            noEncode(),
                            "input",
                            self,
                            noEncode(),
                            DensityFunctions.Slice::new);
            case "noise" ->
                    StructCodec.struct(
                            "noise",
                            Codec.KEY,
                            noEncode(),
                            "xz_scale",
                            Codec.DOUBLE,
                            noEncode(),
                            "y_scale",
                            Codec.DOUBLE,
                            noEncode(),
                            "shift_x",
                            self.optional(),
                            noEncode(),
                            "shift_y",
                            self.optional(),
                            noEncode(),
                            "shift_z",
                            self.optional(),
                            noEncode(),
                            (noiseKey, xzScale, yScale, shiftX, shiftY, shiftZ) ->
                                    new DensityFunctions.Noise(
                                            this.randomState.getOrCreateNoise(noiseKey),
                                            xzScale,
                                            yScale,
                                            nonZero(shiftX),
                                            nonZero(shiftY),
                                            nonZero(shiftZ)));
            case "shift" ->
                    StructCodec.struct(
                            "noise",
                            Codec.KEY,
                            noEncode(),
                            key ->
                                    new DensityFunctions.Shift(
                                            this.randomState.getOrCreateNoise(key)));
            case "shift_a" ->
                    StructCodec.struct(
                            "noise",
                            Codec.KEY,
                            noEncode(),
                            key ->
                                    new DensityFunctions.ShiftA(
                                            this.randomState.getOrCreateNoise(key)));
            case "shift_b" ->
                    StructCodec.struct(
                            "noise",
                            Codec.KEY,
                            noEncode(),
                            key ->
                                    new DensityFunctions.ShiftB(
                                            this.randomState.getOrCreateNoise(key)));
            case "blend_alpha" -> StructCodec.struct(DensityFunctions.BlendAlpha::new);
            case "blend_offset" -> StructCodec.struct(DensityFunctions.BlendOffset::new);
            case "beardifier" -> StructCodec.struct(DensityFunctions.BeardifierNode::new);
            case "blend_density" ->
                    StructCodec.struct(
                            "input", self, noEncode(), DensityFunctions.BlendDensity::new);
            case "cache" ->
                    StructCodec.struct("input", self, noEncode(), DensityFunctions.Cache::new);
            case "interpolated" ->
                    StructCodec.struct(
                            "input",
                            self,
                            noEncode(),
                            "cell_size_xz",
                            Codec.INT,
                            noEncode(),
                            "cell_size_y",
                            Codec.INT,
                            noEncode(),
                            DensityFunctions.Interpolated::new);
            case "find_top_surface" ->
                    StructCodec.struct(
                            "density",
                            self,
                            noEncode(),
                            "upper_bound",
                            self,
                            noEncode(),
                            "lower_bound",
                            Codec.INT,
                            noEncode(),
                            "cell_height",
                            Codec.INT,
                            noEncode(),
                            DensityFunctions.FindTopSurface::new);
            case "old_blended_noise" ->
                    StructCodec.struct(
                            "xz_scale",
                            Codec.DOUBLE,
                            noEncode(),
                            "y_scale",
                            Codec.DOUBLE,
                            noEncode(),
                            "xz_factor",
                            Codec.DOUBLE,
                            noEncode(),
                            "y_factor",
                            Codec.DOUBLE,
                            noEncode(),
                            "smear_scale_multiplier",
                            Codec.DOUBLE,
                            noEncode(),
                            (xzScale, yScale, xzFactor, yFactor, smearScaleMultiplier) ->
                                    new DensityFunctions.OldBlendedNoise(
                                            new BlendedNoise(
                                                    this.randomState.terrainRandom(),
                                                    xzScale,
                                                    yScale,
                                                    xzFactor,
                                                    yFactor,
                                                    smearScaleMultiplier)));
            case "end_outer_islands" ->
                    StructCodec.struct(
                            () -> {
                                var randomSource = new LegacyRandomSource(this.randomState.seed());
                                randomSource.consumeCount(17292);
                                return new DensityFunctions.EndOuterIslands(
                                        new SimplexNoise(randomSource));
                            });
            case "distance_to_point" ->
                    StructCodec.struct(
                            "point",
                            Codec.INT.list(3),
                            noEncode(),
                            "metric",
                            METRIC_CODEC,
                            noEncode(),
                            (point, metric) ->
                                    new DensityFunctions.DistanceToPoint(
                                            point.get(0), point.get(1), point.get(2), metric));
            case "spline" -> splineStruct(self);
            default ->
                    throw new IllegalStateException(
                            "Unsupported density function type: " + type.asString());
        };
    }

    private static DensityFunction constant(float value) {
        return new DensityFunctions.Constant(value);
    }

    private static DensityFunction nonZero(DensityFunction shift) {
        if (shift instanceof DensityFunctions.Constant(var value) && value == 0.0) {
            return null;
        }
        return shift;
    }

    private static <T, R> java.util.function.Function<T, R> noEncode() {
        return value -> {
            throw new UnsupportedOperationException("Encoding is not supported");
        };
    }

    private static final Codec<DensityFunctions.Axis> AXIS_CODEC =
            enumCodec(DensityFunctions.Axis.class);
    private static final Codec<DensityFunctions.Tiling> TILING_CODEC =
            enumCodec(DensityFunctions.Tiling.class);
    private static final Codec<DensityFunctions.Metric> METRIC_CODEC =
            enumCodec(DensityFunctions.Metric.class);

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.transform(
                value -> Enum.valueOf(type, value.toUpperCase(Locale.ROOT)),
                value -> {
                    throw new UnsupportedOperationException("Encoding is not supported");
                });
    }

    private static StructCodec<DensityFunction> splineStruct(Codec<DensityFunction> self) {
        Codec<DensityFunctions.SplineNode> nodeCodec =
                Codec.Recursive(
                        nodeSelf -> {
                            var constant =
                                    Codec.FLOAT.transform(
                                            value ->
                                                    (DensityFunctions.SplineNode)
                                                            new DensityFunctions.SplineConstant(
                                                                    value),
                                            node -> {
                                                throw new UnsupportedOperationException(
                                                        "Encoding is not supported");
                                            });

                            var pointCodec =
                                    StructCodec.struct(
                                            "location",
                                            Codec.FLOAT,
                                            point -> {
                                                throw new UnsupportedOperationException(
                                                        "Encoding is not supported");
                                            },
                                            "value",
                                            nodeSelf,
                                            point -> {
                                                throw new UnsupportedOperationException(
                                                        "Encoding is not supported");
                                            },
                                            "derivative",
                                            Codec.FLOAT,
                                            point -> {
                                                throw new UnsupportedOperationException(
                                                        "Encoding is not supported");
                                            },
                                            SplinePoint::new);

                            StructCodec<DensityFunctions.SplineNode> multipoint =
                                    StructCodec.struct(
                                            "coordinate",
                                            self,
                                            node -> {
                                                throw new UnsupportedOperationException(
                                                        "Encoding is not supported");
                                            },
                                            "points",
                                            pointCodec.list(),
                                            node -> {
                                                throw new UnsupportedOperationException(
                                                        "Encoding is not supported");
                                            },
                                            (coordinate, points) -> {
                                                var locations = new float[points.size()];
                                                var values =
                                                        new ArrayList<DensityFunctions.SplineNode>(
                                                                points.size());
                                                var derivatives = new float[points.size()];
                                                for (var index = 0;
                                                        index < points.size();
                                                        index++) {
                                                    var point = points.get(index);
                                                    locations[index] = point.location();
                                                    values.add(point.value());
                                                    derivatives[index] = point.derivative();
                                                }
                                                return new DensityFunctions.SplineMultipoint(
                                                        coordinate, locations, values, derivatives);
                                            });

                            return new Codec<>() {
                                @Override
                                public <D> Result<DensityFunctions.SplineNode> decode(
                                        Transcoder<D> coder, D value) {
                                    var mapResult = coder.getMap(value);
                                    if (mapResult instanceof Result.Ok) {
                                        return multipoint.decode(coder, value);
                                    }

                                    var floatResult = coder.getFloat(value);
                                    if (floatResult instanceof Result.Ok) {
                                        return constant.decode(coder, value);
                                    }

                                    return new Result.Error<>(
                                            "Unsupported spline node value: " + value);
                                }

                                @Override
                                public <D> Result<D> encode(
                                        Transcoder<D> coder, DensityFunctions.SplineNode value) {
                                    throw new UnsupportedOperationException(
                                            "Encoding is not supported");
                                }
                            };
                        });

        return StructCodec.struct(
                "spline",
                nodeCodec,
                function -> {
                    throw new UnsupportedOperationException("Encoding is not supported");
                },
                DensityFunctions.Spline::new);
    }

    private record SplinePoint(
            float location, DensityFunctions.SplineNode value, float derivative) {}
}
