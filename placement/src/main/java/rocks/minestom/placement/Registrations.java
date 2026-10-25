package rocks.minestom.placement;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockManager;

public final class Registrations {
    private Registrations() {}

    public static void registerAllVanilla(BlockManager blockManager) {
        registerHandlers(blockManager);
        registerByTag(blockManager, AxisPlacementRule::new, "minecraft:logs");
        Utility.registerPlacementRules(
                blockManager,
                AxisPlacementRule::new,
                Block.HAY_BLOCK,
                Block.DEEPSLATE,
                Block.INFESTED_DEEPSLATE,
                Block.MUDDY_MANGROVE_ROOTS,
                Block.BAMBOO_BLOCK,
                Block.STRIPPED_BAMBOO_BLOCK,
                Block.BASALT,
                Block.POLISHED_BASALT,
                Block.QUARTZ_PILLAR,
                Block.PURPUR_PILLAR,
                Block.BONE_BLOCK,
                Block.OCHRE_FROGLIGHT,
                Block.VERDANT_FROGLIGHT,
                Block.PEARLESCENT_FROGLIGHT);
        Utility.registerPlacementRules(
                blockManager, CreakingHeartPlacementRule::new, Block.CREAKING_HEART);

        registerByTag(blockManager, SlabPlacementRule::new, "minecraft:slabs");
        registerByTag(blockManager, StairPlacementRule::new, "minecraft:stairs");
        registerByTag(blockManager, TrapdoorPlacementRule::new, "minecraft:trapdoors");
        registerByTag(blockManager, DoorPlacementRule::new, "minecraft:doors");
        registerByTag(blockManager, FencePlacementRule::new, "minecraft:fences");
        registerByTag(blockManager, FenceGatePlacementRule::new, "minecraft:fence_gates");
        registerByTag(blockManager, WallPlacementRule::new, "minecraft:walls");
        registerByTag(blockManager, PressurePlatePlacementRule::new, "minecraft:pressure_plates");
        registerByTag(
                blockManager,
                block -> new FaceAttachedPlacementRule(block, ButtonBlockHandler.INSTANCE),
                "minecraft:buttons");
        registerByTag(blockManager, RailPlacementRule::new, "minecraft:rails");
        registerByTag(blockManager, BedPlacementRule::new, "minecraft:beds");
        Utility.registerPlacementRules(blockManager, BedPlacementRule::new, Block.STRAW_BED);
        registerByTag(blockManager, BannerPlacementRule::new, "minecraft:banners");
        registerByTag(blockManager, StandingSignPlacementRule::new, "minecraft:standing_signs");
        registerByTag(
                blockManager,
                block -> new WallMountedPlacementRule(block, SignBlockHandler.INSTANCE),
                "minecraft:wall_signs");
        registerByTag(blockManager, WallMountedPlacementRule::new, "minecraft:wall_banners");
        registerByTag(
                blockManager,
                CeilingHangingSignPlacementRule::new,
                "minecraft:ceiling_hanging_signs");
        registerByTag(
                blockManager, WallHangingSignPlacementRule::new, "minecraft:wall_hanging_signs");
        registerByTag(blockManager, PlantPlacementRule::new, "minecraft:saplings");
        registerByTag(blockManager, PlantPlacementRule::new, "minecraft:small_flowers");
        registerByTag(blockManager, TallPlantPlacementRule::new, "minecraft:tall_flowers");
        registerByTag(blockManager, CropPlacementRule::new, "minecraft:crops");
        Utility.registerPlacementRules(
                blockManager,
                block -> new CropPlacementRule(block, BerryBlockHandler.INSTANCE),
                Block.SWEET_BERRY_BUSH);
        registerByTag(blockManager, LeavesPlacementRule::new, "minecraft:leaves");
        registerByTag(blockManager, ShulkerBoxPlacementRule::new, "minecraft:shulker_boxes");
        registerByTag(blockManager, CarpetPlacementRule::new, "minecraft:wool_carpets");
        Utility.registerPlacementRules(blockManager, CarpetPlacementRule::new, Block.MOSS_CARPET);
        registerByTag(blockManager, CoralPlantPlacementRule::new, "minecraft:corals");
        registerByTag(blockManager, CoralWallFanPlacementRule::new, "minecraft:wall_corals");

        Utility.registerPlacementRules(
                blockManager,
                HorizontalFacingPlacementRule::new,
                Block.CARVED_PUMPKIN,
                Block.JACK_O_LANTERN,
                Block.WHITE_GLAZED_TERRACOTTA,
                Block.LIGHT_GRAY_GLAZED_TERRACOTTA,
                Block.GRAY_GLAZED_TERRACOTTA,
                Block.BLACK_GLAZED_TERRACOTTA,
                Block.BROWN_GLAZED_TERRACOTTA,
                Block.RED_GLAZED_TERRACOTTA,
                Block.ORANGE_GLAZED_TERRACOTTA,
                Block.YELLOW_GLAZED_TERRACOTTA,
                Block.LIME_GLAZED_TERRACOTTA,
                Block.GREEN_GLAZED_TERRACOTTA,
                Block.CYAN_GLAZED_TERRACOTTA,
                Block.LIGHT_BLUE_GLAZED_TERRACOTTA,
                Block.BLUE_GLAZED_TERRACOTTA,
                Block.PURPLE_GLAZED_TERRACOTTA,
                Block.MAGENTA_GLAZED_TERRACOTTA,
                Block.PINK_GLAZED_TERRACOTTA);
        Utility.registerPlacementRules(
                blockManager,
                block -> new WaterloggedHorizontalFacingPlacementRule(block, false, null),
                Block.DRIED_GHAST);

        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new HorizontalFacingPlacementRule(
                                block, ConsumeInteractionBlockHandler.INSTANCE),
                Block.FURNACE,
                Block.BLAST_FURNACE,
                Block.SMOKER,
                Block.LECTERN,
                Block.LOOM,
                Block.STONECUTTER);
        Utility.registerPlacementRules(
                blockManager,
                HorizontalFacingPlacementRule::new,
                Block.CHISELED_BOOKSHELF,
                Block.VAULT);
        Utility.registerPlacementRules(
                blockManager,
                block -> new HorizontalFacingPlacementRule(block, BeeHiveBlockHandler.INSTANCE),
                Block.BEEHIVE,
                Block.BEE_NEST);
        Utility.registerPlacementRules(
                blockManager,
                block -> new WaterloggedHorizontalFacingPlacementRule(block, true, null),
                Block.CALIBRATED_SCULK_SENSOR);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new DirectionalPlacementRule(
                                block, ConsumeInteractionBlockHandler.INSTANCE),
                Block.BARREL);
        Utility.registerPlacementRules(
                blockManager,
                block -> new HorizontalFacingPlacementRule(block, RepeaterBlockHandler.INSTANCE),
                Block.REPEATER);
        Utility.registerPlacementRules(
                blockManager,
                block -> new HorizontalFacingPlacementRule(block, ComparatorBlockHandler.INSTANCE),
                Block.COMPARATOR);

        Utility.registerPlacementRules(
                blockManager, DirectionalPlacementRule::new, Block.PISTON, Block.STICKY_PISTON);
        Utility.registerPlacementRules(
                blockManager, block -> new DirectionalPlacementRule(block, true), Block.OBSERVER);

        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new DirectionalPlacementRule(
                                block, GameMasterInteractionBlockHandler.INSTANCE),
                Block.COMMAND_BLOCK,
                Block.CHAIN_COMMAND_BLOCK,
                Block.REPEATING_COMMAND_BLOCK);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new DirectionalPlacementRule(
                                block, ConsumeInteractionBlockHandler.INSTANCE),
                Block.DISPENSER,
                Block.DROPPER);

        Utility.registerPlacementRules(
                blockManager,
                ChestPlacementRule::new,
                Block.CHEST,
                Block.TRAPPED_CHEST,
                Block.COPPER_CHEST,
                Block.EXPOSED_COPPER_CHEST,
                Block.WEATHERED_COPPER_CHEST,
                Block.OXIDIZED_COPPER_CHEST,
                Block.WAXED_COPPER_CHEST,
                Block.WAXED_EXPOSED_COPPER_CHEST,
                Block.WAXED_WEATHERED_COPPER_CHEST,
                Block.WAXED_OXIDIZED_COPPER_CHEST);

        Utility.registerPlacementRules(
                blockManager,
                CrossConnectingPlacementRule::new,
                Block.IRON_BARS,
                Block.COPPER_BARS,
                Block.EXPOSED_COPPER_BARS,
                Block.WEATHERED_COPPER_BARS,
                Block.OXIDIZED_COPPER_BARS,
                Block.WAXED_COPPER_BARS,
                Block.WAXED_EXPOSED_COPPER_BARS,
                Block.WAXED_WEATHERED_COPPER_BARS,
                Block.WAXED_OXIDIZED_COPPER_BARS,
                Block.GLASS_PANE,
                Block.WHITE_STAINED_GLASS_PANE,
                Block.LIGHT_GRAY_STAINED_GLASS_PANE,
                Block.GRAY_STAINED_GLASS_PANE,
                Block.BLACK_STAINED_GLASS_PANE,
                Block.BROWN_STAINED_GLASS_PANE,
                Block.RED_STAINED_GLASS_PANE,
                Block.ORANGE_STAINED_GLASS_PANE,
                Block.YELLOW_STAINED_GLASS_PANE,
                Block.LIME_STAINED_GLASS_PANE,
                Block.GREEN_STAINED_GLASS_PANE,
                Block.CYAN_STAINED_GLASS_PANE,
                Block.LIGHT_BLUE_STAINED_GLASS_PANE,
                Block.BLUE_STAINED_GLASS_PANE,
                Block.PURPLE_STAINED_GLASS_PANE,
                Block.MAGENTA_STAINED_GLASS_PANE,
                Block.PINK_STAINED_GLASS_PANE);

        Utility.registerPlacementRules(
                blockManager,
                block -> new FaceAttachedPlacementRule(block, LeverBlockHandler.INSTANCE),
                Block.LEVER);
        blockManager.registerBlockPlacementRule(
                new HandlerAttachingPlacementRule(Block.CAKE, CakeBlockHandler.INSTANCE));
        Utility.registerPlacementRules(
                blockManager,
                block -> new HandlerAttachingPlacementRule(block, CandleCakeBlockHandler.INSTANCE),
                Block.CANDLE_CAKE,
                Block.WHITE_CANDLE_CAKE,
                Block.LIGHT_GRAY_CANDLE_CAKE,
                Block.GRAY_CANDLE_CAKE,
                Block.BLACK_CANDLE_CAKE,
                Block.BROWN_CANDLE_CAKE,
                Block.RED_CANDLE_CAKE,
                Block.ORANGE_CANDLE_CAKE,
                Block.YELLOW_CANDLE_CAKE,
                Block.LIME_CANDLE_CAKE,
                Block.GREEN_CANDLE_CAKE,
                Block.CYAN_CANDLE_CAKE,
                Block.LIGHT_BLUE_CANDLE_CAKE,
                Block.BLUE_CANDLE_CAKE,
                Block.PURPLE_CANDLE_CAKE,
                Block.MAGENTA_CANDLE_CAKE,
                Block.PINK_CANDLE_CAKE);
        blockManager.registerBlockPlacementRule(
                new HandlerAttachingPlacementRule(Block.JUKEBOX, JukeboxBlockHandler.INSTANCE));
        blockManager.registerBlockPlacementRule(
                new HandlerAttachingPlacementRule(Block.COMPOSTER, ComposterBlockHandler.INSTANCE));
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new HandlerAttachingPlacementRule(
                                block, ConsumeInteractionBlockHandler.INSTANCE),
                Block.BEACON,
                Block.BREWING_STAND,
                Block.CARTOGRAPHY_TABLE,
                Block.CRAFTING_TABLE,
                Block.ENCHANTING_TABLE,
                Block.GRINDSTONE,
                Block.SMITHING_TABLE);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new HandlerAttachingPlacementRule(
                                block, GameMasterInteractionBlockHandler.INSTANCE),
                Block.STRUCTURE_BLOCK,
                Block.TEST_BLOCK,
                Block.TEST_INSTANCE_BLOCK);
        blockManager.registerBlockPlacementRule(
                new HandlerAttachingPlacementRule(
                        Block.DAYLIGHT_DETECTOR, DaylightDetectorBlockHandler.INSTANCE));
        blockManager.registerBlockPlacementRule(new NoteBlockPlacementRule(Block.NOTE_BLOCK));
        Utility.registerPlacementRules(
                blockManager,
                block -> new HandlerAttachingPlacementRule(block, FlowerPotBlockHandler.INSTANCE),
                Block.FLOWER_POT,
                Block.POTTED_TORCHFLOWER,
                Block.POTTED_OAK_SAPLING,
                Block.POTTED_SPRUCE_SAPLING,
                Block.POTTED_BIRCH_SAPLING,
                Block.POTTED_JUNGLE_SAPLING,
                Block.POTTED_ACACIA_SAPLING,
                Block.POTTED_CHERRY_SAPLING,
                Block.POTTED_DARK_OAK_SAPLING,
                Block.POTTED_PALE_OAK_SAPLING,
                Block.POTTED_POPLAR_SAPLING,
                Block.POTTED_MANGROVE_PROPAGULE,
                Block.POTTED_FERN,
                Block.POTTED_DANDELION,
                Block.POTTED_GOLDEN_DANDELION,
                Block.POTTED_POPPY,
                Block.POTTED_BLUE_ORCHID,
                Block.POTTED_ALLIUM,
                Block.POTTED_AZURE_BLUET,
                Block.POTTED_RED_TULIP,
                Block.POTTED_ORANGE_TULIP,
                Block.POTTED_WHITE_TULIP,
                Block.POTTED_PINK_TULIP,
                Block.POTTED_OXEYE_DAISY,
                Block.POTTED_CORNFLOWER,
                Block.POTTED_LILY_OF_THE_VALLEY,
                Block.POTTED_WITHER_ROSE,
                Block.POTTED_RED_MUSHROOM,
                Block.POTTED_BROWN_MUSHROOM,
                Block.POTTED_DEAD_BUSH,
                Block.POTTED_CACTUS,
                Block.POTTED_BAMBOO,
                Block.POTTED_CRIMSON_FUNGUS,
                Block.POTTED_WARPED_FUNGUS,
                Block.POTTED_CRIMSON_ROOTS,
                Block.POTTED_WARPED_ROOTS,
                Block.POTTED_AZALEA_BUSH,
                Block.POTTED_FLOWERING_AZALEA_BUSH,
                Block.POTTED_OPEN_EYEBLOSSOM,
                Block.POTTED_CLOSED_EYEBLOSSOM);
        Utility.registerPlacementRules(blockManager, CactusPlacementRule::new, Block.CACTUS);
        Utility.registerPlacementRules(
                blockManager, CactusFlowerPlacementRule::new, Block.CACTUS_FLOWER);
        Utility.registerPlacementRules(blockManager, SugarCanePlacementRule::new, Block.SUGAR_CANE);
        Utility.registerPlacementRules(
                blockManager, MushroomPlacementRule::new, Block.RED_MUSHROOM, Block.BROWN_MUSHROOM);

        Utility.registerPlacementRules(
                blockManager,
                SkullPlacementRule::new,
                Block.SKELETON_SKULL,
                Block.WITHER_SKELETON_SKULL,
                Block.ZOMBIE_HEAD,
                Block.PLAYER_HEAD,
                Block.CREEPER_HEAD,
                Block.DRAGON_HEAD,
                Block.PIGLIN_HEAD);
        Utility.registerPlacementRules(
                blockManager,
                WallSkullPlacementRule::new,
                Block.SKELETON_WALL_SKULL,
                Block.WITHER_SKELETON_WALL_SKULL,
                Block.ZOMBIE_WALL_HEAD,
                Block.PLAYER_WALL_HEAD,
                Block.CREEPER_WALL_HEAD,
                Block.DRAGON_WALL_HEAD,
                Block.PIGLIN_WALL_HEAD);
        Utility.registerPlacementRules(
                blockManager,
                WallTorchPlacementRule::new,
                Block.WALL_TORCH,
                Block.SOUL_WALL_TORCH,
                Block.REDSTONE_WALL_TORCH,
                Block.COPPER_WALL_TORCH);
        Utility.registerPlacementRules(
                blockManager,
                TorchPlacementRule::new,
                Block.TORCH,
                Block.SOUL_TORCH,
                Block.REDSTONE_TORCH,
                Block.COPPER_TORCH);
        Utility.registerPlacementRules(
                blockManager,
                PlantPlacementRule::new,
                Block.BUSH,
                Block.FERN,
                Block.FIREFLY_BUSH,
                Block.RED_SHRUB);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new PlantPlacementRule(block, Key.key("minecraft:supports_crimson_fungus")),
                Block.CRIMSON_FUNGUS);
        Utility.registerPlacementRules(
                blockManager,
                block -> new PlantPlacementRule(block, Key.key("minecraft:supports_warped_fungus")),
                Block.WARPED_FUNGUS);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new PlantPlacementRule(block, Key.key("minecraft:supports_nether_sprouts")),
                Block.NETHER_SPROUTS);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new PlantPlacementRule(block, Key.key("minecraft:supports_dry_vegetation")),
                Block.DEAD_BUSH);
        Utility.registerPlacementRules(
                blockManager,
                block -> new PlantPlacementRule(block, Key.key("minecraft:supports_crimson_roots")),
                Block.CRIMSON_ROOTS);
        Utility.registerPlacementRules(
                blockManager,
                block -> new PlantPlacementRule(block, Key.key("minecraft:supports_warped_roots")),
                Block.WARPED_ROOTS);
        Utility.registerPlacementRules(blockManager, LilyPadPlacementRule::new, Block.LILY_PAD);
        Utility.registerPlacementRules(blockManager, PlantPlacementRule::new, Block.SHORT_GRASS);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new PlantPlacementRule(block, Key.key("minecraft:supports_dry_vegetation")),
                Block.SHORT_DRY_GRASS,
                Block.TALL_DRY_GRASS);
        Utility.registerPlacementRules(
                blockManager,
                block -> new PlantPlacementRule(block, Key.key("minecraft:supports_nether_wart")),
                Block.NETHER_WART);
        Utility.registerPlacementRules(
                blockManager, SporeBlossomPlacementRule::new, Block.SPORE_BLOSSOM);
        Utility.registerPlacementRules(
                blockManager, HangingMossPlacementRule::new, Block.PALE_HANGING_MOSS);
        Utility.registerPlacementRules(
                blockManager,
                CoralPlantPlacementRule::new,
                Block.DEAD_TUBE_CORAL,
                Block.DEAD_BRAIN_CORAL,
                Block.DEAD_BUBBLE_CORAL,
                Block.DEAD_FIRE_CORAL,
                Block.DEAD_HORN_CORAL,
                Block.DEAD_TUBE_CORAL_FAN,
                Block.DEAD_BRAIN_CORAL_FAN,
                Block.DEAD_BUBBLE_CORAL_FAN,
                Block.DEAD_FIRE_CORAL_FAN,
                Block.DEAD_HORN_CORAL_FAN);
        Utility.registerPlacementRules(
                blockManager,
                CoralWallFanPlacementRule::new,
                Block.DEAD_TUBE_CORAL_WALL_FAN,
                Block.DEAD_BRAIN_CORAL_WALL_FAN,
                Block.DEAD_BUBBLE_CORAL_WALL_FAN,
                Block.DEAD_FIRE_CORAL_WALL_FAN,
                Block.DEAD_HORN_CORAL_WALL_FAN);
        Utility.registerPlacementRules(blockManager, CocoaPlacementRule::new, Block.COCOA);
        Utility.registerPlacementRules(
                blockManager,
                LanternPlacementRule::new,
                Block.LANTERN,
                Block.SOUL_LANTERN,
                Block.COPPER_LANTERN,
                Block.EXPOSED_COPPER_LANTERN,
                Block.WEATHERED_COPPER_LANTERN,
                Block.OXIDIZED_COPPER_LANTERN,
                Block.WAXED_COPPER_LANTERN,
                Block.WAXED_EXPOSED_COPPER_LANTERN,
                Block.WAXED_WEATHERED_COPPER_LANTERN,
                Block.WAXED_OXIDIZED_COPPER_LANTERN);
        Utility.registerPlacementRules(
                blockManager,
                CandlePlacementRule::new,
                Block.CANDLE,
                Block.WHITE_CANDLE,
                Block.LIGHT_GRAY_CANDLE,
                Block.GRAY_CANDLE,
                Block.BLACK_CANDLE,
                Block.BROWN_CANDLE,
                Block.RED_CANDLE,
                Block.ORANGE_CANDLE,
                Block.YELLOW_CANDLE,
                Block.LIME_CANDLE,
                Block.GREEN_CANDLE,
                Block.CYAN_CANDLE,
                Block.LIGHT_BLUE_CANDLE,
                Block.BLUE_CANDLE,
                Block.PURPLE_CANDLE,
                Block.MAGENTA_CANDLE,
                Block.PINK_CANDLE);
        Utility.registerPlacementRules(blockManager, SnowLayerPlacementRule::new, Block.SNOW);
        Utility.registerPlacementRules(blockManager, SeaPicklePlacementRule::new, Block.SEA_PICKLE);
        Utility.registerPlacementRules(blockManager, TurtleEggPlacementRule::new, Block.TURTLE_EGG);
        Utility.registerPlacementRules(
                blockManager,
                AnvilPlacementRule::new,
                Block.ANVIL,
                Block.CHIPPED_ANVIL,
                Block.DAMAGED_ANVIL);
        Utility.registerPlacementRules(blockManager, HopperPlacementRule::new, Block.HOPPER);
        Utility.registerPlacementRules(blockManager, BellPlacementRule::new, Block.BELL);
        Utility.registerPlacementRules(
                blockManager,
                MultifacePlacementRule::new,
                Block.GLOW_LICHEN,
                Block.SCULK_VEIN,
                Block.RESIN_CLUMP);
        Utility.registerPlacementRules(blockManager, MultifacePlacementRule::vine, Block.VINE);
        Utility.registerPlacementRules(
                blockManager, SegmentedPlacementRule::new, Block.PINK_PETALS, Block.WILDFLOWERS);
        Utility.registerPlacementRules(
                blockManager,
                block -> new SegmentedPlacementRule(block, "segment_amount", true),
                Block.LEAF_LITTER);
        Utility.registerPlacementRules(
                blockManager, ScaffoldingPlacementRule::new, Block.SCAFFOLDING);

        Utility.registerPlacementRules(
                blockManager,
                LightningRodPlacementRule::new,
                Block.LIGHTNING_ROD,
                Block.EXPOSED_LIGHTNING_ROD,
                Block.WEATHERED_LIGHTNING_ROD,
                Block.OXIDIZED_LIGHTNING_ROD,
                Block.WAXED_LIGHTNING_ROD,
                Block.WAXED_EXPOSED_LIGHTNING_ROD,
                Block.WAXED_WEATHERED_LIGHTNING_ROD,
                Block.WAXED_OXIDIZED_LIGHTNING_ROD);
        Utility.registerPlacementRules(
                blockManager,
                AmethystClusterPlacementRule::new,
                Block.SMALL_AMETHYST_BUD,
                Block.MEDIUM_AMETHYST_BUD,
                Block.LARGE_AMETHYST_BUD,
                Block.AMETHYST_CLUSTER);
        Utility.registerPlacementRules(blockManager, EndRodPlacementRule::new, Block.END_ROD);
        Utility.registerPlacementRules(
                blockManager, TripWireHookPlacementRule::new, Block.TRIPWIRE_HOOK);
        Utility.registerPlacementRules(
                blockManager, BigDripleafPlacementRule::new, Block.BIG_DRIPLEAF);
        Utility.registerPlacementRules(
                blockManager, SmallDripleafPlacementRule::new, Block.SMALL_DRIPLEAF);
        Utility.registerPlacementRules(
                blockManager, PitcherCropPlacementRule::new, Block.PITCHER_CROP);

        Utility.registerPlacementRules(blockManager, JigsawPlacementRule::new, Block.JIGSAW);
        Utility.registerPlacementRules(blockManager, CrafterPlacementRule::new, Block.CRAFTER);
        Utility.registerPlacementRules(
                blockManager, DecoratedPotPlacementRule::new, Block.DECORATED_POT);
        Utility.registerPlacementRules(
                blockManager, EnderChestPlacementRule::new, Block.ENDER_CHEST);
        Utility.registerPlacementRules(blockManager, BambooStalkPlacementRule::new, Block.BAMBOO);
        Utility.registerPlacementRules(
                blockManager,
                GrowingPlantHeadPlacementRule::new,
                Block.KELP,
                Block.WEEPING_VINES,
                Block.TWISTING_VINES);
        Utility.registerPlacementRules(
                blockManager,
                block -> new GrowingPlantHeadPlacementRule(block, BerryBlockHandler.INSTANCE),
                Block.CAVE_VINES);
        Utility.registerPlacementRules(
                blockManager,
                block -> new GrowingPlantBodyPlacementRule(block, Block.KELP, BlockFace.TOP, null),
                Block.KELP_PLANT);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new GrowingPlantBodyPlacementRule(
                                block, Block.TWISTING_VINES, BlockFace.TOP, null),
                Block.TWISTING_VINES_PLANT);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new GrowingPlantBodyPlacementRule(
                                block, Block.WEEPING_VINES, BlockFace.BOTTOM, null),
                Block.WEEPING_VINES_PLANT);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new GrowingPlantBodyPlacementRule(
                                block,
                                Block.CAVE_VINES,
                                BlockFace.BOTTOM,
                                BerryBlockHandler.INSTANCE),
                Block.CAVE_VINES_PLANT);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new AttachedStemPlacementRule(
                                block,
                                Block.PUMPKIN,
                                Block.PUMPKIN_STEM,
                                Key.key("minecraft:supports_pumpkin_stem")),
                Block.ATTACHED_PUMPKIN_STEM);
        Utility.registerPlacementRules(
                blockManager,
                block ->
                        new AttachedStemPlacementRule(
                                block,
                                Block.MELON,
                                Block.MELON_STEM,
                                Key.key("minecraft:supports_melon_stem")),
                Block.ATTACHED_MELON_STEM);
        Utility.registerPlacementRules(blockManager, SeagrassPlacementRule::new, Block.SEAGRASS);
        Utility.registerPlacementRules(
                blockManager, MangrovePropagulePlacementRule::new, Block.MANGROVE_PROPAGULE);
        Utility.registerPlacementRules(
                blockManager, HangingRootsPlacementRule::new, Block.HANGING_ROOTS);
        Utility.registerPlacementRules(
                blockManager,
                WaterloggedAxisPlacementRule::new,
                Block.IRON_CHAIN,
                Block.COPPER_CHAIN,
                Block.EXPOSED_COPPER_CHAIN,
                Block.WEATHERED_COPPER_CHAIN,
                Block.OXIDIZED_COPPER_CHAIN,
                Block.WAXED_COPPER_CHAIN,
                Block.WAXED_EXPOSED_COPPER_CHAIN,
                Block.WAXED_WEATHERED_COPPER_CHAIN,
                Block.WAXED_OXIDIZED_COPPER_CHAIN);
        Utility.registerPlacementRules(
                blockManager,
                WaterloggedDummyPlacementRule::new,
                Block.MANGROVE_ROOTS,
                Block.HEAVY_CORE,
                Block.BARRIER,
                Block.SCULK_SENSOR,
                Block.SCULK_SHRIEKER);
        Utility.registerPlacementRules(
                blockManager,
                block -> new WaterloggedDummyPlacementRule(block, true),
                Block.CONDUIT);
        Utility.registerPlacementRules(
                blockManager,
                HugeMushroomPlacementRule::new,
                Block.RED_MUSHROOM_BLOCK,
                Block.BROWN_MUSHROOM_BLOCK,
                Block.MUSHROOM_STEM);
        Utility.registerPlacementRules(
                blockManager, CampfirePlacementRule::new, Block.CAMPFIRE, Block.SOUL_CAMPFIRE);

        blockManager.registerBlockPlacementRule(
                new HandlerAttachingPlacementRule(
                        Block.RESPAWN_ANCHOR, RespawnAnchorBlockHandler.INSTANCE));
        blockManager.registerBlockPlacementRule(
                new HandlerAttachingPlacementRule(
                        Block.DRAGON_EGG, DragonEggBlockHandler.INSTANCE));

        Utility.registerPlacementRules(
                blockManager, ChorusPlantPlacementRule::new, Block.CHORUS_PLANT);
        Utility.registerPlacementRules(
                blockManager, MossyCarpetPlacementRule::new, Block.PALE_MOSS_CARPET);
        Utility.registerPlacementRules(
                blockManager,
                PointedDripstonePlacementRule::new,
                Block.POINTED_DRIPSTONE,
                Block.SULFUR_SPIKE);
        Utility.registerPlacementRules(
                blockManager,
                CopperGolemStatuePlacementRule::new,
                Block.COPPER_GOLEM_STATUE,
                Block.EXPOSED_COPPER_GOLEM_STATUE,
                Block.WEATHERED_COPPER_GOLEM_STATUE,
                Block.OXIDIZED_COPPER_GOLEM_STATUE,
                Block.WAXED_COPPER_GOLEM_STATUE,
                Block.WAXED_EXPOSED_COPPER_GOLEM_STATUE,
                Block.WAXED_WEATHERED_COPPER_GOLEM_STATUE,
                Block.WAXED_OXIDIZED_COPPER_GOLEM_STATUE);
        Utility.registerPlacementRules(
                blockManager,
                WaterloggedDummyPlacementRule::new,
                Block.COPPER_GRATE,
                Block.EXPOSED_COPPER_GRATE,
                Block.WEATHERED_COPPER_GRATE,
                Block.OXIDIZED_COPPER_GRATE,
                Block.WAXED_COPPER_GRATE,
                Block.WAXED_EXPOSED_COPPER_GRATE,
                Block.WAXED_WEATHERED_COPPER_GRATE,
                Block.WAXED_OXIDIZED_COPPER_GRATE);
        Utility.registerPlacementRules(
                blockManager, TallPlantPlacementRule::new, Block.PITCHER_PLANT);
        Utility.registerPlacementRules(blockManager, TripWirePlacementRule::new, Block.TRIPWIRE);
        Utility.registerPlacementRules(
                blockManager, RedstoneWirePlacementRule::new, Block.REDSTONE_WIRE);
        Utility.registerPlacementRules(blockManager, FirePlacementRule::new, Block.FIRE);
        Utility.registerPlacementRules(
                blockManager, NetherPortalPlacementRule::new, Block.NETHER_PORTAL);
        Utility.registerPlacementRules(
                blockManager, RedstoneLampPlacementRule::new, Block.REDSTONE_LAMP);
        Utility.registerPlacementRules(blockManager, LightPlacementRule::new, Block.LIGHT);
        Utility.registerPlacementRules(
                blockManager, PotentSulfurPlacementRule::new, Block.POTENT_SULFUR);
        Utility.registerPlacementRules(
                blockManager,
                SnowyPlacementRule::new,
                Block.GRASS_BLOCK,
                Block.MYCELIUM,
                Block.PODZOL);
        Utility.registerPlacementRules(blockManager, FarmlandPlacementRule::new, Block.FARMLAND);
        Utility.registerPlacementRules(blockManager, DirtPathPlacementRule::new, Block.DIRT_PATH);
        registerConcretePowders(blockManager);

        Utility.registerPlacementRules(
                blockManager,
                block -> new HorizontalFacingPlacementRule(block, true),
                Block.END_PORTAL_FRAME);
        Utility.registerPlacementRules(
                blockManager,
                block -> new WallMountedPlacementRule(block, true, false, null),
                Block.LADDER);
        Utility.registerPlacementRules(
                blockManager,
                block -> new WallMountedPlacementRule(block, false, false, null),
                Block.SHELF_MUSHROOM);
        Utility.registerPlacementRules(
                blockManager, TallSeagrassPlacementRule::new, Block.TALL_SEAGRASS);
        Utility.registerPlacementRules(
                blockManager, TallPlantPlacementRule::new, Block.TALL_GRASS, Block.LARGE_FERN);
        Utility.registerPlacementRules(
                blockManager,
                ShelfPlacementRule::new,
                Block.ACACIA_SHELF,
                Block.BAMBOO_SHELF,
                Block.BIRCH_SHELF,
                Block.CHERRY_SHELF,
                Block.CRIMSON_SHELF,
                Block.DARK_OAK_SHELF,
                Block.JUNGLE_SHELF,
                Block.MANGROVE_SHELF,
                Block.OAK_SHELF,
                Block.PALE_OAK_SHELF,
                Block.POPLAR_SHELF,
                Block.SPRUCE_SHELF,
                Block.WARPED_SHELF);
    }

    private static void registerHandlers(BlockManager blockManager) {
        blockManager.registerHandler(
                DoorBlockHandler.INSTANCE.getKey(), () -> DoorBlockHandler.INSTANCE);
        blockManager.registerHandler(
                TrapdoorBlockHandler.INSTANCE.getKey(), () -> TrapdoorBlockHandler.INSTANCE);
        blockManager.registerHandler(
                FenceGateBlockHandler.INSTANCE.getKey(), () -> FenceGateBlockHandler.INSTANCE);
        blockManager.registerHandler(
                LeverBlockHandler.INSTANCE.getKey(), () -> LeverBlockHandler.INSTANCE);
        blockManager.registerHandler(
                ButtonBlockHandler.INSTANCE.getKey(), () -> ButtonBlockHandler.INSTANCE);
        blockManager.registerHandler(
                CandleBlockHandler.INSTANCE.getKey(), () -> CandleBlockHandler.INSTANCE);
        blockManager.registerHandler(
                CakeBlockHandler.INSTANCE.getKey(), () -> CakeBlockHandler.INSTANCE);
        blockManager.registerHandler(
                CandleCakeBlockHandler.INSTANCE.getKey(), () -> CandleCakeBlockHandler.INSTANCE);
        blockManager.registerHandler(
                JukeboxBlockHandler.INSTANCE.getKey(), () -> JukeboxBlockHandler.INSTANCE);
        blockManager.registerHandler(
                RedstoneWireBlockHandler.INSTANCE.getKey(),
                () -> RedstoneWireBlockHandler.INSTANCE);
        blockManager.registerHandler(
                LightBlockHandler.INSTANCE.getKey(), () -> LightBlockHandler.INSTANCE);
        blockManager.registerHandler(
                BerryBlockHandler.INSTANCE.getKey(), () -> BerryBlockHandler.INSTANCE);
        blockManager.registerHandler(
                ComposterBlockHandler.INSTANCE.getKey(), () -> ComposterBlockHandler.INSTANCE);
        blockManager.registerHandler(
                BedBlockHandler.INSTANCE.getKey(), () -> BedBlockHandler.INSTANCE);
        blockManager.registerHandler(
                DaylightDetectorBlockHandler.INSTANCE.getKey(),
                () -> DaylightDetectorBlockHandler.INSTANCE);
        blockManager.registerHandler(
                ConsumeInteractionBlockHandler.INSTANCE.getKey(),
                () -> ConsumeInteractionBlockHandler.INSTANCE);
        blockManager.registerHandler(
                NoteBlockHandler.INSTANCE.getKey(), () -> NoteBlockHandler.INSTANCE);
        blockManager.registerHandler(
                RepeaterBlockHandler.INSTANCE.getKey(), () -> RepeaterBlockHandler.INSTANCE);
        blockManager.registerHandler(
                ComparatorBlockHandler.INSTANCE.getKey(), () -> ComparatorBlockHandler.INSTANCE);
        blockManager.registerHandler(
                BellBlockHandler.INSTANCE.getKey(), () -> BellBlockHandler.INSTANCE);
        blockManager.registerHandler(
                FlowerPotBlockHandler.INSTANCE.getKey(), () -> FlowerPotBlockHandler.INSTANCE);
        blockManager.registerHandler(
                SignBlockHandler.INSTANCE.getKey(), () -> SignBlockHandler.INSTANCE);
        blockManager.registerHandler(
                RespawnAnchorBlockHandler.INSTANCE.getKey(),
                () -> RespawnAnchorBlockHandler.INSTANCE);
        blockManager.registerHandler(
                DragonEggBlockHandler.INSTANCE.getKey(), () -> DragonEggBlockHandler.INSTANCE);
        blockManager.registerHandler(
                BeeHiveBlockHandler.INSTANCE.getKey(), () -> BeeHiveBlockHandler.INSTANCE);
        blockManager.registerHandler(
                GameMasterInteractionBlockHandler.INSTANCE.getKey(),
                () -> GameMasterInteractionBlockHandler.INSTANCE);
    }

    private static void registerByTag(
            BlockManager blockManager,
            java.util.function.Function<
                            Block,
                            ? extends net.minestom.server.instance.block.rule.BlockPlacementRule>
                    factory,
            String tagKey) {
        Utility.registerPlacementRules(blockManager, factory::apply, Key.key(tagKey));
    }

    private static void registerConcretePowders(BlockManager blockManager) {
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.WHITE_CONCRETE_POWDER, Block.WHITE_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(
                        Block.ORANGE_CONCRETE_POWDER, Block.ORANGE_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(
                        Block.MAGENTA_CONCRETE_POWDER, Block.MAGENTA_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(
                        Block.LIGHT_BLUE_CONCRETE_POWDER, Block.LIGHT_BLUE_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(
                        Block.YELLOW_CONCRETE_POWDER, Block.YELLOW_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.LIME_CONCRETE_POWDER, Block.LIME_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.PINK_CONCRETE_POWDER, Block.PINK_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.GRAY_CONCRETE_POWDER, Block.GRAY_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(
                        Block.LIGHT_GRAY_CONCRETE_POWDER, Block.LIGHT_GRAY_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.CYAN_CONCRETE_POWDER, Block.CYAN_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(
                        Block.PURPLE_CONCRETE_POWDER, Block.PURPLE_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.BLUE_CONCRETE_POWDER, Block.BLUE_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.BROWN_CONCRETE_POWDER, Block.BROWN_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.GREEN_CONCRETE_POWDER, Block.GREEN_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.RED_CONCRETE_POWDER, Block.RED_CONCRETE));
        blockManager.registerBlockPlacementRule(
                new ConcretePowderPlacementRule(Block.BLACK_CONCRETE_POWDER, Block.BLACK_CONCRETE));
    }
}
