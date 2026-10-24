package rocks.minestom.worldgen.structure.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.structure.JigsawStructure;
import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.TerrainAdjustment;
import rocks.minestom.worldgen.structure.endcity.EndCityStructure;
import rocks.minestom.worldgen.structure.fortress.FortressStructure;
import rocks.minestom.worldgen.structure.igloo.IglooStructure;
import rocks.minestom.worldgen.structure.mansion.WoodlandMansionStructure;
import rocks.minestom.worldgen.structure.mineshaft.MineshaftStructure;
import rocks.minestom.worldgen.structure.mineshaft.MineshaftType;
import rocks.minestom.worldgen.structure.monument.OceanMonumentStructure;
import rocks.minestom.worldgen.structure.netherfossil.NetherFossilStructure;
import rocks.minestom.worldgen.structure.oceanruin.OceanRuinStructure;
import rocks.minestom.worldgen.structure.pool.PoolAliasBinding;
import rocks.minestom.worldgen.structure.ruinedportal.RuinedPortalStructure;
import rocks.minestom.worldgen.structure.scattered.BuriedTreasureStructure;
import rocks.minestom.worldgen.structure.scattered.ScatteredFeatureKind;
import rocks.minestom.worldgen.structure.scattered.ScatteredFeatureStructure;
import rocks.minestom.worldgen.structure.shipwreck.ShipwreckStructure;
import rocks.minestom.worldgen.structure.stronghold.StrongholdStructure;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.surface.VerticalAnchor;

public final class Structures {
    private static final List<Key> NETHER_FOSSIL_TEMPLATES =
            List.of(
                    Key.key("minecraft:nether_fossils/fossil_1"),
                    Key.key("minecraft:nether_fossils/fossil_2"),
                    Key.key("minecraft:nether_fossils/fossil_3"),
                    Key.key("minecraft:nether_fossils/fossil_4"),
                    Key.key("minecraft:nether_fossils/fossil_5"),
                    Key.key("minecraft:nether_fossils/fossil_6"),
                    Key.key("minecraft:nether_fossils/fossil_7"),
                    Key.key("minecraft:nether_fossils/fossil_8"),
                    Key.key("minecraft:nether_fossils/fossil_9"),
                    Key.key("minecraft:nether_fossils/fossil_10"),
                    Key.key("minecraft:nether_fossils/fossil_11"),
                    Key.key("minecraft:nether_fossils/fossil_12"),
                    Key.key("minecraft:nether_fossils/fossil_13"),
                    Key.key("minecraft:nether_fossils/fossil_14"));

    private Structures() {}

    /** Parses a structure definition, failing on unknown structure types and malformed fields. */
    public static Structure parseStructure(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Structure must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var type = Key.key(obj.get("type").getAsString()).asString();
        var biomes = parseBiomes(obj.get("biomes"));
        return switch (type) {
            case "minecraft:jigsaw" -> parseJigsawStructure(obj, biomes);
            case "minecraft:mineshaft" ->
                    new MineshaftStructure(
                            MineshaftType.fromName(obj.get("mineshaft_type").getAsString()),
                            biomes);
            case "minecraft:stronghold" -> new StrongholdStructure(biomes);
            case "minecraft:fortress" -> new FortressStructure(biomes);
            case "minecraft:desert_pyramid" ->
                    new ScatteredFeatureStructure(ScatteredFeatureKind.DESERT_PYRAMID, biomes);
            case "minecraft:jungle_temple" ->
                    new ScatteredFeatureStructure(ScatteredFeatureKind.JUNGLE_TEMPLE, biomes);
            case "minecraft:swamp_hut" ->
                    new ScatteredFeatureStructure(ScatteredFeatureKind.SWAMP_HUT, biomes);
            case "minecraft:buried_treasure" -> new BuriedTreasureStructure(biomes);
            case "minecraft:ocean_monument" -> new OceanMonumentStructure(biomes);
            case "minecraft:woodland_mansion" -> new WoodlandMansionStructure(biomes);
            case "minecraft:end_city" -> new EndCityStructure(biomes);
            case "minecraft:igloo" -> new IglooStructure(biomes);
            case "minecraft:ocean_ruin" -> parseOceanRuinStructure(obj, biomes);
            case "minecraft:shipwreck" ->
                    new ShipwreckStructure(biomes, obj.get("is_beached").getAsBoolean());
            case "minecraft:nether_fossil" -> parseNetherFossilStructure(obj, biomes);
            case "minecraft:ruined_portal" -> parseRuinedPortalStructure(obj, biomes);
            default -> throw new IllegalArgumentException("Unsupported structure type: " + type);
        };
    }

    private static Structure parseNetherFossilStructure(
            JsonObject obj, Structure.StructureBiomes biomes) {
        var height = obj.getAsJsonObject("height");
        var heightType = height.get("type").getAsString();
        if (!heightType.equals("minecraft:uniform")) {
            throw new IllegalArgumentException(
                    "Unsupported nether fossil height provider: " + heightType);
        }
        var minHeight =
                VerticalAnchor.CODEC
                        .decode(Transcoder.JSON, height.get("min_inclusive"))
                        .orElseThrow();
        var maxHeight =
                VerticalAnchor.CODEC
                        .decode(Transcoder.JSON, height.get("max_inclusive"))
                        .orElseThrow();
        return new NetherFossilStructure(
                biomes, minHeight, maxHeight, NETHER_FOSSIL_TEMPLATES, parseTerrainAdaptation(obj));
    }

    private static Structure parseOceanRuinStructure(
            JsonObject obj, Structure.StructureBiomes biomes) {
        var biomeTemp =
                switch (obj.get("biome_temp").getAsString()) {
                    case "cold" -> OceanRuinStructure.BiomeTemp.COLD;
                    case "warm" -> OceanRuinStructure.BiomeTemp.WARM;
                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown ocean ruin biome_temp: " + obj.get("biome_temp"));
                };
        return new OceanRuinStructure(
                biomes,
                biomeTemp,
                obj.get("large_probability").getAsFloat(),
                obj.get("cluster_probability").getAsFloat());
    }

    private static Structure parseRuinedPortalStructure(
            JsonObject obj, Structure.StructureBiomes biomes) {
        var setups = new ArrayList<RuinedPortalStructure.Setup>();
        for (var element : obj.getAsJsonArray("setups")) {
            var setup = element.getAsJsonObject();
            setups.add(
                    new RuinedPortalStructure.Setup(
                            RuinedPortalStructure.VerticalPlacement.fromName(
                                    setup.get("placement").getAsString()),
                            setup.get("air_pocket_probability").getAsFloat(),
                            setup.get("mossiness").getAsFloat(),
                            setup.get("overgrown").getAsBoolean(),
                            setup.get("vines").getAsBoolean(),
                            setup.get("can_be_cold").getAsBoolean(),
                            setup.get("replace_with_blackstone").getAsBoolean(),
                            setup.get("weight").getAsFloat()));
        }
        return new RuinedPortalStructure(biomes, List.copyOf(setups));
    }

    private static Structure parseJigsawStructure(
            JsonObject obj, Structure.StructureBiomes biomes) {
        var startPool = Key.key(obj.get("start_pool").getAsString());
        var startJigsawName =
                obj.has("start_jigsaw_name")
                        ? Key.key(obj.get("start_jigsaw_name").getAsString())
                        : null;
        var size = obj.get("size").getAsInt();
        var startHeight = parseStartHeight(obj.get("start_height"));
        var projectToHeightmap = obj.has("project_start_to_heightmap");
        if (projectToHeightmap
                && !obj.get("project_start_to_heightmap")
                        .getAsString()
                        .equals("WORLD_SURFACE_WG")) {
            throw new IllegalArgumentException(
                    "Unsupported project_start_to_heightmap: "
                            + obj.get("project_start_to_heightmap"));
        }
        var useExpansionHack = obj.get("use_expansion_hack").getAsBoolean();
        var maxDistance = parseMaxDistance(obj.get("max_distance_from_center"));
        var poolAliases = PoolAliasBinding.parseList(obj.get("pool_aliases"));
        var paddingBottom = 0;
        var paddingTop = 0;
        if (obj.has("dimension_padding")) {
            var padding = obj.get("dimension_padding");
            if (padding.isJsonPrimitive()) {
                paddingBottom = padding.getAsInt();
                paddingTop = padding.getAsInt();
            } else {
                var paddingObj = padding.getAsJsonObject();
                paddingBottom = paddingObj.has("bottom") ? paddingObj.get("bottom").getAsInt() : 0;
                paddingTop = paddingObj.has("top") ? paddingObj.get("top").getAsInt() : 0;
            }
        }
        var liquidSettings =
                obj.has("liquid_settings")
                        ? LiquidSettings.fromName(obj.get("liquid_settings").getAsString())
                        : LiquidSettings.APPLY_WATERLOGGING;

        return new JigsawStructure(
                biomes,
                startPool,
                startJigsawName,
                size,
                new JigsawStructure.StartHeight(startHeight.min(), startHeight.max()),
                projectToHeightmap,
                useExpansionHack,
                maxDistance,
                poolAliases,
                paddingBottom,
                paddingTop,
                liquidSettings,
                parseTerrainAdaptation(obj));
    }

    private static int parseMaxDistance(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return json.getAsInt();
        }
        return json.getAsJsonObject().get("horizontal").getAsInt();
    }

    private static TerrainAdjustment parseTerrainAdaptation(JsonObject obj) {
        return obj.has("terrain_adaptation")
                ? TerrainAdjustment.fromName(obj.get("terrain_adaptation").getAsString())
                : TerrainAdjustment.NONE;
    }

    /** A biome holder set: a {@code #tag}, a single biome id or a list of biome ids. */
    private static Structure.StructureBiomes parseBiomes(JsonElement json) {
        if (json.isJsonPrimitive()) {
            var value = json.getAsString();
            if (value.startsWith("#")) {
                return new Structure.StructureBiomes(Key.key(value.substring(1)), null);
            }
            return new Structure.StructureBiomes(null, List.of(Key.key(value)));
        }

        var biomes = new ArrayList<Key>();
        for (var entry : json.getAsJsonArray()) {
            var value = entry.getAsString();
            if (value.startsWith("#")) {
                throw new IllegalArgumentException(
                        "Biome tags are not allowed inside a list: " + value);
            }
            biomes.add(Key.key(value));
        }
        return new Structure.StructureBiomes(null, List.copyOf(biomes));
    }

    private static HeightRange parseStartHeight(JsonElement json) {
        var obj = json.getAsJsonObject();
        if (obj.has("absolute")) {
            var absolute = obj.get("absolute").getAsInt();
            return new HeightRange(absolute, absolute);
        }

        var type = obj.get("type").getAsString();
        if (!type.equals("minecraft:uniform")) {
            throw new IllegalArgumentException("Unsupported start_height provider: " + type);
        }
        return new HeightRange(
                absoluteAnchor(obj.get("min_inclusive")), absoluteAnchor(obj.get("max_inclusive")));
    }

    private static int absoluteAnchor(JsonElement json) {
        var obj = json.getAsJsonObject();
        if (!obj.has("absolute")) {
            throw new IllegalArgumentException("Unsupported start_height anchor: " + json);
        }
        return obj.get("absolute").getAsInt();
    }

    private record HeightRange(int min, int max) {}
}
