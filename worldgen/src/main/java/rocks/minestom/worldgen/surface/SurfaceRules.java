package rocks.minestom.worldgen.surface;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.RandomState;
import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.biome.BiomeClimate;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.density.ChunkContext;
import rocks.minestom.worldgen.density.DensityFunction;

/**
 * Describes the surface material rule system that selects top-layer blocks from climate and noise
 * inputs. Surface rules are responsible for the visible ground: topsoil, stone, sand, and
 * biome-specific layers that sit on top of the raw terrain density.
 */
public final class SurfaceRules {
    private SurfaceRules() {}

    public static Block constantBlock(RuleSource rule) {
        if (rule instanceof BlockRuleSource(var block)) {
            return block;
        }
        return null;
    }

    /**
     * Rule codec that also resolves named rule and condition references from the {@code
     * material_rule} and {@code material_condition} datapack directories.
     */
    public static Codec<RuleSource> codec(DataPack dataPack, Codec<DensityFunction> densityCodec) {
        var ruleReferences = new ConcurrentHashMap<Key, RuleSource>();
        var conditionReferences = new ConcurrentHashMap<Key, ConditionSource>();
        Codec<ConditionSource> conditionCodec =
                Codec.Recursive(
                        self ->
                                referenceCodec(
                                        Codec.KEY.<ConditionSource>unionType(
                                                "type",
                                                type -> structForCondition(type, self),
                                                condition -> {
                                                    throw new UnsupportedOperationException(
                                                            "Encoding is not supported");
                                                }),
                                        conditionReferences,
                                        dataPack::readMaterialCondition));
        return Codec.Recursive(
                self ->
                        referenceCodec(
                                Codec.KEY.<RuleSource>unionType(
                                        "type",
                                        type ->
                                                structForRule(
                                                        type, self, conditionCodec, densityCodec),
                                        rule -> {
                                            throw new UnsupportedOperationException(
                                                    "Encoding is not supported");
                                        }),
                                ruleReferences,
                                dataPack::readMaterialRule));
    }

    /**
     * Wraps a typed-object codec so a plain string value loads the named definition through {@code
     * reader} with the same codec.
     */
    private static <T> Codec<T> referenceCodec(
            Codec<T> direct, Map<Key, T> cache, Function<Key, JsonElement> reader) {
        return new Codec<>() {
            private final Codec<T> self = this;

            @Override
            public <D> Result<T> decode(Transcoder<D> coder, D value) {
                if (coder.getString(value) instanceof Result.Ok<String>(var name)) {
                    var key = Key.key(name);
                    var cached = cache.get(key);
                    if (cached != null) {
                        return new Result.Ok<>(cached);
                    }
                    var loaded = self.decode(Transcoder.JSON, reader.apply(key));
                    if (loaded instanceof Result.Ok<T>(var definition)) {
                        cache.put(key, definition);
                    }
                    return loaded;
                }
                return direct.decode(coder, value);
            }

            @Override
            public <D> Result<D> encode(Transcoder<D> coder, T value) {
                throw new UnsupportedOperationException("Encoding is not supported");
            }
        };
    }

    private static StructCodec<? extends RuleSource> structForRule(
            Key type,
            Codec<RuleSource> self,
            Codec<ConditionSource> conditionCodec,
            Codec<DensityFunction> densityCodec) {
        return switch (type.asString()) {
            case "minecraft:bandlands" -> StructCodec.struct(Bandlands::new);
            case "minecraft:ore_vein" ->
                    StructCodec.struct(
                            "ore_block",
                            BlockCodec.CODEC,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "raw_ore_block",
                            BlockCodec.CODEC,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "filler_block",
                            BlockCodec.CODEC,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "raw_ore_chance",
                            Codec.FLOAT,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "density",
                            densityCodec,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "richness",
                            densityCodec,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "filler_gap",
                            densityCodec,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            OreVeinRuleSource::new);
            case "minecraft:block" ->
                    StructCodec.struct(
                            "result_state",
                            BlockCodec.CODEC,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            BlockRuleSource::new);
            case "minecraft:sequence" ->
                    StructCodec.struct(
                            "sequence",
                            self.list(),
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            SequenceRuleSource::new);
            case "minecraft:condition" ->
                    StructCodec.struct(
                            "if_true",
                            conditionCodec,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "then_run",
                            self,
                            rule -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            ConditionRuleSource::new);
            default ->
                    throw new IllegalStateException(
                            "Unsupported surface rule type: " + type.asString());
        };
    }

    private static StructCodec<? extends ConditionSource> structForCondition(
            Key type, Codec<ConditionSource> self) {
        return switch (type.asString()) {
            case "minecraft:biome" ->
                    StructCodec.struct(
                            "biome_is",
                            Codec.KEY.listOrSingle(),
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            BiomeConditionSource::new);
            case "minecraft:noise_threshold" ->
                    StructCodec.struct(
                            "noise",
                            Codec.KEY,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "min_threshold",
                            Codec.DOUBLE,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "max_threshold",
                            Codec.DOUBLE,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "is_3d",
                            Codec.BOOLEAN.optional(false),
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            NoiseThresholdConditionSource::new);
            case "minecraft:vertical_gradient" ->
                    StructCodec.struct(
                            "random_name",
                            Codec.KEY,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "true_at_and_below",
                            VerticalAnchor.CODEC,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "false_at_and_above",
                            VerticalAnchor.CODEC,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            VerticalGradientConditionSource::new);
            case "minecraft:y_above" ->
                    StructCodec.struct(
                            "anchor",
                            VerticalAnchor.CODEC,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "surface_depth_multiplier",
                            Codec.INT,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "add_stone_depth",
                            Codec.BOOLEAN,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            YAboveConditionSource::new);
            case "minecraft:water" ->
                    StructCodec.struct(
                            "offset",
                            Codec.INT,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "surface_depth_multiplier",
                            Codec.INT,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "add_stone_depth",
                            Codec.BOOLEAN,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            WaterConditionSource::new);
            case "minecraft:stone_depth" ->
                    StructCodec.struct(
                            "offset",
                            Codec.INT,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "add_surface_depth",
                            Codec.BOOLEAN,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "secondary_depth_range",
                            Codec.INT,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "surface_type",
                            Codec.STRING.transform(
                                    value ->
                                            switch (value) {
                                                case "floor" -> SurfaceType.FLOOR;
                                                case "ceiling" -> SurfaceType.CEILING;
                                                default ->
                                                        throw new IllegalStateException(
                                                                "Unsupported surface_type: "
                                                                        + value);
                                            },
                                    value -> {
                                        throw new UnsupportedOperationException(
                                                "Encoding is not supported");
                                    }),
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            StoneDepthConditionSource::new);
            case "minecraft:not" ->
                    StructCodec.struct(
                            "invert",
                            self,
                            condition -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            NotConditionSource::new);
            case "minecraft:hole" -> StructCodec.struct(HoleConditionSource::new);
            case "minecraft:steep" -> StructCodec.struct(SteepConditionSource::new);
            case "minecraft:above_preliminary_surface" ->
                    StructCodec.struct(AbovePreliminarySurfaceConditionSource::new);
            case "minecraft:temperature" -> StructCodec.struct(TemperatureConditionSource::new);
            default ->
                    throw new IllegalStateException(
                            "Unsupported surface condition type: " + type.asString());
        };
    }

    public interface RuleSource {
        Block tryApply(Context context);
    }

    public interface ConditionSource {
        boolean test(Context context);
    }

    public enum SurfaceType {
        FLOOR,
        CEILING
    }

    public static final class Context {
        private static final int HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE = 8;

        private final SurfaceSystem system;
        private final RandomState randomState;
        private final BiomeResolver biomeResolver;
        private final BiomeZoomer biomeZoomer;
        private final DensityFunction chunkSurfaceLevel;
        private final ChunkContext densityContext;
        private final int minY;
        private final int maxYInclusive;

        private Key biome;
        private boolean biomeDirty;
        private int blockX;
        private int blockZ;
        private int blockY;

        private int surfaceDepth;
        private double surfaceSecondary;
        private boolean surfaceSecondaryDirty;
        private int minSurfaceLevel;
        private boolean minSurfaceLevelDirty;
        private int waterHeight;
        private int stoneDepthBelow;
        private int stoneDepthAbove;
        private boolean steep;

        public Context(
                SurfaceSystem system,
                RandomState randomState,
                BiomeResolver biomeResolver,
                BiomeZoomer biomeZoomer,
                DensityFunction chunkSurfaceLevel,
                ChunkContext densityContext,
                int minY,
                int maxYInclusive) {
            this.system = system;
            this.randomState = randomState;
            this.biomeResolver = biomeResolver;
            this.biomeZoomer = biomeZoomer;
            this.chunkSurfaceLevel = chunkSurfaceLevel;
            this.densityContext = densityContext;
            this.minY = minY;
            this.maxYInclusive = maxYInclusive;
        }

        public void updateXZ(int blockX, int blockZ, boolean steep) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.steep = steep;
            this.surfaceDepth = this.system.getSurfaceDepth(blockX, blockZ);
            this.surfaceSecondaryDirty = true;
            this.minSurfaceLevelDirty = true;
        }

        public void updateY(int blockY, int stoneDepthAbove, int stoneDepthBelow, int waterHeight) {
            this.blockY = blockY;
            this.stoneDepthAbove = stoneDepthAbove;
            this.stoneDepthBelow = stoneDepthBelow;
            this.waterHeight = waterHeight;
            this.biomeDirty = true;
        }

        public SurfaceSystem system() {
            return this.system;
        }

        public RandomState randomState() {
            return this.randomState;
        }

        public BiomeResolver biomeResolver() {
            return this.biomeResolver;
        }

        public int minY() {
            return this.minY;
        }

        public int maxYInclusive() {
            return this.maxYInclusive;
        }

        public Key biome() {
            if (this.biomeDirty) {
                this.biomeDirty = false;
                this.biome = this.biomeZoomer.biome(this.blockX, this.blockY, this.blockZ);
            }
            return this.biome;
        }

        public int blockX() {
            return this.blockX;
        }

        public int blockZ() {
            return this.blockZ;
        }

        public int blockY() {
            return this.blockY;
        }

        public int seaLevel() {
            return this.system.seaLevel();
        }

        public int surfaceDepth() {
            return this.surfaceDepth;
        }

        public double surfaceSecondary() {
            if (this.surfaceSecondaryDirty) {
                this.surfaceSecondaryDirty = false;
                this.surfaceSecondary = this.system.getSurfaceSecondary(this.blockX, this.blockZ);
            }
            return this.surfaceSecondary;
        }

        public int minSurfaceLevel() {
            if (this.minSurfaceLevelDirty) {
                this.minSurfaceLevelDirty = false;
                var preliminarySurface =
                        VMath.floor(
                                this.densityContext.sample(
                                        this.chunkSurfaceLevel, this.blockX, 0, this.blockZ));
                this.minSurfaceLevel =
                        preliminarySurface
                                + this.surfaceDepth
                                - HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE;
            }
            return this.minSurfaceLevel;
        }

        public ChunkContext densityContext() {
            return this.densityContext;
        }

        public int waterHeight() {
            return this.waterHeight;
        }

        public int stoneDepthBelow() {
            return this.stoneDepthBelow;
        }

        public int stoneDepthAbove() {
            return this.stoneDepthAbove;
        }

        public boolean steep() {
            return this.steep;
        }
    }

    private record Bandlands() implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            return context.system().getBand(context.blockX(), context.blockY(), context.blockZ());
        }
    }

    private record OreVeinRuleSource(
            Block oreBlock,
            Block rawOreBlock,
            Block fillerBlock,
            float rawOreChance,
            DensityFunction density,
            DensityFunction richness,
            DensityFunction fillerGap)
            implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            var x = context.blockX();
            var y = context.blockY();
            var z = context.blockZ();
            var densityContext = context.densityContext();
            var veinDensity = (float) densityContext.sample(this.density, x, y, z);
            if (veinDensity <= 0.0F) {
                return null;
            }

            var random = context.randomState().oreRandom().at(x, y, z);
            if (random.nextFloat() > veinDensity) {
                return null;
            }

            var veinRichness = (float) densityContext.sample(this.richness, x, y, z);
            if (random.nextFloat() < veinRichness
                    && (float) densityContext.sample(this.fillerGap, x, y, z) < 0.0F) {
                return random.nextFloat() < this.rawOreChance ? this.rawOreBlock : this.oreBlock;
            }
            return this.fillerBlock;
        }
    }

    private record BlockRuleSource(Block block) implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            return this.block;
        }
    }

    private record SequenceRuleSource(List<RuleSource> sequence) implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            for (var rule : this.sequence) {
                var result = rule.tryApply(context);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
    }

    private record ConditionRuleSource(ConditionSource condition, RuleSource thenRun)
            implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            if (!this.condition.test(context)) {
                return null;
            }
            return this.thenRun.tryApply(context);
        }
    }

    private record BiomeConditionSource(List<Key> biomes) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return this.biomes.contains(context.biome());
        }
    }

    private record NoiseThresholdConditionSource(
            Key noise, double minThreshold, double maxThreshold, boolean is3d)
            implements ConditionSource {
        @Override
        public boolean test(Context context) {
            var normalNoise = context.randomState().getOrCreateNoise(this.noise);
            var value =
                    this.is3d
                            ? normalNoise.getValue(
                                    (double) context.blockX(),
                                    (double) context.blockY(),
                                    (double) context.blockZ())
                            : normalNoise.getValue(
                                    (double) context.blockX(), 0.0D, (double) context.blockZ());
            return value >= this.minThreshold && value <= this.maxThreshold;
        }
    }

    private record VerticalGradientConditionSource(
            Key randomName, VerticalAnchor trueAtAndBelow, VerticalAnchor falseAtAndAbove)
            implements ConditionSource {
        @Override
        public boolean test(Context context) {
            var trueY = this.trueAtAndBelow.resolveY(context.minY(), context.maxYInclusive());
            var falseY = this.falseAtAndAbove.resolveY(context.minY(), context.maxYInclusive());
            var y = context.blockY();
            if (y <= trueY) {
                return true;
            }
            if (y >= falseY) {
                return false;
            }

            var chance = map((double) y, (double) trueY, (double) falseY, 1.0D, 0.0D);
            var randomFactory = context.randomState().getOrCreateRandomFactory(this.randomName);
            var randomSource = randomFactory.at(context.blockX(), y, context.blockZ());
            return (double) randomSource.nextFloat() < chance;
        }
    }

    private record YAboveConditionSource(
            VerticalAnchor anchor, int surfaceDepthMultiplier, boolean addStoneDepth)
            implements ConditionSource {
        @Override
        public boolean test(Context context) {
            var depth = this.addStoneDepth ? context.stoneDepthAbove() : 0;
            return context.blockY() + depth
                    >= this.anchor.resolveY(context.minY(), context.maxYInclusive())
                            + context.surfaceDepth() * this.surfaceDepthMultiplier;
        }
    }

    private record WaterConditionSource(
            int offset, int surfaceDepthMultiplier, boolean addStoneDepth)
            implements ConditionSource {
        @Override
        public boolean test(Context context) {
            if (context.waterHeight() == Integer.MIN_VALUE) {
                return true;
            }

            var depth = this.addStoneDepth ? context.stoneDepthAbove() : 0;
            return context.blockY() + depth
                    >= context.waterHeight()
                            + this.offset
                            + context.surfaceDepth() * this.surfaceDepthMultiplier;
        }
    }

    private record StoneDepthConditionSource(
            int offset, boolean addSurfaceDepth, int secondaryDepthRange, SurfaceType surfaceType)
            implements ConditionSource {
        @Override
        public boolean test(Context context) {
            var ceiling = this.surfaceType == SurfaceType.CEILING;
            var stoneDepth = ceiling ? context.stoneDepthBelow() : context.stoneDepthAbove();
            var surfaceDepth = this.addSurfaceDepth ? context.surfaceDepth() : 0;
            var secondary =
                    this.secondaryDepthRange == 0
                            ? 0
                            : (int)
                                    map(
                                            context.surfaceSecondary(),
                                            -1.0D,
                                            1.0D,
                                            0.0D,
                                            (double) this.secondaryDepthRange);
            return stoneDepth <= 1 + this.offset + surfaceDepth + secondary;
        }
    }

    private record NotConditionSource(ConditionSource invert) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return !this.invert.test(context);
        }
    }

    private record HoleConditionSource() implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return context.surfaceDepth() <= 0;
        }
    }

    private record SteepConditionSource() implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return context.steep();
        }
    }

    private record AbovePreliminarySurfaceConditionSource() implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return context.blockY() >= context.minSurfaceLevel();
        }
    }

    private record TemperatureConditionSource() implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return BiomeClimate.coldEnoughToSnow(
                    context.biomeResolver(),
                    context.biome(),
                    context.blockX(),
                    context.blockY(),
                    context.blockZ(),
                    context.seaLevel());
        }
    }

    private static double map(
            double value, double inMin, double inMax, double outMin, double outMax) {
        if (inMax - inMin == 0.0D) {
            return outMin;
        }
        var delta = (value - inMin) / (inMax - inMin);
        return outMin + delta * (outMax - outMin);
    }
}
