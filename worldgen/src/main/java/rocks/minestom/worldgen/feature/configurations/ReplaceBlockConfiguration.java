package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.ruletest.RuleTest;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public record ReplaceBlockConfiguration(List<OreConfiguration.TargetBlockState> replacements)
        implements FeatureConfiguration {

    public static ReplaceBlockConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var replacements = new ArrayList<OreConfiguration.TargetBlockState>();
        for (var element : json.getAsJsonObject().getAsJsonArray("targets")) {
            var object = element.getAsJsonObject();
            replacements.add(
                    new OreConfiguration.TargetBlockState(
                            RuleTest.fromJson(object.get("target"), blockTags),
                            BlockCodec.CODEC
                                    .decode(Transcoder.JSON, object.get("state"))
                                    .orElseThrow()));
        }

        return new ReplaceBlockConfiguration(List.copyOf(replacements));
    }
}
