package rocks.minestom.worldgen.feature.ruletest;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public interface RuleTest {
    boolean test(Block block, BlockVec position, RandomSource random);

    static RuleTest fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("RuleTest must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var typeStr = obj.get("predicate_type").getAsString();

        return switch (typeStr) {
            case "minecraft:always_true" -> new AlwaysTrueTest();
            case "minecraft:block_match" -> {
                var block = parseBlock(obj.get("block").getAsString());
                yield new BlockMatchTest(block);
            }
            case "minecraft:blockstate_match" -> {
                var state =
                        BlockCodec.CODEC
                                .decode(Transcoder.JSON, obj.get("block_state"))
                                .orElseThrow();
                yield new BlockStateMatchTest(state);
            }
            case "minecraft:tag_match" -> {
                var tag = Key.key(obj.get("tag").getAsString());
                yield new TagMatchTest(tag, resolveTag(tag, blockTags));
            }
            case "minecraft:random_block_match" -> {
                var block = parseBlock(obj.get("block").getAsString());
                var probability = obj.get("probability").getAsFloat();
                yield new RandomBlockMatchTest(block, probability);
            }
            case "minecraft:random_blockstate_match" -> {
                var state =
                        BlockCodec.CODEC
                                .decode(Transcoder.JSON, obj.get("block_state"))
                                .orElseThrow();
                var probability = obj.get("probability").getAsFloat();
                yield new RandomBlockStateMatchTest(state, probability);
            }
            case "minecraft:height_match" ->
                    new HeightMatchTest(
                            obj.get("min_inclusive").getAsInt(),
                            obj.get("max_inclusive").getAsInt());
            case "minecraft:all_of" -> new AllOfTest(parseRules(obj, blockTags));
            case "minecraft:any_of" -> new AnyOfTest(parseRules(obj, blockTags));
            case "minecraft:not" -> new NotTest(fromJson(obj.get("rule"), blockTags));
            default -> throw new IllegalArgumentException("Unknown rule test type: " + typeStr);
        };
    }

    private static List<RuleTest> parseRules(
            com.google.gson.JsonObject obj, BlockTagManager blockTags) {
        var rules = new ArrayList<RuleTest>();
        for (var rule : obj.getAsJsonArray("rules")) {
            rules.add(fromJson(rule, blockTags));
        }
        return List.copyOf(rules);
    }

    private static Block parseBlock(String name) {
        var block = Block.fromKey(Key.key(name));
        if (block == null) {
            throw new IllegalStateException("Unknown block key: " + name);
        }

        return block;
    }

    private static Set<Key> resolveTag(Key tag, BlockTagManager blockTags) {
        if (blockTags == null) {
            return Set.of();
        }

        synchronized (blockTags) {
            return blockTags.blocks(tag);
        }
    }

    record AlwaysTrueTest() implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            return true;
        }
    }

    record HeightMatchTest(int minInclusive, int maxInclusive) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            return this.minInclusive <= position.blockY() && position.blockY() <= this.maxInclusive;
        }
    }

    record AllOfTest(List<RuleTest> rules) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            for (var rule : this.rules) {
                if (!rule.test(block, position, random)) {
                    return false;
                }
            }
            return true;
        }
    }

    record AnyOfTest(List<RuleTest> rules) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            for (var rule : this.rules) {
                if (rule.test(block, position, random)) {
                    return true;
                }
            }
            return false;
        }
    }

    record NotTest(RuleTest rule) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            return !this.rule.test(block, position, random);
        }
    }

    record BlockMatchTest(Block block) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            return block.compare(this.block);
        }
    }

    record BlockStateMatchTest(Block state) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            return block.compare(this.state, Block.Comparator.STATE);
        }
    }

    record TagMatchTest(Key tag, Set<Key> blocks) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            return this.blocks.contains(block.key());
        }
    }

    record RandomBlockMatchTest(Block block, float probability) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            return block.compare(this.block) && random.nextFloat() < this.probability;
        }
    }

    record RandomBlockStateMatchTest(Block state, float probability) implements RuleTest {
        @Override
        public boolean test(Block block, BlockVec position, RandomSource random) {
            return block.compare(this.state, Block.Comparator.STATE)
                    && random.nextFloat() < this.probability;
        }
    }
}
