package rocks.minestom.worldgen.feature.placement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.BlockSupports;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.feature.UnknownTypes;
import rocks.minestom.worldgen.feature.configurations.BlockSets;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.noise.SimplexNoise;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public final class PlacementModifiers {
    // Vanilla Biome.BIOME_INFO_NOISE: PerlinSimplexNoise over a legacy random;
    // with octave set [0] the wrapper is identity, so a single simplex suffices
    private static final SimplexNoise BIOME_INFO_NOISE =
            new SimplexNoise(new LegacyRandomSource(2345L));

    /**
     * Block tag manager for the configured feature currently being parsed. Set by {@code
     * Features.parseConfiguredFeature} so that nested parses (inline placed features, state
     * provider rules) can resolve block tags without threading the manager through every codec.
     */
    private static final ThreadLocal<BlockTagManager> CURRENT_BLOCK_TAGS = new ThreadLocal<>();

    private static final PlacementModifier NEVER_MODIFIER =
            (context, random, position) -> List.of();

    private PlacementModifiers() {}

    private static boolean debugFilterAt(BlockVec position) {
        var gate = System.getProperty("worldgen.debugFilter");
        if (gate == null) {
            return false;
        }
        if (gate.equals("1")) {
            return true;
        }
        return java.util.Arrays.asList(gate.split(";"))
                .contains((position.blockX() >> 4) + "," + (position.blockZ() >> 4));
    }

    public static BlockTagManager currentBlockTags() {
        return CURRENT_BLOCK_TAGS.get();
    }

    public static void currentBlockTags(BlockTagManager blockTags) {
        if (blockTags == null) {
            CURRENT_BLOCK_TAGS.remove();
        } else {
            CURRENT_BLOCK_TAGS.set(blockTags);
        }
    }

    public static List<PlacementModifier> parse(JsonArray placementArray) {
        var modifiers = new ArrayList<PlacementModifier>();
        for (var placementElement : placementArray) {
            if (!placementElement.isJsonObject()) {
                throw new IllegalArgumentException("Placement modifier must be an object");
            }

            modifiers.add(parseModifier(placementElement.getAsJsonObject()));
        }

        return List.copyOf(modifiers);
    }

    public static List<BlockVec> apply(
            List<PlacementModifier> modifiers,
            PlacementContext context,
            rocks.minestom.worldgen.random.RandomSource random,
            BlockVec origin) {
        var positions = new ArrayList<BlockVec>();
        positions.add(origin);

        for (var modifier : modifiers) {
            var nextPositions = new ArrayList<BlockVec>();
            for (var position : positions) {
                nextPositions.addAll(modifier.apply(context, random, position));
            }
            positions = nextPositions;
            if (positions.isEmpty()) {
                break;
            }
        }

        return positions;
    }

    private static PlacementModifier parseModifier(JsonObject object) {
        var type = Key.key(object.get("type").getAsString()).asString();
        return switch (type) {
            case "minecraft:count" -> new CountModifier(IntProvider.fromJson(object.get("count")));
            case "minecraft:heightmap" ->
                    new HeightmapModifier(parseHeightmap(object.get("heightmap").getAsString()));
            case "minecraft:in_square" -> new InSquareModifier();
            case "minecraft:biome" -> new BiomeModifier();
            case "minecraft:rarity_filter" ->
                    new RarityFilterModifier(object.get("chance").getAsInt());
            case "minecraft:height_range" -> {
                var provider = HeightProvider.parse(object.getAsJsonObject("height"));
                yield new HeightRangeModifier(provider);
            }
            case "minecraft:offset" ->
                    new OffsetModifier(
                            IntProvider.fromJson(object.get("x")),
                            IntProvider.fromJson(object.get("y")),
                            IntProvider.fromJson(object.get("z")));
            case "minecraft:random_chance" ->
                    new RandomChanceModifier(object.get("chance").getAsFloat());
            case "minecraft:randomly_selected" ->
                    new RandomlySelectedModifier(parse(object.getAsJsonArray("placements")));
            case "minecraft:cuboid" ->
                    new CuboidModifier(
                            IntProvider.fromJson(object.get("xz_size")),
                            IntProvider.fromJson(object.get("y_size")),
                            !object.has("include_edges")
                                    || object.get("include_edges").getAsBoolean(),
                            !object.has("include_interior")
                                    || object.get("include_interior").getAsBoolean());
            case "minecraft:fixed_placement" -> {
                var positions = new ArrayList<BlockVec>();
                for (var positionElement : object.getAsJsonArray("positions")) {
                    var positionArray = positionElement.getAsJsonArray();
                    positions.add(
                            new BlockVec(
                                    positionArray.get(0).getAsInt(),
                                    positionArray.get(1).getAsInt(),
                                    positionArray.get(2).getAsInt()));
                }
                yield new FixedPlacementModifier(positions);
            }
            case "minecraft:noise_based_count" ->
                    new NoiseBasedCountModifier(
                            object.get("noise_to_count_ratio").getAsInt(),
                            object.get("noise_factor").getAsDouble(),
                            object.has("noise_offset")
                                    ? object.get("noise_offset").getAsDouble()
                                    : 0.0D);
            case "minecraft:noise_threshold_count" ->
                    new NoiseThresholdCountModifier(
                            object.get("noise_level").getAsDouble(),
                            object.get("below_noise").getAsInt(),
                            object.get("above_noise").getAsInt());
            case "minecraft:count_on_every_layer" ->
                    new CountOnEveryLayerModifier(IntProvider.fromJson(object.get("count")));
            case "minecraft:environment_scan" ->
                    new EnvironmentScanModifier(
                            Direction.fromString(object.get("direction_of_search").getAsString()),
                            parseBlockPredicate(object.getAsJsonObject("target_condition")),
                            object.has("allowed_search_condition")
                                    ? parseBlockPredicate(
                                            object.getAsJsonObject("allowed_search_condition"))
                                    : BlockPredicate.ALWAYS_TRUE,
                            object.get("max_steps").getAsInt());
            case "minecraft:surface_relative_threshold_filter" -> {
                var heightmapType = parseHeightmap(object.get("heightmap").getAsString());
                var minInclusive =
                        object.has("min_inclusive")
                                ? object.get("min_inclusive").getAsInt()
                                : Integer.MIN_VALUE;
                var maxInclusive =
                        object.has("max_inclusive")
                                ? object.get("max_inclusive").getAsInt()
                                : Integer.MAX_VALUE;
                yield new SurfaceRelativeThresholdFilterModifier(
                        heightmapType, minInclusive, maxInclusive);
            }
            case "minecraft:surface_water_depth_filter" ->
                    new SurfaceWaterDepthFilterModifier(object.get("max_water_depth").getAsInt());
            case "minecraft:block_predicate_filter" ->
                    new BlockPredicateFilterModifier(
                            parseBlockPredicate(object.getAsJsonObject("predicate")));
            default -> {
                UnknownTypes.report("placement modifier", type);
                yield NEVER_MODIFIER;
            }
        };
    }

    private static PlacementContext.HeightmapType parseHeightmap(String name) {
        var heightmapType = PlacementContext.HeightmapType.fromString(name);
        if (heightmapType == null) {
            throw new IllegalArgumentException("Unknown heightmap type " + name);
        }

        return heightmapType;
    }

    public static BlockPredicate parseBlockPredicate(JsonObject object) {
        return parseBlockPredicate(object, currentBlockTags());
    }

    public static BlockPredicate parseBlockPredicate(JsonObject object, BlockTagManager blockTags) {
        var type = Key.key(object.get("type").getAsString()).asString();
        return switch (type) {
            case "minecraft:true" -> BlockPredicate.ALWAYS_TRUE;
            case "minecraft:matching_blocks" ->
                    new MatchingBlocksPredicate(
                            BlockSets.parse(object.get("blocks"), blockTags), parseOffset(object));
            case "minecraft:matching_block_tag" -> {
                var tag = Key.key(object.get("tag").getAsString());
                yield new MatchingBlocksPredicate(
                        BlockSets.resolveTag(tag, blockTags), parseOffset(object));
            }
            case "minecraft:matching_fluids" ->
                    new MatchingFluidsPredicate(
                            parseFluids(object.get("fluids")), parseOffset(object));
            case "minecraft:would_survive" ->
                    new WouldSurvivePredicate(
                            BlockCodec.CODEC
                                    .decode(Transcoder.JSON, object.get("state"))
                                    .orElseThrow(),
                            parseOffset(object),
                            Features.currentBlockSupports());
            case "minecraft:all_of" ->
                    new AllOfPredicate(
                            parsePredicates(object.getAsJsonArray("predicates"), blockTags));
            case "minecraft:any_of" ->
                    new AnyOfPredicate(
                            parsePredicates(object.getAsJsonArray("predicates"), blockTags));
            case "minecraft:not" ->
                    new NotPredicate(
                            parseBlockPredicate(object.getAsJsonObject("predicate"), blockTags));
            case "minecraft:inside_world_bounds" ->
                    new InsideWorldBoundsPredicate(parseOffset(object));
            case "minecraft:solid" -> new SolidPredicate(parseOffset(object));
            case "minecraft:replaceable" -> new ReplaceablePredicate(parseOffset(object));
            case "minecraft:has_sturdy_face" ->
                    new HasSturdyFacePredicate(
                            Direction.fromString(object.get("direction").getAsString()),
                            parseOffset(object));
            case "minecraft:height_range" ->
                    new HeightRangePredicate(
                            parseAnchor(object.get("min_inclusive")),
                            parseAnchor(object.get("max_inclusive")));
            case "minecraft:volume_match" ->
                    new VolumeMatchPredicate(
                            parseVector(object.getAsJsonArray("min")),
                            parseVector(object.getAsJsonArray("max")),
                            parseBlockPredicate(object.getAsJsonObject("match"), blockTags));
            // No entities exist while a chunk generates, so nothing can obstruct the block
            case "minecraft:unobstructed" -> BlockPredicate.ALWAYS_TRUE;
            default -> {
                UnknownTypes.report("block predicate", type);
                yield BlockPredicate.NEVER;
            }
        };
    }

    private static List<BlockPredicate> parsePredicates(
            JsonArray array, BlockTagManager blockTags) {
        var predicates = new ArrayList<BlockPredicate>();
        for (var predicate : array) {
            predicates.add(parseBlockPredicate(predicate.getAsJsonObject(), blockTags));
        }

        return List.copyOf(predicates);
    }

    private static HeightAnchor parseAnchor(JsonElement json) {
        return HeightAnchor.fromJson(json);
    }

    private static Set<Key> parseFluids(JsonElement value) {
        var fluids = new HashSet<Key>();
        if (value.isJsonArray()) {
            for (var fluidValue : value.getAsJsonArray()) {
                fluids.add(Key.key(fluidValue.getAsString()));
            }
            return fluids;
        }

        fluids.add(Key.key(value.getAsString()));
        return fluids;
    }

    private static BlockVec parseOffset(JsonObject object) {
        return object.has("offset") ? parseVector(object.getAsJsonArray("offset")) : BlockVec.ZERO;
    }

    private static BlockVec parseVector(JsonArray vector) {
        return new BlockVec(
                vector.get(0).getAsInt(), vector.get(1).getAsInt(), vector.get(2).getAsInt());
    }

    private record CountModifier(IntProvider count) implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var countValue = this.count.sample(random);
            if (countValue <= 0) {
                return List.of();
            }

            var results = new ArrayList<BlockVec>(countValue);
            for (var countIndex = 0; countIndex < countValue; countIndex++) {
                results.add(position);
            }
            return results;
        }
    }

    private record HeightmapModifier(PlacementContext.HeightmapType type)
            implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var y = context.getHeight(this.type, position.blockX(), position.blockZ());
            if (y <= context.minY()) {
                return List.of();
            }

            return List.of(new BlockVec(position.blockX(), y, position.blockZ()));
        }
    }

    private static final class InSquareModifier implements PlacementModifier {
        private static final boolean DEBUG = Boolean.getBoolean("worldgen.debugInSquare");

        @Override
        public List<BlockVec> apply(
                PlacementContext context,
                rocks.minestom.worldgen.random.RandomSource random,
                BlockVec position) {
            var x = position.blockX() + random.nextInt(16);
            var z = position.blockZ() + random.nextInt(16);
            if (DEBUG) {
                System.out.println(
                        "INSQ "
                                + position.blockX()
                                + ","
                                + position.blockZ()
                                + " -> "
                                + x
                                + ","
                                + z);
            }
            return List.of(new BlockVec(x, position.blockY(), z));
        }
    }

    private static final class BiomeModifier implements PlacementModifier {
        @Override
        public List<BlockVec> apply(
                PlacementContext context,
                rocks.minestom.worldgen.random.RandomSource random,
                BlockVec position) {
            if (context.currentFeatureInBiomeAt(position)) {
                return List.of(position);
            }
            if (debugFilterAt(position)) {
                System.out.println("BIOMEREJ " + context.currentFeature() + " at " + position);
            }
            return List.of();
        }
    }

    private record RarityFilterModifier(int chance) implements PlacementModifier {
        private RarityFilterModifier(int chance) {
            this.chance = Math.max(1, chance);
        }

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            return random.nextFloat() < 1.0F / (float) this.chance ? List.of(position) : List.of();
        }
    }

    private record HeightRangeModifier(HeightProvider provider) implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            return List.of(
                    new BlockVec(
                            position.blockX(),
                            this.provider.sample(random, context),
                            position.blockZ()));
        }
    }

    private record OffsetModifier(IntProvider x, IntProvider y, IntProvider z)
            implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var offsetX = this.x.sample(random);
            var offsetY = this.y.sample(random);
            var offsetZ = this.z.sample(random);
            return List.of(position.add(offsetX, offsetY, offsetZ));
        }
    }

    private record RandomChanceModifier(float chance) implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            return random.nextFloat() < this.chance ? List.of(position) : List.of();
        }
    }

    private record RandomlySelectedModifier(List<PlacementModifier> placements)
            implements PlacementModifier {
        private RandomlySelectedModifier {
            if (placements.isEmpty()) {
                throw new IllegalArgumentException("randomly_selected needs placements");
            }
        }

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            return this.placements
                    .get(random.nextInt(this.placements.size()))
                    .apply(context, random, position);
        }
    }

    private record CuboidModifier(
            IntProvider xzSize, IntProvider ySize, boolean includeEdges, boolean includeInterior)
            implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var height = this.ySize.sample(random);
            var width = this.xzSize.sample(random);
            var length = this.xzSize.sample(random);
            var results = new ArrayList<BlockVec>();

            for (var x = 0; x <= width; x++) {
                for (var y = 0; y <= height; y++) {
                    for (var z = 0; z <= length; z++) {
                        if ((this.includeEdges || x != 0 && x != width || y != 0 && y != height)
                                && (this.includeEdges
                                        || z != 0 && z != length
                                        || y != 0 && y != height)
                                && (this.includeEdges
                                        || x != 0 && x != width
                                        || z != 0 && z != length)
                                && (this.includeInterior
                                        || x == 0
                                        || x == width
                                        || y == 0
                                        || y == height
                                        || z == 0
                                        || z == length)) {
                            results.add(position.add(x, y, z));
                        }
                    }
                }
            }

            return results;
        }
    }

    private record FixedPlacementModifier(List<BlockVec> positions) implements PlacementModifier {
        private FixedPlacementModifier(List<BlockVec> positions) {
            this.positions = List.copyOf(positions);
        }

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var chunkX = Math.floorDiv(position.blockX(), 16);
            var chunkZ = Math.floorDiv(position.blockZ(), 16);
            var results = new ArrayList<BlockVec>();

            for (var fixedPosition : this.positions) {
                if (Math.floorDiv(fixedPosition.blockX(), 16) == chunkX
                        && Math.floorDiv(fixedPosition.blockZ(), 16) == chunkZ) {
                    results.add(fixedPosition);
                }
            }

            return results;
        }
    }

    private record NoiseBasedCountModifier(
            int noiseToCountRatio, double noiseFactor, double noiseOffset)
            implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var noiseValue =
                    BIOME_INFO_NOISE.getValue(
                            (double) position.blockX() / this.noiseFactor,
                            (double) position.blockZ() / this.noiseFactor);
            var count =
                    (int)
                            Math.ceil(
                                    (noiseValue + this.noiseOffset)
                                            * (double) this.noiseToCountRatio);
            if (count <= 0) {
                return List.of();
            }

            var results = new ArrayList<BlockVec>(count);
            for (var countIndex = 0; countIndex < count; countIndex++) {
                results.add(position);
            }
            return results;
        }
    }

    private record NoiseThresholdCountModifier(double noiseLevel, int belowNoise, int aboveNoise)
            implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var noiseValue =
                    BIOME_INFO_NOISE.getValue(
                            (double) position.blockX() / 200.0D,
                            (double) position.blockZ() / 200.0D);
            var count = noiseValue < this.noiseLevel ? this.belowNoise : this.aboveNoise;
            if (count <= 0) {
                return List.of();
            }

            var results = new ArrayList<BlockVec>(count);
            for (var countIndex = 0; countIndex < count; countIndex++) {
                results.add(position);
            }
            return results;
        }
    }

    private record CountOnEveryLayerModifier(IntProvider count) implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var results = new ArrayList<BlockVec>();
            var layer = 0;
            var found = false;

            do {
                found = false;
                // Vanilla resamples the count on every loop check
                for (var countIndex = 0; countIndex < this.count.sample(random); countIndex++) {
                    var x = position.blockX() + random.nextInt(16);
                    var z = position.blockZ() + random.nextInt(16);
                    var topY =
                            context.getHeight(PlacementContext.HeightmapType.MOTION_BLOCKING, x, z);
                    var y = findOnGroundYPosition(context, x, topY, z, layer);
                    if (y != Integer.MAX_VALUE) {
                        results.add(new BlockVec(x, y, z));
                        found = true;
                    }
                }

                layer++;
            } while (found);

            return results;
        }

        /**
         * Vanilla {@code findOnGroundYPosition}: walks down from the heightmap top and returns the
         * air block above the {@code targetLayer}-th solid-under-empty surface.
         */
        private static int findOnGroundYPosition(
                PlacementContext context, int x, int startY, int z, int targetLayer) {
            var accessor = context.accessor();
            var layersFound = 0;
            var current = accessor.getBlock(x, startY, z);

            for (var y = startY; y >= context.minY() + 1; y--) {
                var below = accessor.getBlock(x, y - 1, z);
                if (!isEmpty(below) && isEmpty(current) && !below.compare(Block.BEDROCK)) {
                    if (layersFound == targetLayer) {
                        return y;
                    }
                    layersFound++;
                }
                current = below;
            }

            return Integer.MAX_VALUE;
        }

        private static boolean isEmpty(Block block) {
            return block.air() || block.compare(Block.WATER) || block.compare(Block.LAVA);
        }
    }

    private record EnvironmentScanModifier(
            Direction direction,
            BlockPredicate targetCondition,
            BlockPredicate allowedSearchCondition,
            int maxSteps)
            implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var mutablePosition = position;
            if (!this.allowedSearchCondition.test(context, mutablePosition)) {
                if (debugFilterAt(position)) {
                    System.out.println(
                            "ESCANORIGIN "
                                    + context.currentFeature()
                                    + " origin="
                                    + position
                                    + " block="
                                    + context.accessor()
                                            .getBlock(
                                                    position.blockX(),
                                                    position.blockY(),
                                                    position.blockZ())
                                            .name());
                }
                return List.of();
            }

            for (var step = 0; step < this.maxSteps; step++) {
                if (this.targetCondition.test(context, mutablePosition)) {
                    return List.of(mutablePosition);
                }

                mutablePosition =
                        mutablePosition.add(
                                this.direction.stepX, this.direction.stepY, this.direction.stepZ);
                if (!context.inWorldBounds(mutablePosition)) {
                    if (debugFilterAt(position)) {
                        System.out.println(
                                "ESCANBOUNDS " + context.currentFeature() + " origin=" + position);
                    }
                    return List.of();
                }

                if (!this.allowedSearchCondition.test(context, mutablePosition)) {
                    break;
                }
            }

            if (this.targetCondition.test(context, mutablePosition)) {
                return List.of(mutablePosition);
            }
            if (debugFilterAt(position)) {
                System.out.println(
                        "ESCANREJ "
                                + context.currentFeature()
                                + " origin="
                                + position
                                + " stopped="
                                + mutablePosition
                                + " block="
                                + context.accessor()
                                        .getBlock(
                                                mutablePosition.blockX(),
                                                mutablePosition.blockY(),
                                                mutablePosition.blockZ())
                                        .name());
            }
            return List.of();
        }
    }

    private record SurfaceRelativeThresholdFilterModifier(
            PlacementContext.HeightmapType heightmapType, int minInclusive, int maxInclusive)
            implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var surfaceY =
                    context.getHeight(this.heightmapType, position.blockX(), position.blockZ());
            var minY = (long) surfaceY + this.minInclusive;
            var maxY = (long) surfaceY + this.maxInclusive;
            if (position.blockY() >= minY && position.blockY() <= maxY) {
                return List.of(position);
            }

            if (debugFilterAt(position)) {
                System.out.println(
                        "THRESHREJ "
                                + context.currentFeature()
                                + " at "
                                + position
                                + " surface="
                                + surfaceY
                                + " type="
                                + this.heightmapType);
            }
            return List.of();
        }
    }

    private record SurfaceWaterDepthFilterModifier(int maxWaterDepth) implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var oceanFloor =
                    context.getHeight(
                            PlacementContext.HeightmapType.OCEAN_FLOOR,
                            position.blockX(),
                            position.blockZ());
            var worldSurface =
                    context.getHeight(
                            PlacementContext.HeightmapType.WORLD_SURFACE,
                            position.blockX(),
                            position.blockZ());
            return worldSurface - oceanFloor <= this.maxWaterDepth ? List.of(position) : List.of();
        }
    }

    private record BlockPredicateFilterModifier(BlockPredicate predicate)
            implements PlacementModifier {

        @Override
        public List<BlockVec> apply(
                PlacementContext context, RandomSource random, BlockVec position) {
            var pass = this.predicate.test(context, position);
            if (!pass && debugFilterAt(position)) {
                System.out.println(
                        "FILTERREJ "
                                + context.currentFeature()
                                + " at "
                                + position
                                + " block="
                                + context.accessor()
                                        .getBlock(
                                                position.blockX(),
                                                position.blockY(),
                                                position.blockZ())
                                        .name());
            }
            return pass ? List.of(position) : List.of();
        }
    }

    public interface BlockPredicate {
        BlockPredicate ALWAYS_TRUE = (context, position) -> true;
        BlockPredicate NEVER = (context, position) -> false;

        boolean test(PlacementContext context, BlockVec position);
    }

    private record MatchingBlocksPredicate(Set<Key> blocks, BlockVec offset)
            implements BlockPredicate {
        private MatchingBlocksPredicate(Set<Key> blocks, BlockVec offset) {
            this.blocks = Set.copyOf(blocks);
            this.offset = offset;
        }

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            var targetPosition =
                    position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ());
            var block = context.accessor().getBlock(targetPosition);
            return this.blocks.contains(block.key());
        }
    }

    private record MatchingFluidsPredicate(Set<Key> fluids, BlockVec offset)
            implements BlockPredicate {
        private MatchingFluidsPredicate(Set<Key> fluids, BlockVec offset) {
            this.fluids = Set.copyOf(fluids);
            this.offset = offset;
        }

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            var targetPosition =
                    position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ());
            var block = context.accessor().getBlock(targetPosition);
            // Vanilla tests the block's FluidState, which is water for
            // waterlogged blocks too (fences, slabs, sea pickles, and so
            // on) and for inherently waterlogged blocks like seagrass and
            // kelp, not only for the literal water block.
            if (this.fluids.contains(Key.key("minecraft:water"))
                    && rocks.minestom.worldgen.feature.WaterStates.hasWaterFluid(block)) {
                return true;
            }
            return this.fluids.contains(Key.key("minecraft:lava")) && block.compare(Block.LAVA);
        }
    }

    private record WouldSurvivePredicate(Block state, BlockVec offset, BlockSupports supports)
            implements BlockPredicate {

        private static boolean isVegetationFamily(String key) {
            return switch (key) {
                case "minecraft:short_grass",
                        "minecraft:fern",
                        "minecraft:tall_grass",
                        "minecraft:large_fern",
                        "minecraft:firefly_bush",
                        "minecraft:bush",
                        "minecraft:dandelion",
                        "minecraft:poppy",
                        "minecraft:blue_orchid",
                        "minecraft:allium",
                        "minecraft:azure_bluet",
                        "minecraft:red_tulip",
                        "minecraft:orange_tulip",
                        "minecraft:white_tulip",
                        "minecraft:pink_tulip",
                        "minecraft:oxeye_daisy",
                        "minecraft:cornflower",
                        "minecraft:lily_of_the_valley",
                        "minecraft:pink_petals",
                        "minecraft:wildflowers",
                        "minecraft:closed_eyeblossom",
                        "minecraft:open_eyeblossom" ->
                        true;
                default -> false;
            };
        }

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            // Vanilla evaluates state.canSurvive(level, pos): survival only
            // looks at the supporting block, never at the target content
            var targetPosition =
                    position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ());
            var below = context.accessor().getBlock(targetPosition.sub(0, 1, 0));

            // 26.x moved most plant survival rules to per-block
            // supports_<name> block tags; prefer those when present
            var supported = this.supports.supportsOf(this.state);
            if (supported != null) {
                return supported.contains(below.key());
            }

            if (this.state.compare(Block.CACTUS)) {
                return below.compare(Block.SAND)
                        || below.compare(Block.RED_SAND)
                        || below.compare(Block.CACTUS);
            }

            // Vanilla MushroomBlock.canSurvive: overriding floor or raw
            // brightness below 13 - during generation only a
            // not-yet-generated chunk reads bright (15 everywhere)
            if (this.state.compare(Block.RED_MUSHROOM)
                    || this.state.compare(Block.BROWN_MUSHROOM)) {
                if (this.supports.isInTag(
                        "minecraft:overrides_mushroom_light_requirement", below)) {
                    return true;
                }
                if (context.accessor()
                                instanceof
                                rocks.minestom.worldgen.feature.GenerationUnitAdapter adapter
                        && adapter.fullBrightAtGeneration(
                                targetPosition.blockX(), targetPosition.blockZ())) {
                    return false;
                }
                return below.solid();
            }

            if (this.state.compare(Block.MANGROVE_PROPAGULE)) {
                return this.supports.isInTag("minecraft:supports_vegetation", below)
                        || below.compare(Block.CLAY);
            }

            if (this.state.name().endsWith("_sapling") || this.state.compare(Block.BAMBOO)) {
                return this.supports.isInTag("minecraft:supports_vegetation", below);
            }

            // VegetationBlock family (grasses, flowers, firefly bush):
            // vanilla mayPlaceOn is the supports_vegetation tag, so a
            // firefly bush anchor over a sandy river bank must fail here
            if (isVegetationFamily(this.state.name())) {
                return this.supports.isInTag("minecraft:supports_vegetation", below);
            }

            // DryVegetationBlock family: supports_dry_vegetation (sand,
            // terracotta plus the vegetation substrates)
            var stateKey = this.state.name();
            if (stateKey.equals("minecraft:dead_bush")
                    || stateKey.equals("minecraft:short_dry_grass")
                    || stateKey.equals("minecraft:tall_dry_grass")) {
                return this.supports.isInTag("minecraft:supports_dry_vegetation", below);
            }

            return below.solid();
        }
    }

    private record AllOfPredicate(List<BlockPredicate> predicates) implements BlockPredicate {
        private AllOfPredicate(List<BlockPredicate> predicates) {
            this.predicates = List.copyOf(predicates);
        }

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            for (var predicate : this.predicates) {
                if (!predicate.test(context, position)) {
                    return false;
                }
            }
            return true;
        }
    }

    private record AnyOfPredicate(List<BlockPredicate> predicates) implements BlockPredicate {
        private AnyOfPredicate(List<BlockPredicate> predicates) {
            this.predicates = List.copyOf(predicates);
        }

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            for (var predicate : this.predicates) {
                if (predicate.test(context, position)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record NotPredicate(BlockPredicate predicate) implements BlockPredicate {

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            return !this.predicate.test(context, position);
        }
    }

    private record InsideWorldBoundsPredicate(BlockVec offset) implements BlockPredicate {

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            return context.inWorldBounds(
                    position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ()));
        }
    }

    private record SolidPredicate(BlockVec offset) implements BlockPredicate {

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            var block =
                    context.accessor()
                            .getBlock(
                                    position.add(
                                            this.offset.blockX(),
                                            this.offset.blockY(),
                                            this.offset.blockZ()));
            return block.solid();
        }
    }

    private record ReplaceablePredicate(BlockVec offset) implements BlockPredicate {

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            return context.accessor()
                    .getBlock(
                            position.add(
                                    this.offset.blockX(),
                                    this.offset.blockY(),
                                    this.offset.blockZ()))
                    .replaceable();
        }
    }

    private record HeightRangePredicate(HeightAnchor minInclusive, HeightAnchor maxInclusive)
            implements BlockPredicate {

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            return position.blockY() >= this.minInclusive.resolveY(context)
                    && position.blockY() <= this.maxInclusive.resolveY(context);
        }
    }

    private record VolumeMatchPredicate(BlockVec min, BlockVec max, BlockPredicate match)
            implements BlockPredicate {
        private VolumeMatchPredicate {
            if (min.blockX() > max.blockX()
                    || min.blockY() > max.blockY()
                    || min.blockZ() > max.blockZ()) {
                throw new IllegalArgumentException("min bound cannot be larger than max bound");
            }
        }

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            for (var x = this.min.blockX(); x <= this.max.blockX(); x++) {
                for (var z = this.min.blockZ(); z <= this.max.blockZ(); z++) {
                    for (var y = this.min.blockY(); y <= this.max.blockY(); y++) {
                        if (!this.match.test(context, position.add(x, y, z))) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }
    }

    private record HasSturdyFacePredicate(Direction direction, BlockVec offset)
            implements BlockPredicate {

        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            // Vanilla's BlockState.isFaceSturdy (default SupportType.FULL),
            // approximated with the collision shape like the other face checks.
            var targetPosition =
                    position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ());
            var block = context.accessor().getBlock(targetPosition);
            return block.collisionShape().isFaceFull(this.direction.blockFace());
        }
    }

    private enum Direction {
        UP(0, 1, 0),
        DOWN(0, -1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        private final int stepX;
        private final int stepY;
        private final int stepZ;

        Direction(int stepX, int stepY, int stepZ) {
            this.stepX = stepX;
            this.stepY = stepY;
            this.stepZ = stepZ;
        }

        private static Direction fromString(String value) {
            return Direction.valueOf(value.toUpperCase());
        }

        private BlockFace blockFace() {
            return switch (this) {
                case UP -> BlockFace.TOP;
                case DOWN -> BlockFace.BOTTOM;
                case NORTH -> BlockFace.NORTH;
                case SOUTH -> BlockFace.SOUTH;
                case WEST -> BlockFace.WEST;
                case EAST -> BlockFace.EAST;
            };
        }
    }

    private interface HeightProvider {
        int sample(rocks.minestom.worldgen.random.RandomSource random, PlacementContext context);

        static HeightProvider parse(JsonObject object) {
            if (!object.has("type")) {
                // Bare vertical anchor, e.g. {"absolute": 62}
                return new ConstantAnchorHeightProvider(HeightAnchor.fromJson(object));
            }

            var type = Key.key(object.get("type").getAsString()).asString();
            return switch (type) {
                case "minecraft:constant" ->
                        new ConstantAnchorHeightProvider(
                                HeightAnchor.fromJson(object.get("value")));
                case "minecraft:uniform" ->
                        new UniformHeightProvider(
                                HeightAnchor.fromJson(object.get("min_inclusive")),
                                HeightAnchor.fromJson(object.get("max_inclusive")));
                case "minecraft:trapezoid" ->
                        new TrapezoidHeightProvider(
                                HeightAnchor.fromJson(object.get("min_inclusive")),
                                HeightAnchor.fromJson(object.get("max_inclusive")),
                                object.has("plateau") ? object.get("plateau").getAsInt() : 0);
                case "minecraft:biased_to_bottom" ->
                        new BiasedToBottomHeightProvider(
                                HeightAnchor.fromJson(object.get("min_inclusive")),
                                HeightAnchor.fromJson(object.get("max_inclusive")),
                                object.has("inner") ? object.get("inner").getAsInt() : 1);
                case "minecraft:very_biased_to_bottom" ->
                        new VeryBiasedToBottomHeightProvider(
                                HeightAnchor.fromJson(object.get("min_inclusive")),
                                HeightAnchor.fromJson(object.get("max_inclusive")),
                                object.has("inner") ? object.get("inner").getAsInt() : 1);
                case "minecraft:weighted_list" -> {
                    var entries = new ArrayList<WeightedListHeightProvider.Entry>();
                    for (var element : object.getAsJsonArray("distribution")) {
                        var entry = element.getAsJsonObject();
                        entries.add(
                                new WeightedListHeightProvider.Entry(
                                        parse(entry.getAsJsonObject("data")),
                                        entry.get("weight").getAsInt()));
                    }
                    yield new WeightedListHeightProvider(List.copyOf(entries));
                }
                default -> throw new IllegalArgumentException("Unknown height provider " + type);
            };
        }
    }

    private record WeightedListHeightProvider(List<Entry> distribution) implements HeightProvider {
        private WeightedListHeightProvider {
            if (distribution.isEmpty()) {
                throw new IllegalArgumentException("weighted_list needs a distribution");
            }
        }

        @Override
        public int sample(RandomSource random, PlacementContext context) {
            var total = 0;
            for (var entry : this.distribution) {
                total += entry.weight();
            }

            var remaining = random.nextInt(total);
            for (var entry : this.distribution) {
                remaining -= entry.weight();
                if (remaining < 0) {
                    return entry.provider().sample(random, context);
                }
            }

            throw new IllegalStateException("Weighted height distribution exhausted");
        }

        private record Entry(HeightProvider provider, int weight) {}
    }

    private record ConstantAnchorHeightProvider(HeightAnchor anchor) implements HeightProvider {
        @Override
        public int sample(
                rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            return this.anchor.resolveY(context);
        }
    }

    private record UniformHeightProvider(HeightAnchor minInclusive, HeightAnchor maxInclusive)
            implements HeightProvider {
        @Override
        public int sample(
                rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            var minY = this.minInclusive.resolveY(context);
            var maxY = this.maxInclusive.resolveY(context);
            if (minY > maxY) {
                return minY;
            }
            return minY + random.nextInt(maxY - minY + 1);
        }
    }

    private record TrapezoidHeightProvider(
            HeightAnchor minInclusive, HeightAnchor maxInclusive, int plateau)
            implements HeightProvider {
        @Override
        public int sample(
                rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            var minY = this.minInclusive.resolveY(context);
            var maxY = this.maxInclusive.resolveY(context);
            if (minY > maxY) {
                return minY;
            }

            var range = maxY - minY;
            if (this.plateau >= range) {
                return minY + random.nextInt(range + 1);
            }

            var plateauStart = (range - this.plateau) / 2;
            var plateauEnd = range - plateauStart;
            return minY + random.nextInt(plateauEnd + 1) + random.nextInt(plateauStart + 1);
        }
    }

    private record BiasedToBottomHeightProvider(
            HeightAnchor minInclusive, HeightAnchor maxInclusive, int inner)
            implements HeightProvider {
        @Override
        public int sample(
                rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            var minY = this.minInclusive.resolveY(context);
            var maxY = this.maxInclusive.resolveY(context);
            if (maxY - minY - this.inner + 1 <= 0) {
                return minY;
            }

            var limit = random.nextInt(maxY - minY - this.inner + 1);
            return random.nextInt(limit + this.inner) + minY;
        }
    }

    private record VeryBiasedToBottomHeightProvider(
            HeightAnchor minInclusive, HeightAnchor maxInclusive, int inner)
            implements HeightProvider {
        @Override
        public int sample(
                rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            var minY = this.minInclusive.resolveY(context);
            var maxY = this.maxInclusive.resolveY(context);
            if (maxY - minY - this.inner + 1 <= 0) {
                return minY;
            }

            var upperInclusive = nextIntBetween(random, minY + this.inner, maxY);
            var biasedUpperInclusive = nextIntBetween(random, minY, upperInclusive - 1);
            return nextIntBetween(random, minY, biasedUpperInclusive - 1 + this.inner);
        }

        // Vanilla Mth.nextInt: no draw when the range is empty or a single value
        private static int nextIntBetween(
                rocks.minestom.worldgen.random.RandomSource random,
                int minInclusive,
                int maxInclusive) {
            return minInclusive >= maxInclusive
                    ? minInclusive
                    : random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
        }
    }
}
