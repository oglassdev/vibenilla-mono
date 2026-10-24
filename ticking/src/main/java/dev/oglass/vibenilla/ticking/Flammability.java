package dev.oglass.vibenilla.ticking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minestom.server.instance.block.Block;

/** Vanilla {@code FireBlock.bootStrap}: how readily blocks catch fire and how readily they burn. */
final class Flammability {
    private record Odds(int ignite, int burn) {}

    private static final List<String> WOODS =
            List.of(
                    "oak",
                    "spruce",
                    "birch",
                    "jungle",
                    "acacia",
                    "cherry",
                    "dark_oak",
                    "pale_oak",
                    "mangrove",
                    "bamboo",
                    "poplar");
    private static final List<String> LEAVES =
            List.of(
                    "oak",
                    "spruce",
                    "birch",
                    "jungle",
                    "acacia",
                    "cherry",
                    "dark_oak",
                    "pale_oak",
                    "mangrove",
                    "azalea",
                    "flowering_azalea",
                    "red_poplar",
                    "orange_poplar",
                    "yellow_poplar");
    private static final List<String> COLORS =
            List.of(
                    "white",
                    "orange",
                    "magenta",
                    "light_blue",
                    "yellow",
                    "lime",
                    "pink",
                    "gray",
                    "light_gray",
                    "cyan",
                    "purple",
                    "blue",
                    "brown",
                    "green",
                    "red",
                    "black");

    private static final Map<String, Odds> ODDS = new HashMap<>();

    static {
        for (var wood : WOODS) {
            add(5, 20, wood + "_planks", wood + "_slab", wood + "_fence_gate", wood + "_fence");
            add(5, 20, wood + "_stairs", wood + "_shelf");
            add(
                    5,
                    5,
                    wood + "_log",
                    wood + "_wood",
                    "stripped_" + wood + "_log",
                    "stripped_" + wood + "_wood");
        }
        for (var leaves : LEAVES) {
            add(30, 60, leaves + "_leaves");
        }
        for (var color : COLORS) {
            add(30, 60, color + "_wool");
            add(60, 20, color + "_carpet");
        }
        add(5, 20, "bamboo_mosaic", "bamboo_mosaic_slab", "bamboo_mosaic_stairs", "mangrove_roots");
        add(5, 20, "composter", "beehive");
        add(5, 5, "bamboo_block", "stripped_bamboo_block", "coal_block");
        add(30, 60, "dried_kelp_block", "azalea", "flowering_azalea", "hanging_roots");
        add(30, 20, "bookshelf", "bee_nest", "lectern");
        add(
                60,
                100,
                "short_grass",
                "fern",
                "dead_bush",
                "short_dry_grass",
                "tall_dry_grass",
                "sunflower",
                "lilac",
                "rose_bush",
                "peony",
                "tall_grass",
                "large_fern",
                "dandelion",
                "golden_dandelion",
                "poppy",
                "open_eyeblossom",
                "closed_eyeblossom",
                "blue_orchid",
                "allium",
                "azure_bluet",
                "red_tulip",
                "orange_tulip",
                "white_tulip",
                "pink_tulip",
                "oxeye_daisy",
                "cornflower",
                "lily_of_the_valley",
                "torchflower",
                "pitcher_plant",
                "wither_rose",
                "pink_petals",
                "wildflowers",
                "leaf_litter",
                "cactus_flower",
                "sweet_berry_bush",
                "spore_blossom",
                "big_dripleaf",
                "big_dripleaf_stem",
                "small_dripleaf",
                "firefly_bush",
                "bush");
        add(60, 20, "hay_block");
        add(60, 60, "bamboo", "scaffolding");
        add(15, 100, "vine", "glow_lichen");
        add(15, 60, "cave_vines", "cave_vines_plant");
        add(15, 20, "target");
        add(15, 100, "tnt");
        add(5, 100, "pale_moss_block", "pale_moss_carpet", "pale_hanging_moss");
    }

    private Flammability() {}

    private static void add(int ignite, int burn, String... names) {
        for (var name : names) {
            ODDS.put(name, new Odds(ignite, burn));
        }
    }

    /**
     * How strongly the block encourages nearby fire to spread into the air next to it. Waterlogged
     * blocks never do.
     */
    static int igniteOdds(Block block) {
        if ("true".equals(block.getProperty("waterlogged"))) {
            return 0;
        }
        var odds = ODDS.get(block.key().value());
        return odds == null ? 0 : odds.ignite;
    }

    /** How likely fire next to the block is to destroy it. Waterlogged blocks never burn. */
    static int burnOdds(Block block) {
        if ("true".equals(block.getProperty("waterlogged"))) {
            return 0;
        }
        var odds = ODDS.get(block.key().value());
        return odds == null ? 0 : odds.burn;
    }

    static boolean canBurn(Block block) {
        return igniteOdds(block) > 0;
    }
}
