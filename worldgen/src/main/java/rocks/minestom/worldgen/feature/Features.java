package rocks.minestom.worldgen.feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.feature.configurations.*;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public final class Features {
    private Features() {}

    public static final TreeFeature TREE = new TreeFeature();
    public static final FallenTreeFeature FALLEN_TREE = new FallenTreeFeature();
    public static final MultifaceGrowthFeature MULTIFACE_GROWTH = new MultifaceGrowthFeature();
    public static final HugeBrownMushroomFeature HUGE_BROWN_MUSHROOM =
            new HugeBrownMushroomFeature();
    public static final HugeRedMushroomFeature HUGE_RED_MUSHROOM = new HugeRedMushroomFeature();
    public static final BlockPileFeature BLOCK_PILE = new BlockPileFeature();
    public static final ChorusPlantFeature CHORUS_PLANT = new ChorusPlantFeature();
    public static final EndPlatformFeature END_PLATFORM = new EndPlatformFeature();
    public static final EndPodiumFeature END_PODIUM = new EndPodiumFeature();
    public static final EndSpikeFeature END_SPIKE = new EndSpikeFeature();
    public static final SimpleBlockFeature SIMPLE_BLOCK = new SimpleBlockFeature();
    public static final SimpleRandomSelectorFeature SIMPLE_RANDOM_SELECTOR =
            new SimpleRandomSelectorFeature();
    public static final DiskFeature DISK = new DiskFeature();
    public static final BlockColumnFeature BLOCK_COLUMN = new BlockColumnFeature();
    public static final VegetationPatchFeature VEGETATION_PATCH = new VegetationPatchFeature();
    public static final WaterloggedVegetationPatchFeature WATERLOGGED_VEGETATION_PATCH =
            new WaterloggedVegetationPatchFeature();
    public static final OreFeature ORE = new OreFeature();
    public static final ScatteredOreFeature SCATTERED_ORE = new ScatteredOreFeature();
    public static final NoOpFeature NO_OP = new NoOpFeature();
    public static final BlockBlobFeature BLOCK_BLOB = new BlockBlobFeature();
    public static final SpikeFeature SPIKE = new SpikeFeature();
    public static final SpeleothemFeature SPELEOTHEM = new SpeleothemFeature();
    public static final SpeleothemClusterFeature SPELEOTHEM_CLUSTER =
            new SpeleothemClusterFeature();
    public static final LargeDripstoneFeature LARGE_DRIPSTONE = new LargeDripstoneFeature();
    public static final LakeFeature LAKE = new LakeFeature();
    public static final MonsterRoomFeature MONSTER_ROOM = new MonsterRoomFeature();
    public static final HugeFungusFeature HUGE_FUNGUS = new HugeFungusFeature();
    public static final VinesFeature VINES = new VinesFeature();
    public static final SpringFeature SPRING = new SpringFeature();
    public static final DeltaFeature DELTA_FEATURE = new DeltaFeature();
    public static final ReplaceBlobsFeature NETHERRACK_REPLACE_BLOBS = new ReplaceBlobsFeature();
    public static final FreezeTopLayerFeature FREEZE_TOP_LAYER = new FreezeTopLayerFeature();
    public static final GeodeFeature GEODE = new GeodeFeature();
    public static final IcebergFeature ICEBERG = new IcebergFeature();
    public static final BlueIceFeature BLUE_ICE = new BlueIceFeature();
    public static final CoralTreeFeature CORAL_TREE = new CoralTreeFeature();
    public static final CoralClawFeature CORAL_CLAW = new CoralClawFeature();
    public static final BambooFeature BAMBOO = new BambooFeature();
    public static final FillLayerFeature FILL_LAYER = new FillLayerFeature();
    public static final BonusChestFeature BONUS_CHEST = new BonusChestFeature();
    public static final FossilFeature FOSSIL = new FossilFeature();
    public static final UnderwaterMagmaFeature UNDERWATER_MAGMA = new UnderwaterMagmaFeature();
    public static final SculkPatchFeature SCULK_PATCH = new SculkPatchFeature();
    public static final RootSystemFeature ROOT_SYSTEM = new RootSystemFeature();
    public static final EndIslandFeature END_ISLAND = new EndIslandFeature();
    public static final EndGatewayFeature END_GATEWAY = new EndGatewayFeature();
    public static final VoidStartPlatformFeature VOID_START_PLATFORM =
            new VoidStartPlatformFeature();
    public static final OverlayFeature OVERLAY = new OverlayFeature();
    public static final SingleBlockPillarFeature SINGLE_BLOCK_PILLAR =
            new SingleBlockPillarFeature();
    public static final ProjectedRandomPatchySquareFeature PROJECTED_RANDOM_PATCHY_SQUARE =
            new ProjectedRandomPatchySquareFeature();
    public static final RandomNeighborSpreadFeature RANDOM_NEIGHBOR_SPREAD =
            new RandomNeighborSpreadFeature();
    public static final SteppedColumnClusterFeature STEPPED_COLUMN_CLUSTER =
            new SteppedColumnClusterFeature();
    public static final ReplaceBlockFeature REPLACE_SINGLE_BLOCK = new ReplaceBlockFeature();
    public static final TemplateFeature TEMPLATE = new TemplateFeature();

    /**
     * Feature loader owning the parse currently in progress, so nested configurations can resolve
     * by-id data and capture the loader's block supports.
     */
    private static final ThreadLocal<FeatureLoader> CURRENT_LOADER = new ThreadLocal<>();

    public static FeatureLoader currentLoader() {
        return CURRENT_LOADER.get();
    }

    /** Block support tags of the parse in progress; none when parsing without a loader. */
    public static BlockSupports currentBlockSupports() {
        var loader = CURRENT_LOADER.get();
        return loader != null ? loader.blockSupports() : BlockSupports.NONE;
    }

    public static void currentLoader(FeatureLoader loader) {
        if (loader == null) {
            CURRENT_LOADER.remove();
        } else {
            CURRENT_LOADER.set(loader);
        }
    }

    public static ConfiguredFeature<?> parseConfiguredFeature(JsonElement json) {
        return parseConfiguredFeature(json, null);
    }

    public static ConfiguredFeature<?> parseConfiguredFeature(
            JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Feature must be a JSON object");
        }

        // Nested parses (inline placed features, state provider rules) resolve block tags through
        // this thread-local; inherit it when the caller did not pass a manager explicitly.
        var previousBlockTags = PlacementModifiers.currentBlockTags();
        var effectiveBlockTags = blockTags != null ? blockTags : previousBlockTags;
        PlacementModifiers.currentBlockTags(effectiveBlockTags);
        try {
            return parse(json.getAsJsonObject(), effectiveBlockTags);
        } finally {
            PlacementModifiers.currentBlockTags(previousBlockTags);
        }
    }

    private static ConfiguredFeature<?> parse(JsonObject obj, BlockTagManager blockTags) {
        var type = obj.get("type").getAsString();

        return switch (type) {
            case "minecraft:tree" -> of(TREE, decode(TreeConfiguration.CODEC, obj));
            case "minecraft:fallen_tree" ->
                    of(FALLEN_TREE, decode(FallenTreeConfiguration.CODEC, obj));
            case "minecraft:multiface_growth" ->
                    of(MULTIFACE_GROWTH, MultifaceGrowthConfiguration.fromJson(obj, blockTags));
            case "minecraft:huge_brown_mushroom" ->
                    of(
                            HUGE_BROWN_MUSHROOM,
                            HugeMushroomFeatureConfiguration.fromJson(obj, blockTags));
            case "minecraft:huge_red_mushroom" ->
                    of(
                            HUGE_RED_MUSHROOM,
                            HugeMushroomFeatureConfiguration.fromJson(obj, blockTags));
            case "minecraft:random_selector" -> {
                var features = new ArrayList<RandomSelectorFeature.WeightedFeature>();
                for (var entryElement : obj.getAsJsonArray("features")) {
                    var entry = entryElement.getAsJsonObject();
                    features.add(
                            new RandomSelectorFeature.WeightedFeature(
                                    entry.get("chance").getAsFloat(),
                                    PlacedFeature.fromJson(entry.get("feature"))));
                }
                yield noConfig(
                        new RandomSelectorFeature(
                                PlacedFeature.fromJson(obj.get("default")), List.copyOf(features)));
            }
            case "minecraft:weighted_random_selector" -> {
                var features = new ArrayList<WeightedRandomSelectorFeature.WeightedPlacedFeature>();
                for (var entryElement : obj.getAsJsonArray("features")) {
                    var entry = entryElement.getAsJsonObject();
                    features.add(
                            new WeightedRandomSelectorFeature.WeightedPlacedFeature(
                                    PlacedFeature.fromJson(entry.get("data")),
                                    entry.get("weight").getAsInt()));
                }
                yield noConfig(new WeightedRandomSelectorFeature(List.copyOf(features)));
            }
            case "minecraft:random_boolean_selector" ->
                    noConfig(
                            new RandomBooleanSelectorFeature(
                                    PlacedFeature.fromJson(obj.get("feature_true")),
                                    PlacedFeature.fromJson(obj.get("feature_false"))));
            case "minecraft:sequence" -> {
                var features = new ArrayList<PlacedFeature>();
                for (var featureElement : obj.getAsJsonArray("features")) {
                    features.add(PlacedFeature.fromJson(featureElement));
                }
                yield noConfig(new SequenceFeature(List.copyOf(features)));
            }
            case "minecraft:simple_random_selector" ->
                    of(
                            SIMPLE_RANDOM_SELECTOR,
                            decode(SimpleRandomSelectorConfiguration.CODEC, obj));
            case "minecraft:overlay" -> of(OVERLAY, OverlayConfiguration.fromJson(obj));
            case "minecraft:block_pile" ->
                    of(BLOCK_PILE, decode(BlockPileConfiguration.CODEC, obj));
            case "minecraft:end_spike" -> of(END_SPIKE, decode(EndSpikeConfiguration.CODEC, obj));
            case "minecraft:end_podium" ->
                    of(END_PODIUM, decode(EndPodiumConfiguration.CODEC, obj));
            case "minecraft:simple_block" ->
                    of(SIMPLE_BLOCK, decode(SimpleBlockConfiguration.CODEC, obj));
            case "minecraft:block_column" ->
                    of(BLOCK_COLUMN, BlockColumnConfiguration.fromJson(obj, blockTags));
            case "minecraft:disk" -> of(DISK, DiskConfiguration.fromJson(obj));
            case "minecraft:ore" -> of(ORE, OreConfiguration.fromJson(obj, blockTags));
            case "minecraft:scattered_ore" ->
                    of(SCATTERED_ORE, OreConfiguration.fromJson(obj, blockTags));
            case "minecraft:block_blob" ->
                    of(BLOCK_BLOB, BlockBlobConfiguration.fromJson(obj, blockTags));
            case "minecraft:spike" -> of(SPIKE, SpikeConfiguration.fromJson(obj, blockTags));
            case "minecraft:speleothem" ->
                    of(SPELEOTHEM, SpeleothemConfiguration.fromJson(obj, blockTags));
            case "minecraft:large_dripstone" ->
                    of(LARGE_DRIPSTONE, LargeDripstoneConfiguration.fromJson(obj, blockTags));
            case "minecraft:speleothem_cluster" ->
                    of(SPELEOTHEM_CLUSTER, SpeleothemClusterConfiguration.fromJson(obj, blockTags));
            case "minecraft:lake" -> of(LAKE, LakeConfiguration.fromJson(obj, blockTags));
            case "minecraft:vegetation_patch" ->
                    of(VEGETATION_PATCH, VegetationPatchConfiguration.fromJson(obj, blockTags));
            case "minecraft:waterlogged_vegetation_patch" ->
                    of(
                            WATERLOGGED_VEGETATION_PATCH,
                            VegetationPatchConfiguration.fromJson(obj, blockTags));
            case "minecraft:huge_fungus" ->
                    of(HUGE_FUNGUS, HugeFungusConfiguration.fromJson(obj, blockTags));
            case "minecraft:spring_feature" ->
                    of(SPRING, SpringConfiguration.fromJson(obj, blockTags));
            case "minecraft:delta_feature" ->
                    of(DELTA_FEATURE, DeltaFeatureConfiguration.fromJson(obj));
            case "minecraft:sculk_patch" -> of(SCULK_PATCH, SculkPatchConfiguration.fromJson(obj));
            case "minecraft:root_system" ->
                    of(ROOT_SYSTEM, RootSystemConfiguration.fromJson(obj, blockTags));
            case "minecraft:underwater_magma" ->
                    of(UNDERWATER_MAGMA, UnderwaterMagmaConfiguration.fromJson(obj));
            case "minecraft:fossil" ->
                    of(FOSSIL, FossilFeatureConfiguration.fromJson(obj, blockTags));
            case "minecraft:bamboo" -> of(BAMBOO, ProbabilityConfiguration.fromJson(obj));
            case "minecraft:fill_layer" -> of(FILL_LAYER, LayerConfiguration.fromJson(obj));
            case "minecraft:iceberg" -> of(ICEBERG, IcebergConfiguration.fromJson(obj));
            case "minecraft:geode" -> of(GEODE, GeodeConfiguration.fromJson(obj, blockTags));
            case "minecraft:end_gateway" -> of(END_GATEWAY, EndGatewayConfiguration.fromJson(obj));
            case "minecraft:netherrack_replace_blobs" ->
                    of(NETHERRACK_REPLACE_BLOBS, ReplaceSphereConfiguration.fromJson(obj));
            case "minecraft:single_block_pillar" ->
                    of(SINGLE_BLOCK_PILLAR, SingleBlockPillarConfiguration.fromJson(obj));
            case "minecraft:projected_random_patchy_square" ->
                    of(
                            PROJECTED_RANDOM_PATCHY_SQUARE,
                            ProjectedRandomPatchySquareConfiguration.fromJson(obj));
            case "minecraft:random_neighbor_spread" ->
                    of(
                            RANDOM_NEIGHBOR_SPREAD,
                            RandomNeighborSpreadConfiguration.fromJson(obj, blockTags));
            case "minecraft:stepped_column_cluster" ->
                    of(
                            STEPPED_COLUMN_CLUSTER,
                            SteppedColumnClusterConfiguration.fromJson(obj, blockTags));
            case "minecraft:replace_single_block" ->
                    of(REPLACE_SINGLE_BLOCK, ReplaceBlockConfiguration.fromJson(obj, blockTags));
            case "minecraft:template" ->
                    of(TEMPLATE, TemplateConfiguration.fromJson(obj, blockTags));
            case "minecraft:chorus_plant" -> of(CHORUS_PLANT, none());
            case "minecraft:end_platform" -> of(END_PLATFORM, none());
            case "minecraft:end_island" -> of(END_ISLAND, none());
            case "minecraft:void_start_platform" -> of(VOID_START_PLATFORM, none());
            case "minecraft:monster_room" -> of(MONSTER_ROOM, none());
            case "minecraft:freeze_top_layer" -> of(FREEZE_TOP_LAYER, none());
            case "minecraft:blue_ice" -> of(BLUE_ICE, none());
            case "minecraft:bonus_chest" -> of(BONUS_CHEST, none());
            case "minecraft:vines" -> of(VINES, none());
            case "minecraft:coral_tree" -> of(CORAL_TREE, none());
            case "minecraft:coral_claw" -> of(CORAL_CLAW, none());
            case "minecraft:no_op" -> of(NO_OP, none());
            default -> throw new IllegalArgumentException("Unknown feature type " + type);
        };
    }

    private static <C extends FeatureConfiguration> ConfiguredFeature<C> of(
            Feature<C> feature, C config) {
        return new ConfiguredFeature<>(feature, config);
    }

    private static ConfiguredFeature<FeatureConfiguration> noConfig(
            Feature<FeatureConfiguration> feature) {
        return new ConfiguredFeature<>(feature, null);
    }

    private static NoneFeatureConfiguration none() {
        return new NoneFeatureConfiguration();
    }

    private static <C> C decode(Codec<C> codec, JsonObject obj) {
        return codec.decode(Transcoder.JSON, obj).orElseThrow();
    }
}
