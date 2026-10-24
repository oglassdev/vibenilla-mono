package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

public record TemplateConfiguration(
        List<Entry> templates,
        int totalWeight,
        StructureProcessorList processors,
        BlockTagManager blockTags)
        implements FeatureConfiguration {

    public static TemplateConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var loader = Features.currentLoader();
        if (loader == null) {
            throw new IllegalStateException(
                    "Templates can only be parsed through a feature loader");
        }

        var object = json.getAsJsonObject();
        var entries = new ArrayList<Entry>();
        var totalWeight = 0;
        for (var element : object.getAsJsonArray("templates")) {
            var entry = element.getAsJsonObject();
            var weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
            var data = entry.getAsJsonObject("data");
            var rotations = new ArrayList<Rotation>();
            if (data.has("rotations")) {
                for (var rotation : data.getAsJsonArray("rotations")) {
                    rotations.add(Rotation.valueOf(rotation.getAsString().toUpperCase()));
                }
            } else {
                rotations.addAll(List.of(Rotation.values()));
            }

            entries.add(
                    new Entry(
                            StructureData.template(
                                    loader.dataPack(), Key.key(data.get("id").getAsString())),
                            List.copyOf(rotations),
                            weight));
            totalWeight += weight;
        }

        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Template list must not be empty");
        }

        return new TemplateConfiguration(
                List.copyOf(entries),
                totalWeight,
                StructureData.processorList(object.get("processors"), loader.dataPack()),
                blockTags);
    }

    public record Entry(StructureTemplate template, List<Rotation> rotations, int weight) {}
}
