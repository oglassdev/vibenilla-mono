package rocks.minestom.worldgen.structure.processor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;

public final class StructureProcessors {
    private StructureProcessors() {}

    /** Parses a processor list, failing on unknown processor or rule test types. */
    public static StructureProcessorList parseProcessorList(JsonElement json) {
        var processors = new ArrayList<StructureProcessor>();
        for (var entry : json.getAsJsonObject().getAsJsonArray("processors")) {
            var processor = parseProcessor(entry.getAsJsonObject());
            if (processor != null) {
                processors.add(processor);
            }
        }

        return processors.isEmpty()
                ? StructureProcessorList.EMPTY
                : new StructureProcessorList(List.copyOf(processors));
    }

    private static StructureProcessor parseProcessor(JsonObject obj) {
        var type = Key.key(obj.get("processor_type").getAsString()).asString();
        return switch (type) {
            case "minecraft:rule" -> parseRuleProcessor(obj);
            case "minecraft:block_rot" -> {
                var rottable =
                        obj.has("rottable_blocks") ? parseTag(obj.get("rottable_blocks")) : null;
                yield new BlockRotProcessor(rottable, obj.get("integrity").getAsFloat());
            }
            case "minecraft:protected_blocks" ->
                    new ProtectedBlockProcessor(parseTag(obj.get("value")));
            case "minecraft:capped" -> {
                var delegate = parseProcessor(obj.getAsJsonObject("delegate"));
                yield delegate == null
                        ? null
                        : new CappedProcessor(delegate, parseIntLimit(obj.get("limit")));
            }
            case "minecraft:nop" -> null;
            default -> throw new IllegalArgumentException("Unsupported processor type: " + type);
        };
    }

    private static RuleStructureProcessor parseRuleProcessor(JsonObject obj) {
        var rules = new ArrayList<ProcessorRule>();
        for (var ruleEntry : obj.getAsJsonArray("rules")) {
            var rule = ruleEntry.getAsJsonObject();
            var position =
                    rule.has("position_predicate")
                            ? parsePosRuleTest(rule.getAsJsonObject("position_predicate"))
                            : PosRuleTest.PosAlwaysTrueTest.INSTANCE;
            rules.add(
                    new ProcessorRule(
                            parseRuleTest(rule.getAsJsonObject("input_predicate")),
                            parseRuleTest(rule.getAsJsonObject("location_predicate")),
                            position,
                            parseBlockState(rule.get("output_state"))));
        }

        return new RuleStructureProcessor(List.copyOf(rules));
    }

    private static RuleTest parseRuleTest(JsonObject obj) {
        var type = Key.key(obj.get("predicate_type").getAsString()).asString();
        return switch (type) {
            case "minecraft:always_true" -> RuleTest.AlwaysTrueTest.INSTANCE;
            case "minecraft:block_match" ->
                    new RuleTest.BlockMatchTest(Key.key(obj.get("block").getAsString()));
            case "minecraft:blockstate_match" ->
                    new RuleTest.BlockStateMatchTest(parseBlockState(obj.get("block_state")));
            case "minecraft:random_block_match" ->
                    new RuleTest.RandomBlockMatchTest(
                            Key.key(obj.get("block").getAsString()),
                            obj.get("probability").getAsFloat());
            case "minecraft:random_blockstate_match" ->
                    new RuleTest.RandomBlockStateMatchTest(
                            parseBlockState(obj.get("block_state")),
                            obj.get("probability").getAsFloat());
            case "minecraft:tag_match" ->
                    new RuleTest.TagMatchTest(Key.key(obj.get("tag").getAsString()));
            case "minecraft:all_of" -> new RuleTest.AllOfRuleTest(parseRuleTests(obj));
            case "minecraft:any_of" -> new RuleTest.AnyOfRuleTest(parseRuleTests(obj));
            case "minecraft:not" ->
                    new RuleTest.NotRuleTest(parseRuleTest(obj.getAsJsonObject("rule")));
            case "minecraft:height_match" ->
                    new RuleTest.HeightMatchTest(
                            obj.get("min_inclusive").getAsInt(),
                            obj.get("max_inclusive").getAsInt());
            default -> throw new IllegalArgumentException("Unsupported rule test: " + type);
        };
    }

    private static List<RuleTest> parseRuleTests(JsonObject obj) {
        var tests = new ArrayList<RuleTest>();
        for (var entry : obj.getAsJsonArray("rules")) {
            tests.add(parseRuleTest(entry.getAsJsonObject()));
        }
        return List.copyOf(tests);
    }

    private static PosRuleTest parsePosRuleTest(JsonObject obj) {
        var type = Key.key(obj.get("predicate_type").getAsString()).asString();
        return switch (type) {
            case "minecraft:always_true" -> PosRuleTest.PosAlwaysTrueTest.INSTANCE;
            case "minecraft:linear_pos" ->
                    new PosRuleTest.LinearPosTest(
                            getFloat(obj, "min_chance"),
                            getFloat(obj, "max_chance"),
                            getInt(obj, "min_dist"),
                            getInt(obj, "max_dist"));
            case "minecraft:axis_aligned_linear_pos" ->
                    new PosRuleTest.AxisAlignedLinearPosTest(
                            getFloat(obj, "min_chance"),
                            getFloat(obj, "max_chance"),
                            getInt(obj, "min_dist"),
                            getInt(obj, "max_dist"),
                            parseAxis(obj));
            default ->
                    throw new IllegalArgumentException("Unsupported position rule test: " + type);
        };
    }

    /** A block state given as a bare id or as {@code {id, properties}}. */
    private static Block parseBlockState(JsonElement json) {
        return BlockCodec.CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }

    /** A block tag reference ({@code #namespace:tag}). */
    private static Key parseTag(JsonElement json) {
        var value = json.getAsString();
        if (!value.startsWith("#")) {
            throw new IllegalArgumentException("Expected a block tag reference: " + value);
        }
        return Key.key(value.substring(1));
    }

    /**
     * Capped limits in the vanilla datapack are constant ints; other int providers are rejected.
     */
    private static int parseIntLimit(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return json.getAsInt();
        }
        throw new IllegalArgumentException("Unsupported capped limit provider: " + json);
    }

    private static String parseAxis(JsonObject obj) {
        var axis = obj.has("axis") ? obj.get("axis").getAsString() : "y";
        if (!axis.equals("x") && !axis.equals("y") && !axis.equals("z")) {
            throw new IllegalArgumentException("Unknown axis: " + axis);
        }
        return axis;
    }

    private static float getFloat(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsFloat() : 0.0F;
    }

    private static int getInt(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsInt() : 0;
    }
}
